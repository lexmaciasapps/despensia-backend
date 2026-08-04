# Technical Design: Scan Status Query + DLQ Consumer

## Change ID: `scan-status-query`

---

## 1. Overview

This design answers **HOW** to implement the scan status query feature set defined in the proposal (sdd/scan-status-query/proposal). Two deliverables:

1. **GET /api/scan/products/{scanId}** — returns current InventoryScan status + persisted result data
2. **DLQ consumer for `scan-events-dlt`** — persists failed Kafka messages with full metadata

---

## 2. Architecture Decisions

### AD-1: JSONB serialization strategy → `@JdbcTypeCode(SqlTypes.JSON)` on a String field

**Decision:** Add a `String resultDataJson` column to InventoryScan, annotated with Hibernate's native `@JdbcTypeCode(SqlTypes.JSON)`. Serialize/deserialize via Jackson ObjectMapper internally.

**Why not Map<String,Object> directly?**
- ProductItem and Receipt have different structures — no common base type that captures both cleanly as a typed field
- Using `Object` with JPA would require custom UserType (too much boilerplate, fragile across Hibernate versions)
- `String` + Jackson is the simplest path: one migration column, zero new entities

**Why not JsonNode?**
- Hibernate's `@JdbcTypeCode(SqlTypes.JSON)` does support `JsonNode`, but it creates an extra dependency on `tools.jackson.databind.JsonNode` that couples our domain layer to Jackson internals
- String + ObjectMapper gives us explicit control over serialization boundaries and makes the API contract clearer

**Implementation details:**

```java
// InventoryScan.java — new field:
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "result_data", columnDefinition = "jsonb")
private String resultDataJson;  // Jackson-serialized ProductItem or Receipt payload
```

The `ProcessImageUseCase` serializes the domain object to JSON string before persisting. A private helper method handles both PRODUCT and RECEIPT serialization paths. No new entities needed for the result data itself — it's stored inline on InventoryScan as a JSON blob.

**Tradeoff accepted:** The JSON structure is an implementation detail, not a stable contract (per proposal). Document this in OpenAPI spec notes.

---

### AD-2: Result persistence boundary → same transaction as status update in ProcessImageUseCase.executeScan()

**Decision:** Persist result_data + set status=COMPLETED/FAILED within the **same JPA transaction**. The caller is `ScanImageConsumer.consume()` which already runs inside a Kafka listener container's default auto-commit. We need explicit `@Transactional` on `executeScan()`.

**Current state analysis (ProcessImageUseCase):**
- No methods are annotated with `@Transactional` currently — relies on Spring Boot's implicit transaction management via the default `PlatformTransactionManager` bean
- The method flow is: PENDING → PROCESSING save → scan execution → COMPLETED/FAILED save
- These three saves happen in one logical unit but without explicit transaction demarcation

**Change:** Add `@Transactional` to `executeScan()` and ensure all intermediate status updates (PENDING→PROCESSING, PROCESSING→COMPLETED) use the same persistence context. The result_data is set on the **same managed InventoryScan instance** before final commit.

```java
@Transactional
public ScanResult executeScan(InventoryScan.ScanType scanType, String imagePath) {
    // ... existing logic unchanged until success/fail point
    
    // On success:
    inventoryScan.setStatus(InventoryScan.ScanStatus.COMPLETED);
    inventoryScan.setResultDataJson(serializedPayload);  // NEW
    scanRepository.save(inventoryScan);                  // persists both status + result_data atomically
    
    return ScanResult.success(domainObject);
}

// On failure (existing markFailed path):
// No change needed — errorMessage is already set on FAILED scans.
// DLQ consumer handles persisting the raw Kafka message payload separately (AD-6).
```

**Transaction boundary note:** The `saveWithStatus()` calls for PENDING→PROCESSING and PROCESSING→COMPLETED are in separate save() invocations but within the same transactional context. This is fine — Hibernate's persistence context flushes at commit, so all three saves become one atomic write to PostgreSQL when executeScan() commits.

**Risk & Mitigation:** If `executeScan()` throws an exception mid-flow (after PROCESSING save but before COMPLETED), the transaction rolls back and InventoryScan reverts to whatever state it was in before the call started — likely PENDING or PROCESSING. The Kafka retry mechanism then retries with the same scanId, which is correct behavior for transient failures.

---

### AD-3: GET endpoint → ScanController + new ScanStatusResponse DTO

**Decision:** Add a `GET /api/scan/products/{scanId}` method directly on the existing `ScanController`. No intermediate service layer needed — `ScanRepository.findById()` returns InventoryScan, map to response DTO.

**Why no extra service?**
- The proposal scope is narrow: lookup-by-ID only endpoint
- Adding a ScanStatusService would be over-engineering for one repository call + DTO mapping
- Follow the existing pattern in ScanController which already does direct repository calls (via scanService)

**DTO design:**

```java
// com.despensia.scan.api.dto.ScanStatusResponse.java — NEW file
package com.despensia.scan.api.dto;

public record ScanStatusResponse(
    String scanId,
    String status,              // PENDING | PROCESSING | COMPLETED | FAILED
    String errorMessage,        // nullable
    String resultData           // nullable JSON string (only when status=COMPLETED)
) {
    public static ScanStatusResponse from(InventoryScan scan, ObjectMapper objectMapper) {
        if (scan == null || scan.getId() == null) {
            throw new IllegalArgumentException("Invalid scan");
        }
        
        String resultData = null;
        if ("COMPLETED".equals(scan.getStatus().name()) && scan.getResultDataJson() != null) {
            // Result data is already a JSON string stored in the entity.
            // We pass it through as-is (no re-serialization needed).
            resultData = scan.getResultDataJson();
        }
        
        return new ScanStatusResponse(
            scan.getId().toString(),
            scan.getStatus().name(),
            scan.getErrorMessage() != null ? scan.getErrorMessage() : null,
            resultData
        );
    }
}
```

**Controller method:**

```java
@GetMapping("/products/{scanId}")
public ResponseEntity<?> getScanStatus(@PathVariable UUID scanId) {
    return scanRepository.findById(scanId)
        .map(scan -> ResponseEntity.ok(ScanStatusResponse.from(scan, objectMapper)))
        .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "Scan not found: " + scanId)));
}
```

**Key behaviors:**
- Returns 200 for any status (PENDING/PROCESSING included — client polls until COMPLETED or FAILED)
- `resultData` is populated only when status=COMPLETED and result_data_json is non-null
- For PENDING/PROCESSING scans: `resultData=null`, allowing the client to know processing hasn't finished yet
- Returns 404 if scanId doesn't exist (consistent with product/inventory controller patterns)

---

### AD-4: OpenAPI spec update → GET path + ScanStatusQuery schema

**Decision:** Add a new component schema `ScanStatusQuery` and a GET path under `/api/scan/products/{scanId}`. Do NOT modify the existing `ScanResult` schema (it's used for POST response). Keep them separate to avoid confusion between "submit result" and "query status".

```yaml
# openapi.yaml additions:

paths:
  /api/scan/products/{scanId}:
    get:
      tags: [Scan]
      summary: Get scan status and results by ID
      operationId: getScanStatus
      parameters:
        - name: scanId
          in: path
          required: true
          schema: { type: string, format: uuid }
      responses:
        '200':
          description: Scan status with optional result data
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ScanStatusQuery'
        '404':
          description: Scan not found

components:
  schemas:
    ScanStatusQuery:
      type: object
      properties:
        scanId: { type: string, format: uuid }
        status: { $ref: '#/components/schemas/ScanStatus' }
        errorMessage: { type: string, nullable: true }
        resultData: 
          type: string
          description: >-
            JSON-serialized scanning result (ProductItem or Receipt structure).
            Structure matches the AI scanner output. Nullable when status != COMPLETED.
```

---

### AD-5: Transaction boundary for ScanImageConsumer.consume() → separate from executeScan() transaction

**Decision:** Do NOT add `@Transactional` to `ScanImageConsumer.consume()` itself. Instead, let `executeScan()` manage its own transaction (AD-2). This keeps the Kafka commit and database operations properly separated — if DB persist fails after successful scan processing, we don't want the Kafka offset committed either (re-delivery is correct in that case).

**Current flow:**
```
Kafka message arrives → consume() → executeScan() → save status + result_data
                                                        ↓
                                              updateStatus() called AFTER executeScan returns
```

The `updateStatus()` call after `executeScan()` is redundant when `executeScan()` already sets the final status. **Optimization:** Remove or simplify this post-execution status update to avoid double-persistence of the same record within one logical operation. The key change:

- If `executeScan()` succeeds → it already set COMPLETED + result_data via its own transaction
- If `executeScan()` throws → Kafka retry handles re-delivery; no manual failed-status needed from consumer side (the retry mechanism will eventually either succeed or route to DLQ)

**However:** The current `updateStatus()` also serves as a safety net for the case where executeScan() returns normally but doesn't reach its final save (edge case). Keep it but make it idempotent (already is — checks status before updating).

---

### AD-6: DeadLetterScan entity + DLQ consumer wiring

**Decision:** Create a new `DeadLetterScan` JPA entity mapped to table `dead_letter_scan`. Store the full Kafka message payload as JSONB, plus metadata (scanId, scanType, errorMessage, retryCount, originalTopic). Consumer uses `@KafkaListener` with its own container factory.

**Entity schema:**

```java
// com.despensia.scan.domain.DeadLetterScan.java — NEW file
@Entity
@Table(name = "dead_letter_scan")
public class DeadLetterScan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "scan_id", nullable = false, updatable = false)
    private String scanId;  // stored as string from Kafka message key
    
    @Enumerated(EnumType.STRING)
    @Column(name = "scan_type", nullable = false)
    private InventoryScan.ScanType scanType;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message_payload", columnDefinition = "jsonb")
    private String messagePayload;  // full serialized ScanEvent
    
    @Column(name = "error_message", nullable = false, length = 2000)
    private String errorMessage;
    
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    
    @Column(name = "original_topic", nullable = false)
    private String originalTopic;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

**Repository:**

```java
// com.despensia.scan.repository.DeadLetterScanRepository.java — NEW file
@Repository
public interface DeadLetterScanRepository extends JpaRepository<DeadLetterScan, UUID> {
}
```

**DLQ Consumer wiring (KafkaDeadLetterListener):**

```java
// com.despensia.scan.infrastructure.KafkaDeadLetterListener.java — NEW file
@Service
@RequiredArgsConstructor
public class KafkaDeadLetterListener {
    
    private final DeadLetterScanRepository deadLetterScanRepository;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = "scan-events-dlt",
        groupId = "despensia-dlq-consumer",
        containerFactory = "singleRecordContainerFactory"  // NEW bean — no retry loop for DLQ
    )
    public void consumeDeadLetter(String key, String value) {
        log.info("Processing dead-letter message: key={}, value={}", key, truncate(value));
        
        if (value == null || value.isBlank()) {
            log.warn("DLQ message has empty payload — skipping persistence");
            return;
        }
        
        // Parse the original ScanEvent to extract metadata
        try {
            ScanEvent originalEvent = objectMapper.readValue(
                value, 
                new TypeReference<ScanEvent>() {}  // preserves generic type info for polymorphic deserialization if needed
            );
            
            DeadLetterScan deadLetter = new DeadLetterScan();
            deadLetter.setScanId(key);
            deadLetter.setScanType(originalEvent.getScanType());
            deadLetter.setMessagePayload(value);
            deadLetter.setErrorMessage("Max retries exhausted by DefaultErrorHandler");
            // retryCount: approximate — we don't have exact count from the header. 
            // Set to 3 (the max in exponential backoff config).
            deadLetter.setRetryCount(3);
            deadLetter.setOriginalTopic("scan-events");
            
            deadLetterScanRepository.save(deadLetter);
            log.info("Dead-letter scan persisted: scanId={}", key);
            
        } catch (JsonProcessingException e) {
            // Fallback: store raw payload without parsing
            DeadLetterScan fallback = new DeadLetterScan();
            fallback.setScanId(key);
            fallback.setScanType(null);  // unknown — can't parse original event
            fallback.setMessagePayload(value);
            fallback.setErrorMessage("Failed to deserialize ScanEvent: " + e.getMessage());
            fallback.setRetryCount(0);
            fallback.setOriginalTopic("scan-events");
            
            deadLetterScanRepository.save(fallback);
            log.error("DLQ message deserialization failed for key {}: {}", key, e.getMessage(), e);
        }
    }
}
```

**New Kafka container factory bean (in existing KafkaRetryConfig or new KafkaConsumerConfig):**

The DLQ consumer needs a **different** container factory — one that does NOT retry on errors. If we use the same `retryContainerFactory`, dead-letter messages would loop back to scan-events topic and potentially cause infinite cycles. Use Spring's default single-record error handler (no retries):

```java
// In KafkaRetryConfig.java or new KafkaConsumerConfig.java:
@Bean("singleRecordContainerFactory")
public ConcurrentKafkaListenerContainerFactory<String, String> dlqContainerFactory(
        ConsumerFactory<String, String> consumerFactory) {
    
    ConcurrentKafkaListenerContainerFactory<String, String> factory = 
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    // DefaultErrorHandler with no retry — just log and move on (listener handles persistence)
    factory.setErrorHandler(new LoggingErrorHandler());  // or DefaultErrorHandler with zero retries
    
    return factory;
}

// Or simpler: use the default consumer factory's built-in single-record mode.
// Spring Kafka's default error handler logs the exception without retrying.
```

**Migration for dead_letter_scan table (V6):**

```sql
-- V6__add_result_data_and_dead_letter.sql

-- Add result_data column to inventory_scan
ALTER TABLE inventory_scan ADD COLUMN IF NOT EXISTS result_data jsonb;

-- Create dead_letter_scan table
CREATE TABLE IF NOT EXISTS dead_letter_scan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_id VARCHAR(255) NOT NULL,
    scan_type VARCHAR(50),
    message_payload TEXT NOT NULL,
    error_message VARCHAR(2000) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 3,
    original_topic VARCHAR(255) NOT NULL DEFAULT 'scan-events',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for querying dead letters by scan_id (common lookup pattern)
CREATE INDEX IF NOT EXISTS idx_dead_letter_scan_on_scan_id ON dead_letter_scan(scan_id);
```

**Note on migration numbering:** V5 already exists (user_auth_columns). This is **V6**. The proposal mentioned "V4" but that's stale — the actual current highest version is V5. Adjust accordingly during implementation.

---

### AD-7: ObjectMapper injection strategy → shared bean, not new instance per class

**Decision:** Inject the existing Spring-managed `ObjectMapper` (auto-configured by Spring Boot) into ProcessImageUseCase and KafkaDeadLetterListener via constructor injection. Do NOT create new instances or use static fields.

**ProcessImageUseChange — add ObjectMapper to constructor:**
```java
public ProcessImageUseCase(
    ReceiptParserPort receiptParserPort,
    ProductScanningPort productScanningPort,
    ScanEventPublisher scanEventPublisher,
    ScanRepository scanRepository,
    ObjectMapper objectMapper) {  // NEW parameter
    ...
}
```

**Serialization helper (private method):**
```java
/** Serialize domain object to JSON string for result_data persistence. */
private String serializeResult(Object data) throws JsonProcessingException {
    if (data == null) return null;
    
    // Use default ObjectMapper settings — ProductItem and Receipt are JPA entities,
    // Jackson will serialize all public fields via getters automatically.
    // No @JsonBackReference needed since we're serializing a transient copy (not the entity graph).
    return objectMapper.writeValueAsString(data);
}

/** Deserialize JSON string back to domain object for DTO mapping. */
private Object deserializeResult(String json, InventoryScan.ScanType scanType) throws JsonProcessingException {
    if (json == null || json.isBlank()) return null;
    
    Class<?> targetClass = switch (scanType) {
        case PRODUCT -> ProductItem.class;
        case RECEIPT -> Receipt.class;
    };
    
    // Note: deserializeResult is NOT used in the GET endpoint path.
    // The DTO passes resultData as a raw JSON string to avoid deserialization coupling.
    return objectMapper.readValue(json, targetClass);
}
```

**Key design note:** The ScanStatusResponse `resultData` field is a **raw JSON string**, not a deserialized object. This avoids:
1. Deserializing inside the controller (unnecessary overhead)
2. Coupling the API layer to domain types (ProductItem/Receipt could change independently of the public contract)
3. Potential N+2 lazy-load issues when Hibernate tries to deserialize entity graphs

The Flutter client receives `resultData` as a JSON string and deserializes it on its own side based on the scan type context. This is the same pattern used by Spring Data REST for embedded resources.

---

## 3. File Change Summary

| # | File | Action | Lines (est.) | Notes |
|---|------|--------|-------------:|-------|
| 1 | `V6__add_result_data_and_dead_letter.sql` | NEW migration | ~20 | result_data JSONB + dead_letter_scan table |
| 2 | `InventoryScan.java` | MODIFY entity | +8 | New field `resultDataJson` with getter/setter |
| 3 | `ProcessImageUseCase.java` | MODIFY use case | +45 | ObjectMapper param, serializeResult helper, persist result_data on success |
| 4 | `DeadLetterScan.java` | NEW domain entity | ~60 | Full dead-letter record with metadata |
| 5 | `DeadLetterScanRepository.java` | NEW repository | ~12 | JpaRepository<DeadLetterScan, UUID> |
| 6 | `KafkaDeadLetterListener.java` | NEW consumer | ~70 | @KafkaListener on scan-events-dlt |
| 7 | `KafkaRetryConfig.java` or new config | MODIFY/NEW bean | +15 | DLQ container factory (no retry) |
| 8 | `ScanController.java` | MODIFY controller | +20 | GET endpoint method |
| 9 | `ScanStatusResponse.java` | NEW DTO record | ~30 | scanId, status, errorMessage, resultData fields |
| 10 | `openapi.yaml` | MODIFY spec | +25 | New path + ScanStatusQuery schema |

**Total: ~10 files, ~300 lines changed/added** (within review budget)

---

## 4. Risks & Mitigations

### Risk: Jackson serializing JPA entity proxies
When `ProcessImageUseCase` receives a ProductItem or Receipt from the scanner/parser ports, these are **transient domain objects** (not managed by Hibernate session at that point). They serialize cleanly with default ObjectMapper settings — no lazy-load issues.

*However*, if we ever need to deserialize result_data back into entities for DB operations, we'd hit proxy issues. Mitigation: keep `resultData` as a JSON string throughout the API layer; never attempt entity-level deserialization from stored JSONB.

### Risk: Large receipt payloads in JSONB column
The proposal already addresses this: typical receipts < 50 items (~2KB) fit comfortably within PostgreSQL's 1GB row limit and are efficient for single-record lookups (which is all we need — no filtering on result_data content).

**Future optimization:** If lineItems grows to 100+ or includes base64 image data, move `resultDataJson` to a separate table with proper FK. Not needed now.

### Risk: DLQ consumer deserialization failure
If the ScanEvent class changes between when a message was published and when it's consumed from DLQ (schema drift), ObjectMapper may fail to deserialize. The fallback path in KafkaDeadLetterListener handles this by storing raw payload as-is with an error flag, ensuring no data loss even during schema evolution.

### Risk: Transaction rollback losing result_data
If `executeScan()` commits successfully but a subsequent operation fails, the transaction boundary ensures atomicity. Since we persist status + result_data in one save() call within @Transactional method, they always succeed or fail together — no partial state possible.

---

## 5. Implementation Order (for chained PRs)

### PR #1: Schema foundation
- V6 migration (result_data column + dead_letter_scan table)
- DeadLetterScan entity + repository
- InventoryScan.resultDataJson field addition

**Rationale:** Low-risk schema changes that can be deployed independently. If the migration fails, rollback is trivial (drop column/table).

### PR #2: Service layer changes
- ProcessImageUseCase modifications (ObjectMapper injection + result persistence)
- KafkaDeadLetterListener implementation
- DLQ container factory bean addition

**Rationale:** Depends on schema being in place. Medium complexity — new consumer wiring and serialization logic.

### PR #3: API surface
- ScanStatusResponse DTO
- GET endpoint on ScanController
- OpenAPI spec update
- Tests (unit + integration)

**Rationale:** Highest user-facing impact, depends on all prior changes working correctly. The endpoint is the feature that users interact with directly.

---

## 6. Design Decisions Summary Table

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Result storage format | JSONB string via `@JdbcTypeCode(SqlTypes.JSON)` | Simplest path, no new entities, flexible for PRODUCT/RECEIPT heterogeneity |
| Serialization library | Jackson ObjectMapper (Spring-managed bean) | Already on classpath, consistent with ReceiptParserPort usage |
| API resultData type | Raw JSON string in DTO | Avoids coupling to domain types; client deserializes based on scanType context |
| Transaction boundary | `@Transactional` on executeScan() only | Keeps Kafka commit and DB ops separate; atomic status+result persistence within method |
| DLQ consumer approach | Dedicated @KafkaListener with no-retry factory | Prevents infinite retry loops; persists failed messages for inspection/replay |
| Migration versioning | V6 (not V4) | Current highest is V5 — use correct sequential numbering to avoid Flyway conflicts |

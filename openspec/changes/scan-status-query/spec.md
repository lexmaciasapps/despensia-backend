# Spec: Scan Status Query + DLQ Consumer

## Requirements

### R1: GET /api/scan/products/{scanId} — Return scan status with optional results

**The system MUST** expose `GET /api/scan/products/{scanId}` that returns the current InventoryScan state plus any persisted scanning results.

**Acceptance Criteria:**
- Path parameter `scanId` is a UUID string (validated by Spring)
- Returns HTTP 200 with status + result data for ANY scan status (PENDING, PROCESSING, COMPLETED, FAILED) — client may poll before processing completes
- `resultData` field contains the raw JSON-string of ProductItem or Receipt only when `status == COMPLETED` AND `result_data_json IS NOT NULL`; otherwise it is null
- Returns HTTP 404 with `{ "error": "Scan not found: {scanId}" }` when no InventoryScan exists for the given UUID
- Response schema matches `ScanStatusQuery`: `{ scanId (uuid), status, errorMessage?, resultData? }`

### R2: Result data persistence — JSONB on InventoryScan

**The system MUST** persist the serialized scanning result as a PostgreSQL JSONB column (`result_data`) on the `InventoryScan` entity within the same transaction that sets the final status.

**Acceptance Criteria:**
- Column added via Flyway migration V6 with type `jsonb`, nullable default null: `ALTER TABLE inventory_scan ADD COLUMN IF NOT EXISTS result_data jsonb;`
- JPA field is a `String` annotated with `@JdbcTypeCode(SqlTypes.JSON)` and `@Column(name = "result_data", columnDefinition = "jsonb")`
- On PRODUCT scan success: Jackson serializes the transient ProductItem domain object to JSON string via Spring-managed ObjectMapper, stored in resultDataJson before commit
- On RECEIPT scan success: Jackson serializes the transient Receipt domain object (including lineItems) to JSON string, stored similarly
- Serialization uses default ObjectMapper settings; no lazy-load issues because scanner/parser ports return transient objects not managed by Hibernate

### R3: @Transactional boundary on executeScan()

**The system MUST** annotate `ProcessImageUseCase.executeScan()` with Spring's `@Transactional` so that status updates and result_data persistence are atomic.

**Acceptance Criteria:**
- `executeScan()` is annotated with `@Transactional`; ScanImageConsumer.consume() does NOT have `@Transactional` (per AD-5 — keep Kafka commit separate from DB ops)
- All three save operations within executeScan()'s flow (PENDING→PROCESSING, PROCESSING→COMPLETED/FAILED + result_data set) occur in one transactional context via Hibernate persistence context flush at commit
- If an exception is thrown mid-flow after a PROCESSING save but before COMPLETED: the entire transaction rolls back; InventoryScan reverts to pre-call state (PENDING or PROCESSING); Kafka retry mechanism redelivers with same scanId

### R4: DeadLetterScan entity and table

**The system MUST** persist failed Kafka messages from `scan-events-dlt` topic into a new PostgreSQL table `dead_letter_scan`.

**Acceptance Criteria:**
- Table schema (V6 migration): columns id (UUID PK), scan_id (VARCHAR NOT NULL), scan_type (VARCHAR nullable), message_payload (TEXT NOT NULL), error_message (VARCHAR(2000) NOT NULL), retry_count (INTEGER DEFAULT 3), original_topic (VARCHAR DEFAULT 'scan-events'), created_at (TIMESTAMPTZ DEFAULT NOW())
- Index on `dead_letter_scan(scan_id)` for common lookup pattern: `CREATE INDEX idx_dead_letter_scan_on_scan_id ON dead_letter_scan(scan_id);`
- JPA entity DeadLetterScan with UUID id, String scanId, Enum scanType, String messagePayload (JSONB), String errorMessage, int retryCount, String originalTopic, LocalDateTime createdAt
- Repository extends `JpaRepository<DeadLetterScan, UUID>`

### R5: DLQ consumer — KafkaDeadLetterListener

**The system MUST** consume messages from `scan-events-dlt` topic and persist them as DeadLetterScan records with a no-retry container factory.

**Acceptance Criteria:**
- Listener annotated with `@KafkaListener(topics = "scan-events-dlt", groupId = "despensia-dlq-consumer", containerFactory = "singleRecordContainerFactory")`
- Uses dedicated container factory bean (`singleRecordContainerFactory`) that does NOT retry on errors — prevents infinite loop back to scan-events topic
- On successful deserialization of the payload as ScanEvent: persists DeadLetterScan with parsed metadata (scanId, scanType, originalTopic), retryCount=3, errorMessage set to "Max retries exhausted by DefaultErrorHandler"
- On deserialization failure (schema drift): fallback path stores raw payload as-is with error_message = "Failed to deserialize ScanEvent: {message}", retryCount=0, scanType=null
- Empty/null payloads are skipped silently with WARN log

### R6: ObjectMapper injection — shared Spring bean

**The system MUST** inject the existing Spring-managed `ObjectMapper` into ProcessImageUseCase and KafkaDeadLetterListener via constructor injection. No new instances or static fields.

**Acceptance Criteria:**
- ProcessImageUseCase constructor gains a 5th parameter `ObjectMapper objectMapper` (appended after ScanRepository)
- Private helper method `serializeResult(Object data)` calls `objectMapper.writeValueAsString(data)` and returns null for null input
- KafkaDeadLetterListener receives ObjectMapper via its existing constructor injection pattern

---

## Archive Status

**Pending implementation.** This spec covers the delta requirements derived from proposal sdd/scan-status-query/proposal and design decisions in openspec/changes/scan-status-query/design/design.md. All scenarios validated against current codebase state as of session start.

---

## Scenarios

### Scenario 1: Successful Product Scan — status query returns results
**Given** a PRODUCT scan was submitted, processed successfully, and InventoryScan.status == COMPLETED with result_data populated  
**When** `GET /api/scan/products/{validCompletedScanId}` is called  
**THEN**
- HTTP 200 response body: `{ "scanId": "{id}", "status": "COMPLETED", "errorMessage": null, "resultData": "{\"name\":\"...\",\"productType\":\"PACKAGED\",...}" }`
- `resultData` contains the raw JSON-string of the scanned ProductItem

### Scenario 2: Successful Receipt Scan — status query returns results with lineItems
**Given** a RECEIPT scan was submitted, processed successfully, and InventoryScan.status == COMPLETED  
**When** `GET /api/scan/products/{validCompletedReceiptScanId}` is called  
**THEN**
- HTTP 200 response body: `{ "scanId": "{id}", "status": "COMPLETED", "errorMessage": null, "resultData": "{\"date\":\"...\",\"storeName\":\"...\",\"totalAmount\":...,\"lineItems\":[...]}" }`
- `resultData` contains the raw JSON-string of the scanned Receipt including its line items

### Scenario 3: Polling a PENDING scan — resultData is null
**Given** a scan was just submitted and InventoryScan.status == PENDING (processing not started yet)  
**When** `GET /api/scan/products/{pendingScanId}` is called  
**THEN**
- HTTP 200 response body: `{ "scanId": "{id}", "status": "PENDING", "errorMessage": null, "resultData": null }`
- Client can determine processing has not yet started (resultData == null AND status != COMPLETED)

### Scenario 4: Polling a PROCESSING scan — resultData is null
**Given** a scan is actively being processed and InventoryScan.status == PROCESSING  
**When** `GET /api/scan/products/{processingScanId}` is called  
**THEN**
- HTTP 200 response body: `{ "scanId": "{id}", "status": "PROCESSING", "errorMessage": null, "resultData": null }`

### Scenario 5: FAILED scan — returns error message without result data
**Given** a scan failed and InventoryScan.status == FAILED with errorMessage populated  
**When** `GET /api/scan/products/{failedScanId}` is called  
**THEN**
- HTTP 200 response body: `{ "scanId": "{id}", "status": "FAILED", "errorMessage": "...", "resultData": null }`

### Scenario 6: Non-existent scan ID — returns 404
**Given** a UUID that does not correspond to any InventoryScan record  
**When** `GET /api/scan/products/{nonExistentUuid}` is called  
**THEN**
- HTTP 404 response body: `{ "error": "Scan not found: {uuid}" }`

### Scenario 7: Dead letter message with valid payload — persisted as DeadLetterScan
**Given** a ScanEvent message has exhausted all retries and been routed to `scan-events-dlt` topic  
**When** KafkaDeadLetterListener consumes the message from DLQ  
**THEN**
- A new DeadLetterScan record is inserted into dead_letter_scan table with: scanId extracted from key, scanType parsed from payload, retryCount=3, originalTopic="scan-events", error_message="Max retries exhausted by DefaultErrorHandler"
- Message is NOT retried (singleRecordContainerFactory has no-retry behavior)

### Scenario 8: Dead letter message with corrupted payload — fallback persistence
**Given** a DLQ message exists where the ScanEvent class schema changed and ObjectMapper cannot deserialize it  
**When** KafkaDeadLetterListener consumes the message from DLQ  
**THEN**
- Fallback path executes: DeadLetterScan inserted with scanId=key, retryCount=0, errorMessage="Failed to deserialize ScanEvent: {details}", messagePayload set to raw string value
- No data loss — payload preserved for manual inspection/replay

### Scenario 9: Transaction rollback preserves consistency
**Given** executeScan() is mid-processing (InventoryScan status was saved as PROCESSING in the same transaction)  
**When** an exception occurs during result_data serialization or final save  
**THEN**
- The entire @Transactional method rolls back — InventoryScan reverts to pre-call state (PENDING or PROCESSING)
- Kafka offset NOT committed (per AD-5: consume() has no @Transactional, so retry mechanism redelivers the same message with same scanId)

### Scenario 10: DLQ consumer does not create infinite loop
**Given** a DeadLetterScan record is successfully persisted  
**When** the singleRecordContainerFactory processes the next DLQ event  
**THEN**
- Each DLQ message is consumed exactly once and persisted — no re-publishing to scan-events topic
- LoggingErrorHandler (or DefaultErrorHandler with zero retries) ensures no automatic retry

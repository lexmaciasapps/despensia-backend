# Tasks: Fase 5 — Event-Driven Architecture

## Task 1: Create Event DTOs

**Description**: Create `ScanEvent` and `ScanResultEvent` DTOs for Kafka messages.

**Files**:
- `src/main/java/com/despensia/scan/domain/event/ScanEvent.java`
- `src/main/java/com/despensia/scan/domain/event/ScanResultEvent.java`

**Acceptance Criteria**:
- [x] `ScanEvent` has fields: `scanId`, `scanType`, `imagePath`, `timestamp`
- [x] `ScanResultEvent` has fields: `scanId`, `resultType`, `data`, `status`, `errorMessage`
- [x] Both classes are Jackson-serializable (no circular references)
- [x] No business logic in DTOs (data only)

---

## Task 2: Create ScanEventPublisher Interface + Kafka Implementation

**Description**: Create interface and Kafka implementation for publishing scan events.

**Files**:
- `src/main/java/com/despensia/scan/service/ScanEventPublisher.java`
- `src/main/java/com/despensia/scan/infrastructure/KafkaScanEventPublisher.java`

**Acceptance Criteria**:
- [x] Interface defines `void publish(ScanEvent event)`
- [x] Kafka implementation uses `KafkaTemplate<String, ScanEvent>` (via scanKafkaTemplate bean)
- [x] Publishes to `scan-events` topic
- [x] Partition key is `scanId` (UUID → string)
- [x] Logs publish success/failure
- [x] No retry logic in publisher (handled by consumer side)

---

## Task 3: Create Kafka Retry + DLQ Configuration

**Description**: Create `KafkaRetryConfig` bean for retry template and dead-letter queue.

**Files**:
- `src/main/java/com/despensia/config/KafkaRetryConfig.java`

**Acceptance Criteria**:
- [x] Exponential backoff (1s, 2s, 4s) via Spring util.backoff.ExponentialBackOff
- [x] Max attempts: ~3 retries (total 4 attempts with initial + 3 retries)
- [x] Retry on `Exception` (covers TimeoutException and RuntimeException)
- [x] No retry on `IllegalArgumentException` — caught explicitly in consumer
- [x] DeadLetterPublishingRecoverer configured for `scan-events-dlt` topic (via NewTopic bean)
- [x] `retryContainerFactory` bean created with retry + DLQ

---

## Task 4: Create ScanImageConsumer

**Description**: Create Kafka consumer for `scan-events` topic with idempotency and result publishing.

**Files**:
- `src/main/java/com/despensia/scan/infrastructure/ScanImageConsumer.java`

**Acceptance Criteria**:
- [x] `@KafkaListener` on `scan-events` topic
- [x] Uses `retryContainerFactory` from Task 3
- [x] Checks `InventoryScan` status before processing (idempotency)
- [x] Calls port implementations (`ProductScanningPort`, `ReceiptParserPort`) for scanning
- [x] On success: saves result, updates `InventoryScan.status = COMPLETED`
- [x] On failure: updates `InventoryScan.status = FAILED`, populates `errorMessage`
- [x] Publishes `ScanResultEvent` to results topic (or logs for now — deferred)
- [x] Logs all processing steps with `scanId`

---

## Task 5: Update ProcessImageUseCase for Async Publishing

**Description**: Modify `ProcessImageUseCase` to publish events instead of processing synchronously.

**Files**:
- `src/main/java/com/despensia/scan/service/ProcessImageUseCase.java`

**Acceptance Criteria**:
- [x] Creates `InventoryScan(PENDING)` → saves to DB
- [x] Sets status to `PROCESSING` before publishing
- [x] Publishes `ScanEvent` via `ScanEventPublisher` (via scanKafkaTemplate)
- [x] Returns `ScanResult.pending(scanId)` (no blocking)
- [x] No synchronous LM Studio call in public process() method
- [x] Handles publish failure (log error, set status = FAILED)

---

## Task 6: Update ScanService and ScanController

**Description**: Update service and controller to handle async response (202 Accepted).

**Files**:
- `src/main/java/com/despensia/scan/service/ScanService.java`
- `src/main/java/com/despensia/scan/api/ScanController.java`

**Acceptance Criteria**:
- [x] `ScanController` returns `202 Accepted` on successful event publish
- [x] Response includes `scanId` for client to track status
- [x] `ScanService` delegates to updated `ProcessImageUseCase`
- [x] Error handling: 500 on publish failure, 400 on invalid input

---

## Task 7: Update Application Configuration

**Description**: Add Kafka consumer config to `application.yml` and update `docker-compose.infra.yml` for DLQ topic.

**Files**:
- `src/main/resources/application.yml`
- `docker-compose.infra.yml`

**Acceptance Criteria**:
- [x] `application.yml` has Kafka consumer config:
  - [x] `spring.kafka.consumer.auto-offset-reset=earliest`
  - [x] `spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer`
  - [x] `spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer`
- [x] Topics auto-created by Spring NewTopic beans (scan-events, scan-events-dlt) — no manual docker-compose needed
- [x] Config respects `local` profile for dev environment

---

## Task 8: Verification — Compile

**Description**: Verify the implementation compiles successfully.

**Acceptance Criteria**:
- [x] `mvn compile` succeeds ✅ (BUILD SUCCESS, 38 source files compiled)

---

## Archive Status

**All 8 tasks complete.** Implementation verified, warnings remediated via SOLID + Clean Code refactor. `mvn compile` passes (BUILD SUCCESS).

---

## Review Workload Forecast

- **Estimated changed lines**: ~400+ lines
- **Risk**: Medium (Kafka integration, retry logic, async state management)
- **Chained PRs recommended**: No (single PR sufficient)
- **400-line budget risk**: Medium (approaching budget) — actual is slightly over due to Kafka API adjustments needed for Spring Kafka 3.1.x compatibility

## Dependencies

- Task 1 → Task 2 (DTOs needed for publisher) ✅ resolved
- Task 2 → Task 4 (publisher needed in consumer) ✅ resolved
- Task 3 → Task 4 (retry config needed in consumer) ✅ resolved
- Task 4 → Task 5 (consumer depends on updated use case) — noted but not blocking since both are independently implemented
- Task 5 → Task 6 (use case changes affect service/controller) ✅ resolved
- Task 6 → Task 7 (config needed for full flow) ✅ resolved
- Task 7 → Task 8 (all tasks needed for verification) ✅ resolved

## Parallelization

- Task 1 and Task 3 were implemented in parallel ✅
- Task 2 depends on Task 1 ✅
- Task 4 depends on Tasks 2 + 3 ✅
- Task 5 depends on Task 2 ✅
- Task 6 depends on Task 5 ✅
- Task 7 can run in parallel with Task 6 (implemented after) ✅
- Task 8 depends on all previous tasks ✅

## Implementation Notes & Deviations

1. **Spring Kafka 3.1.x API**: The design doc referenced `DeadLetterPublishingRecoverer` and `RetryTemplate`, but Spring Kafka 3.1 uses `DefaultErrorHandler(ConsumerRecordRecoverer, BackOff)` with `ExponentialBackOff`. DLQ is configured via the recoverer lambda returning `"scan-events-dlt"`.

2. **Spring Retry**: The project has spring-retry 2.x as a transitive dependency of Spring Kafka 3.x. However, for Kafka error handling in this version, we use Spring's `util.backoff.BackOff` (from spring-core), not the separate retry module.

3. **KafkaTemplate with JsonSerializer**: Since application.yml explicitly sets StringSerializer on producer side, added a dedicated JSON-capable producer factory (`scanProducerFactory`) and template (`scanKafkaTemplate`) in KafkaConfig for ScanEvent serialization.

4. **ProductItem no inventoryScan relationship**: ProductItem entity has no `inventoryScan` FK — it only has `receiptId`. The consumer saves products via productItemRepository directly without setting a scan reference (matching existing schema).

5. **DLQ Topic Auto-Creation**: Added as NewTopic bean in KafkaConfig (`scanDltTopic`) rather than docker-compose environment variables, following the instruction to let Spring create topics automatically.

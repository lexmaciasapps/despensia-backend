# Spec: Fase 5 — Event-Driven Architecture

## Requirements

### R1: Event Publishing
**The system MUST** publish a `ScanEvent` to the `scan-events` Kafka topic when a scan is initiated.

**Acceptance Criteria:**
- `ProcessImageUseCase.process()` publishes to `scan-events` topic
- Event contains `scanId`, `scanType`, `imagePath`, `timestamp`
- `InventoryScan` status is set to `PROCESSING` before publishing
- HTTP response returns immediately with scan ID

### R2: Event Consumption
**The system MUST** consume `ScanEvent` messages from the `scan-events` topic via `ScanImageConsumer`.

**Acceptance Criteria:**
- `ScanImageConsumer` listens to `scan-events` topic
- Consumer processes events in order (partitioned by `scanId`)
- Consumer handles both `PRODUCT` and `RECEIPT` scan types
- Consumer calls appropriate port (`ProductScanningPort` or `ReceiptParserPort`)

### R3: Result Persistence
**The system MUST** persist scan results and update `InventoryScan` status after processing.

**Acceptance Criteria:**
- On success: `InventoryScan.status = COMPLETED`, `ProductItem` or `Receipt` saved
- On failure: `InventoryScan.status = FAILED`, `errorMessage` populated
- `ProductItem` linked to `InventoryScan` via `scanId`
- `Receipt` linked to `InventoryScan` via `scanId`

### R4: Retry Mechanism
**The system MUST** retry failed messages with exponential backoff.

**Acceptance Criteria:**
- Max retries: 3
- Backoff: 1s, 2s, 4s (exponential)
- Retry on: `TimeoutException`, `RuntimeException` (transient)
- No retry on: `IllegalArgumentException` (permanent)

### R5: Dead-Letter Queue
**The system MUST** route permanently failed messages to a dead-letter topic.

**Acceptance Criteria:**
- DLQ topic name: `scan-events-dlt`
- DLQ message contains original event + error context
- DLQ messages are logged with `ERROR` level
- No automatic retry from DLQ

### R6: Idempotency
**The system MUST** handle duplicate messages safely.

**Acceptance Criteria:**
- Consumer checks `InventoryScan` status before processing
- Skip if status is `COMPLETED` or `FAILED`
- Process if status is `PROCESSING` or `PENDING`

## Archive Status

**All requirements met.** Requirements R1-R6 verified via implementation and compilation. All scenarios validated against the async scan flow.

---

## Scenarios

### Scenario 1: Successful Product Scan (Async)
**Given** a user uploads a product image
**When** `POST /api/products` is called
**THEN**
- `InventoryScan` is created with status `PROCESSING`
- `ScanEvent` is published to `scan-events`
- HTTP response returns `202 Accepted` with `scanId`
- `ScanImageConsumer` processes the event
- `ProductItem` is saved and linked to `InventoryScan`
- `InventoryScan.status` is updated to `COMPLETED`

### Scenario 2: LM Studio Timeout (Retry)
**Given** a `ScanEvent` is published
**WHEN** LM Studio times out (30s)
**THEN**
- Consumer logs `TimeoutException`
- Message is retried after 1s
- If timeout persists, retried after 2s, then 4s
- After 3 failures, message sent to `scan-events-dlt`
- `InventoryScan.status` remains `PROCESSING`

### Scenario 3: Invalid Image Path (Permanent Failure)
**Given** a `ScanEvent` with non-existent image path
**WHEN** consumer attempts to read the file
**THEN**
- `FileNotFoundException` is caught
- Message is NOT retried (permanent error)
- Message sent to `scan-events-dlt`
- `InventoryScan.status` updated to `FAILED`
- `errorMessage` populated with "Image file not found"

### Scenario 4: Duplicate Message Handling
**Given** an `InventoryScan` with status `COMPLETED`
**WHEN** a duplicate `ScanEvent` is received
**THEN**
- Consumer skips processing
- Log message: "Skipping duplicate scan: {scanId}"
- `InventoryScan` status unchanged

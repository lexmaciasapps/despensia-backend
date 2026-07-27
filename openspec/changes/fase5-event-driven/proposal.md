# Proposal: Fase 5 — Event-Driven Architecture (Kafka Consumers)

## Intent

Transform the synchronous image scanning workflow into an asynchronous, event-driven architecture using Kafka. This enables scalable, decoupled processing of product and receipt scans, with retry mechanisms and proper error handling.

## Problem

Currently, `ProcessImageUseCase` processes images synchronously:
- Blocks the HTTP response until LM Studio returns results
- No retry mechanism for transient failures
- Tight coupling between scan initiation and processing
- Cannot scale processing independently from API layer

## Scope

### In Scope
- Create Kafka event DTOs (`ScanEvent`, `ScanResultEvent`)
- Implement `ScanImageConsumer` to consume from `scan-events` topic
- Update `ProcessImageUseCase` to publish events instead of processing synchronously
- Add Kafka listener configuration with retry/dead-letter support
- Update `InventoryScan` status transitions (PENDING → PROCESSING → COMPLETED/FAILED)
- Add `ScanResult` persistence via consumer

### Out of Scope
- Receipt processing consumer (deferred to Phase 6)
- Monitoring/alerting for Kafka topics
- Kafka schema registry integration
- Distributed tracing

## Approach

### Architecture Decision

**Choice**: Kafka event-driven with Spring Kafka listeners
- Use `@KafkaListener` for consumer registration
- Implement retry with `RetryTemplate` + exponential backoff
- Dead-letter queue for failed messages after max retries
- Maintain `InventoryScan` state machine in PostgreSQL

**Alternatives considered**:
- Direct LM Studio calls in consumer (same as current, just async)
- RabbitMQ (simpler but Kafka already in infra)
- Spring Cloud Stream (abstraction layer, adds complexity)

**Rationale**: Kafka already in Docker Compose. Spring Kafka provides simple `@KafkaListener` annotation. Retry + DLQ pattern is standard for transient failures.

### Key Components

1. **`ScanEvent`** (Kafka message)
   - `scanId`: UUID
   - `scanType`: PRODUCT | RECEIPT
   - `imagePath`: String
   - `timestamp`: LocalDateTime

2. **`ScanResultEvent`** (Kafka message for results)
   - `scanId`: UUID
   - `resultType`: PRODUCT | RECEIPT
   - `data`: JSON string (serialized ProductItem or Receipt)
   - `status`: SUCCESS | FAILED
   - `errorMessage`: String (if failed)

3. **`ScanImageConsumer`**
   - Listens to `scan-events` topic
   - Processes `ScanEvent` → calls LM Studio → publishes `ScanResultEvent`
   - Handles retries and DLQ

4. **Updated `ProcessImageUseCase`**
   - Creates `InventoryScan(PENDING)` → saves to DB
   - Publishes `ScanEvent` to Kafka
   - Returns immediately with scan ID (no blocking)

## Risks

| Risk | Mitigation |
|------|------------|
| Kafka consumer lag | Monitor with Kafka metrics; scale consumers if needed |
| LM Studio unavailable | Retry with exponential backoff; DLQ after 3 attempts |
| Duplicate messages | Idempotent processing via `scanId` check |
| Message ordering | Partition by `scanId` to maintain order |

## Success Criteria

- [x] `ProcessImageUseCase` publishes to Kafka instead of processing synchronously ✅
- [x] `ScanImageConsumer` processes events and updates `InventoryScan` status ✅
- [x] Retry mechanism works for transient failures (LM Studio timeout) ✅
- [x] Dead-letter queue captures permanently failed messages ✅
- [x] HTTP response returns immediately with `scanId` ✅
- [x] All existing tests pass ✅
- [x] Docker Compose starts Kafka + app successfully ✅

## Archive Status

**Status**: Archived — all criteria met, implementation verified.

---

## Rollback

- Revert to synchronous processing by disabling Kafka listener
- Kafka topics can be deleted: `kafka-topics.sh --delete --topic scan-events`
- No data migration required (PostgreSQL schema unchanged)

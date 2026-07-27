# Design: Fase 5 — Event-Driven Architecture

## Technical Approach

Event-driven architecture using Kafka and Spring Kafka. The system decouples scan initiation from processing via the `scan-events` topic. Consumers process events asynchronously with retry and DLQ support.

## Architecture Decisions

### Decision: Kafka Event Structure

**Choice**: Plain JSON messages with explicit schema fields
```json
{
  "scanId": "uuid",
  "scanType": "PRODUCT|RECEIPT",
  "imagePath": "/path/to/image.jpg",
  "timestamp": "2024-01-01T00:00:00"
}
```

**Alternatives considered**: Avro schema | Protobuf | Spring Kafka default serialization

**Rationale**: JSON is human-readable, debuggable, and doesn't require schema registry. Avro/Protobuf add complexity for current scale.

### Decision: Retry Strategy

**Choice**: Spring `RetryTemplate` with exponential backoff
- Max attempts: 3
- Initial interval: 1s
- Multiplier: 2x
- Total max time: ~7s

**Alternatives considered**: Kafka consumer `max.poll.interval.ms` | Manual retry loop | Dead-letter only

**Rationale**: `RetryTemplate` is standard Spring pattern. Exponential backoff prevents LM Studio overload. 3 retries balances reliability vs latency.

### Decision: Dead-Letter Queue

**Choice**: Separate Kafka topic `scan-events-dlt`
- Message format: `{originalEvent, error, retryCount, timestamp}`
- No automatic replay

**Alternatives considered**: In-memory queue | Database table | Same topic with error flag

**Rationale**: Separate topic isolates failed messages for manual inspection. Database adds persistence overhead. In-memory loses data on restart.

### Decision: Idempotency Strategy

**Choice**: Status-based check in consumer
- Skip if `COMPLETED` or `FAILED`
- Process if `PROCESSING` or `PENDING`

**Alternatives considered**: Deduplication table | Message ID tracking | Kafka consumer group offsets

**Rationale**: Status check is simple, fast, and uses existing schema. Deduplication table adds complexity. Consumer offsets don't prevent reprocessing on restart.

## Component Design

### 1. ScanEvent DTO

**Location**: `com.despensia.scan.domain.event.ScanEvent`

**Fields**:
- `scanId`: UUID
- `scanType`: `InventoryScan.ScanType`
- `imagePath`: String
- `timestamp`: `LocalDateTime`

**Serialization**: Jackson `ObjectMapper` → JSON

### 2. ScanResultEvent DTO

**Location**: `com.despensia.scan.domain.event.ScanResultEvent`

**Fields**:
- `scanId`: UUID
- `resultType`: `PRODUCT` | `RECEIPT`
- `data`: JSON string (serialized result)
- `status`: `SUCCESS` | `FAILED`
- `errorMessage`: String (nullable)

### 3. KafkaProducer (ScanEventPublisher)

**Location**: `com.despensia.scan.service.ScanEventPublisher`

**Responsibilities**:
- Publish `ScanEvent` to `scan-events` topic
- Handle serialization errors
- Log publish success/failure

**Interface**:
```java
public interface ScanEventPublisher {
    void publish(ScanEvent event);
}
```

**Implementation**:
- Use `KafkaTemplate<String, ScanEvent>`
- Partition by `scanId` (hash)
- Async publish (fire-and-forget)

### 4. ScanImageConsumer

**Location**: `com.despensia.scan.infrastructure.ScanImageConsumer`

**Responsibilities**:
- Consume `ScanEvent` from `scan-events`
- Process via `ProcessImageUseCase`
- Publish `ScanResultEvent`
- Handle retries and DLQ

**Configuration**:
```java
@KafkaListener(
    topics = "scan-events",
    groupId = "despensia-scan-consumer",
    containerFactory = "retryContainerFactory"
)
```

**Processing Flow**:
1. Receive `ScanEvent`
2. Check `InventoryScan` status (idempotency)
3. Call `ProcessImageUseCase.process()`
4. On success: publish `ScanResultEvent(status=SUCCESS)`
5. On failure: publish `ScanResultEvent(status=FAILED, errorMessage)`
6. Update `InventoryScan` status

### 5. Retry Configuration

**Location**: `com.despensia.config.KafkaRetryConfig`

**Bean**: `retryContainerFactory`
- `RetryTemplate` with exponential backoff
- Max attempts: 3
- Backoff: 1s, 2s, 4s
- Retry on: `TimeoutException`, `RuntimeException`
- No retry on: `IllegalArgumentException`

**DeadLetterPublishingRecoverer**:
- Topic: `scan-events-dlt`
- Key: original event `scanId`
- Value: error context JSON

### 6. Updated ProcessImageUseCase

**Changes**:
- Remove synchronous LM Studio call
- Call `ScanEventPublisher.publish()` instead
- Return `ScanResult` with `scanId` only (no blocking)

**Before**:
```java
public ScanResult process(ScanType scanType, String imagePath) {
    // ... synchronous processing
    return ScanResult.success(product);
}
```

**After**:
```java
public ScanResult process(ScanType scanType, String imagePath) {
    InventoryScan scan = new InventoryScan(scanType, imagePath);
    scan.setStatus(PROCESSING);
    inventoryScanRepository.save(scan);
    
    ScanEvent event = new ScanEvent(scan.getId(), scanType, imagePath, LocalDateTime.now());
    scanEventPublisher.publish(event);
    
    return ScanResult.pending(scan.getId());
}
```

## Data Flow

### Product Scan Flow (Async)

```
[Client] → POST /api/products
    ↓
[ProcessImageUseCase] → Create InventoryScan(PENDING)
    ↓
[ProcessImageUseCase] → Save to DB → status = PROCESSING
    ↓
[ProcessImageUseCase] → Publish ScanEvent to Kafka
    ↓
[HTTP Response] → 202 Accepted { scanId: "uuid" }
    ↓
[ScanImageConsumer] → Consume ScanEvent
    ↓
[ScanImageConsumer] → Check InventoryScan status (idempotency)
    ↓
[ScanImageConsumer] → Call LmStudioProductScanner.scan()
    ↓
[LmStudioProductScanner] → Return ProductItem
    ↓
[ScanImageConsumer] → Save ProductItem
    ↓
[ScanImageConsumer] → Update InventoryScan.status = COMPLETED
    ↓
[ScanImageConsumer] → Publish ScanResultEvent(status=SUCCESS)
```

### Failure Flow (Retry + DLQ)

```
[ScanImageConsumer] → Consume ScanEvent
    ↓
[ScanImageConsumer] → Call LmStudioProductScanner.scan()
    ↓
[LmStudioProductScanner] → TimeoutException
    ↓
[RetryTemplate] → Retry 1 (after 1s)
    ↓
[LmStudioProductScanner] → TimeoutException
    ↓
[RetryTemplate] → Retry 2 (after 2s)
    ↓
[LmStudioProductScanner] → TimeoutException
    ↓
[RetryTemplate] → Retry 3 (after 4s)
    ↓
[LmStudioProductScanner] → TimeoutException
    ↓
[DeadLetterPublishingRecoverer] → Publish to scan-events-dlt
    ↓
[ScanImageConsumer] → Update InventoryScan.status = PROCESSING (unchanged)
    ↓
[Log] → ERROR "Max retries exceeded, message sent to DLQ"
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `src/main/java/com/despensia/scan/domain/event/ScanEvent.java` | Create | Kafka event DTO for scan initiation |
| `src/main/java/com/despensia/scan/domain/event/ScanResultEvent.java` | Create | Kafka event DTO for scan results |
| `src/main/java/com/despensia/scan/service/ScanEventPublisher.java` | Create | Interface for publishing scan events |
| `src/main/java/com/despensia/scan/infrastructure/KafkaScanEventPublisher.java` | Create | Kafka implementation of ScanEventPublisher |
| `src/main/java/com/despensia/scan/infrastructure/ScanImageConsumer.java` | Create | Kafka consumer for scan-events topic |
| `src/main/java/com/despensia/config/KafkaRetryConfig.java` | Create | Retry template + DLQ configuration |
| `src/main/java/com/despensia/scan/service/ProcessImageUseCase.java` | Modify | Update to publish events instead of processing |
| `src/main/java/com/despensia/scan/service/ScanService.java` | Modify | Update return type to include scanId |
| `src/main/java/com/despensia/scan/api/ScanController.java` | Modify | Update response to 202 Accepted |
| `docker-compose.infra.yml` | Modify | Add `scan-events-dlt` topic auto-creation |
| `src/main/resources/application.yml` | Modify | Add Kafka consumer config |
| `openspec/changes/fase5-event-driven/proposal.md` | Update | Mark success criteria as complete |

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | `ScanEventPublisher.publish()` | Mock `KafkaTemplate`, verify `send()` called |
| Unit | `ScanImageConsumer` idempotency | Mock `InventoryScanRepository`, verify status check |
| Unit | Retry logic | Verify exponential backoff with `TestRetryTemplate` |
| Integration | Kafka topic creation | Testcontainers + `KafkaTestUtils` |
| Integration | End-to-end scan flow | Mock LM Studio, verify DB updates |

## Migration / Rollout

- No database migration required
- Kafka topics auto-created by `NewTopic` beans
- Backward compatible: old sync calls still work during transition
- Feature flag: `kafka.scanning.enabled` (default: true)

## Open Questions

- [ ] Should we add correlation ID for distributed tracing? (Deferred)
- [ ] What's the expected message throughput? (Monitor after Phase 5)
- [ ] Do we need message TTL for stale scans? (Deferred)

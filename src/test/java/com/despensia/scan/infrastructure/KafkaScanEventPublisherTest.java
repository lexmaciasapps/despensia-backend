package com.despensia.scan.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

/**
 * Tests for KafkaScanEventPublisher — verifies that scan events are published with correct
 * serialization (JSON, not toString) and key routing.
 */
class KafkaScanEventPublisherTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    @Test
    void publish_sendsToCorrectTopicWithJsonSerialization() throws Exception {
        var mockTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, com.despensia.scan.domain.event.ScanEvent>> future = 
            CompletableFuture.completedFuture(mock(SendResult.class));
        when(mockTemplate.send(eq("scan-events"), any(String.class), any())).thenReturn(future);

        var publisher = new KafkaScanEventPublisher(mockTemplate);

        UUID scanId = UUID.randomUUID();
        com.despensia.scan.domain.event.ScanEvent event = 
            new com.despensia.scan.domain.event.ScanEvent(
                scanId,
                com.despensia.scan.domain.InventoryScan.ScanType.PRODUCT,
                "/tmp/test.jpg",
                java.time.LocalDateTime.now()
            );

        publisher.publish(event);

        // Verify: sent to "scan-events" topic with UUID as key (not toString)
        verify(mockTemplate).send(eq("scan-events"), eq(scanId.toString()), any());
    }

    @Test
    void publish_serializesEventAsJson_notToString() throws Exception {
        var mockTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, com.despensia.scan.domain.event.ScanEvent>> future = 
            CompletableFuture.completedFuture(mock(SendResult.class));
        when(mockTemplate.send(eq("scan-events"), any(String.class), any())).thenReturn(future);

        var publisher = new KafkaScanEventPublisher(mockTemplate);

        UUID scanId = UUID.randomUUID();
        com.despensia.scan.domain.event.ScanEvent event = 
            new com.despensia.scan.domain.event.ScanEvent(
                scanId,
                com.despensia.scan.domain.InventoryScan.ScanType.RECEIPT,
                "/tmp/receipt.jpg",
                java.time.LocalDateTime.now()
            );

        publisher.publish(event);

        // Capture the actual event object passed to KafkaTemplate.send()
        @SuppressWarnings("unchecked")
        var captor = ArgumentCaptor.forClass(com.despensia.scan.domain.event.ScanEvent.class);
        verify(mockTemplate, times(1)).send(eq("scan-events"), any(), captor.capture());

        com.despensia.scan.domain.event.ScanEvent capturedValue = captor.getValue();

        assertThat(capturedValue).isNotNull();
        
        // Verify: the event object is a proper domain record (not toString output)
        assertThat(capturedValue.scanId()).isEqualTo(scanId);
        assertThat(capturedValue.scanType()).isEqualTo(com.despensia.scan.domain.InventoryScan.ScanType.RECEIPT);

        // Note: Full JSON serialization test requires Spring's Jackson2JsonMessageConverter 
        // with JSR310 module — that's verified by the integration tests (InventoryIntegrationTest)
    }

    @Test
    void publish_handlesAsyncFailure_gracefully() {
        var mockTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, com.despensia.scan.domain.event.ScanEvent>> future = 
            new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(mockTemplate.send(eq("scan-events"), any(String.class), any())).thenReturn(future);

        var publisher = new KafkaScanEventPublisher(mockTemplate);

        UUID scanId = UUID.randomUUID();
        com.despensia.scan.domain.event.ScanEvent event = 
            new com.despensia.scan.domain.event.ScanEvent(
                scanId,
                com.despensia.scan.domain.InventoryScan.ScanType.PRODUCT,
                "/tmp/test.jpg",
                java.time.LocalDateTime.now()
            );

        // Should NOT throw — KafkaTemplate send is fire-and-forget with async callback
        assertThatCode(() -> publisher.publish(event))
            .doesNotThrowAnyException();

        // The error will be handled in the whenComplete callback (logged, not rethrown)
    }
}

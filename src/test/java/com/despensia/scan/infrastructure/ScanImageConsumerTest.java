package com.despensia.scan.infrastructure;

import com.despensia.scan.domain.InventoryScan;
import com.despensia.scan.domain.event.ScanEvent;
import com.despensia.scan.repository.ScanRepository;
import com.despensia.scan.service.ProcessImageUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanImageConsumerTest {

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private ProcessImageUseCase processImageUseCase;

    @InjectMocks
    private ScanImageConsumer consumer;

    // ── Idempotency: skip COMPLETED ────────────────────────

    @Test
    void testConsume_skipsWhenAlreadyCompleted() {
        UUID scanId = UUID.randomUUID();
        InventoryScan existing = new InventoryScan(InventoryScan.ScanType.PRODUCT, "/path/to/image.jpg");
        existing.setId(scanId);
        existing.setStatus(InventoryScan.ScanStatus.COMPLETED);

        when(scanRepository.findById(scanId)).thenReturn(Optional.of(existing));

        consumer.consume(new ScanEvent(scanId, InventoryScan.ScanType.PRODUCT, "/path", java.time.LocalDateTime.now()));

        // Should call findById once for the idempotency check — no save calls since we return early
        verify(scanRepository).findById(eq(scanId));
    }

    // ── Idempotency: skip FAILED ───────────────────────────

    @Test
    void testConsume_skipsWhenAlreadyFailed() {
        UUID scanId = UUID.randomUUID();
        InventoryScan existing = new InventoryScan(InventoryScan.ScanType.PRODUCT, "/path/to/image.jpg");
        existing.setId(scanId);
        existing.setStatus(InventoryScan.ScanStatus.FAILED);

        when(scanRepository.findById(scanId)).thenReturn(Optional.of(existing));

        consumer.consume(new ScanEvent(scanId, InventoryScan.ScanType.PRODUCT, "/path", java.time.LocalDateTime.now()));

        verify(scanRepository).findById(eq(scanId));
    }

    // ── Idempotency: process PENDING (transitions to PROCESSING) ─

    @Test
    void testConsume_processesWhenPending() {
        UUID scanId = UUID.randomUUID();
        InventoryScan existing = new InventoryScan(InventoryScan.ScanType.PRODUCT, "/path/to/image.jpg");
        existing.setId(scanId);
        existing.setStatus(InventoryScan.ScanStatus.PENDING);

        when(scanRepository.findById(scanId)).thenReturn(Optional.of(existing));

        consumer.consume(new ScanEvent(scanId, InventoryScan.ScanType.PRODUCT, "/path", java.time.LocalDateTime.now()));

        // Should call findById at least once (idempotency check + updateStatus)
        verify(scanRepository, atLeastOnce()).findById(any());
    }

    // ── Idempotency: process PROCESSING — already in right state ─

    @Test
    void testConsume_processesWhenAlreadyProcessing() {
        UUID scanId = UUID.randomUUID();
        InventoryScan existing = new InventoryScan(InventoryScan.ScanType.RECEIPT, "/path/to/receipt.jpg");
        existing.setId(scanId);
        existing.setStatus(InventoryScan.ScanStatus.PROCESSING);

        when(scanRepository.findById(scanId)).thenReturn(Optional.of(existing));

        consumer.consume(new ScanEvent(scanId, InventoryScan.ScanType.RECEIPT, "/path", java.time.LocalDateTime.now()));

        verify(scanRepository, atLeastOnce()).findById(any());
    }

    // ── No record found: creates new PROCESSING record ─────

    @Test
    void testConsume_createsNewRecordWhenNotFound() {
        UUID scanId = UUID.randomUUID();
        when(scanRepository.findById(scanId)).thenReturn(Optional.empty());

        consumer.consume(new ScanEvent(scanId, InventoryScan.ScanType.PRODUCT, "/path", java.time.LocalDateTime.now()));

        verify(scanRepository, atLeastOnce()).findById(any());
    }

    // ── Transient error: rethrows for retry ────────────────

    @Test
    void testConsume_rethrowsTransientError() {
        UUID scanId = UUID.randomUUID();
        InventoryScan existing = new InventoryScan(InventoryScan.ScanType.PRODUCT, "/path/to/image.jpg");
        existing.setId(scanId);
        existing.setStatus(InventoryScan.ScanStatus.PENDING);

        when(scanRepository.findById(scanId)).thenReturn(Optional.of(existing));
        doThrow(new RuntimeException("LM Studio timeout")).when(processImageUseCase).executeScan(any(), any());

        assertThatThrownBy(() -> consumer.consume(new ScanEvent(scanId, InventoryScan.ScanType.PRODUCT, "/path", java.time.LocalDateTime.now())))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("timeout");
    }

    // ── Permanent error (IllegalArgumentException): no retry ─

    @Test
    void testConsume_handlesPermanentError() {
        UUID scanId = UUID.randomUUID();
        InventoryScan existing = new InventoryScan(InventoryScan.ScanType.PRODUCT, "/path/to/image.jpg");
        existing.setId(scanId);
        existing.setStatus(InventoryScan.ScanStatus.PENDING);

        when(scanRepository.findById(scanId)).thenReturn(Optional.of(existing));
        doThrow(new IllegalArgumentException("Invalid image format")).when(processImageUseCase).executeScan(any(), any());

        // Should NOT rethrow — permanent errors are caught and handled gracefully
        consumer.consume(new ScanEvent(scanId, InventoryScan.ScanType.PRODUCT, "/path", java.time.LocalDateTime.now()));

        // The consumer should have called save at least once to update status to FAILED
        verify(scanRepository, atLeast(1)).save(argThat(s -> s.getStatus() == InventoryScan.ScanStatus.FAILED));
    }
}

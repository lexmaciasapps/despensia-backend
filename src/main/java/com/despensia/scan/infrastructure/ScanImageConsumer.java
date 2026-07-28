package com.despensia.scan.infrastructure;

import com.despensia.scan.domain.InventoryScan;
import com.despensia.scan.domain.event.ScanEvent;
import com.despensia.scan.repository.ScanRepository;
import com.despensia.scan.service.ProcessImageUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka consumer for scan-events topic.
 *
 * <p>Responsibilities (SRP):</p>
 * <ul>
 *   <li><b>Inbound routing:</b> Receive events, enforce idempotency, delegate to the use case.</li>
 *   <li><b>Status management:</b> Update InventoryScan status after processing completes or fails.</li>
 * </ul>
 */
@Service
public class ScanImageConsumer {

    private static final Logger log = LoggerFactory.getLogger(ScanImageConsumer.class);

    private final ScanRepository scanRepository;
    private final ProcessImageUseCase processImageUseCase;

    public ScanImageConsumer(
            ScanRepository scanRepository,
            ProcessImageUseCase processImageUseCase) {
        this.scanRepository = scanRepository;
        this.processImageUseCase = processImageUseCase;
    }

    @KafkaListener(
            topics = "scan-events",
            groupId = "despensia-scan-consumer",
            containerFactory = "retryContainerFactory"
    )
    public void consume(ScanEvent event) {
        UUID scanId = event.scanId();
        log.info("Received scan event: scanId={}, type={}", scanId, event.scanType());

        // Idempotency check (R6): skip if already completed or failed
        InventoryScan existingScan = scanRepository.findById(scanId).orElse(null);
        if (existingScan != null) {
            if (existingScan.getStatus() == InventoryScan.ScanStatus.COMPLETED) {
                log.info("Skipping duplicate scan (already COMPLETED): {}", scanId);
                return;
            }
            if (existingScan.getStatus() == InventoryScan.ScanStatus.FAILED) {
                log.info("Skipping duplicate scan (already FAILED): {}", scanId);
                return;
            }
        }

        // Ensure a PROCESSING record exists before delegating to the use case
        InventoryScan scan = ensureProcessingRecord(existingScan, event);

        try {
            processImageUseCase.executeScan(event.scanType(), event.imagePath());
            updateStatus(scanId, InventoryScan.ScanStatus.COMPLETED, null);
            log.info("Scan completed successfully: {}", scanId);
        } catch (IllegalArgumentException e) {
            // Permanent error — no retry needed
            log.error("Permanent error processing scan {}: {}", scanId, e.getMessage());
            updateFailedStatus(scanId, "Invalid input: " + e.getMessage(), scan);
        } catch (Exception e) {
            // Transient error — will be retried by DefaultErrorHandler
            log.warn("Transient error processing scan {}: {}", scanId, e.getMessage(), e);
            throw e;
        }
    }

    private void updateFailedStatus(UUID scanId, String errorMessage, InventoryScan scan) {
        if (scan.getId() != null && scan.getStatus() != InventoryScan.ScanStatus.FAILED) {
            updateStatus(scanId, InventoryScan.ScanStatus.FAILED, errorMessage);
        } else {
            // Consumer may have created a transient record — try saving directly
            try {
                InventoryScan existing = scanRepository.findById(scanId).orElse(null);
                if (existing != null && existing.getStatus() == InventoryScan.ScanStatus.PENDING) {
                    updateStatus(scanId, InventoryScan.ScanStatus.FAILED, errorMessage);
                } else {
                    log.warn("Cannot mark scan {} as FAILED — status already changed or not found", scanId);
                }
            } catch (Exception ex) {
                log.error("Error marking scan {} as FAILED: {}", scanId, errorMessage, ex);
            }
        }
    }

    private void updateStatus(UUID scanId, InventoryScan.ScanStatus status, String errorMessage) {
        InventoryScan existing = scanRepository.findById(scanId).orElse(null);
        if (existing != null && existing.getStatus() == status) return; // no-op

        if (existing != null) {
            existing.setStatus(status);
            if (errorMessage != null) {
                existing.setErrorMessage(errorMessage);
            }
            existing.setUpdatedAt(LocalDateTime.now());
            scanRepository.save(existing);
        } else {
            log.error("Cannot update status for scan {}: record not found", scanId);
        }
    }

    private InventoryScan ensureProcessingRecord(InventoryScan existing, ScanEvent event) {
        if (existing != null && existing.getStatus() == InventoryScan.ScanStatus.PENDING) {
            // Transition PENDING → PROCESSING
            existing.setStatus(InventoryScan.ScanStatus.PROCESSING);
            scanRepository.save(existing);
            return existing;
        } else if (existing != null && existing.getStatus() == InventoryScan.ScanStatus.PROCESSING) {
            // Already in the right state — nothing to do
            return existing;
        } else if (existing != null) {
            // Unexpected state — transition to PROCESSING anyway
            log.warn("Unexpected scan status {} for {}; transitioning to PROCESSING", existing.getStatus(), event.scanId());
            existing.setStatus(InventoryScan.ScanStatus.PROCESSING);
            scanRepository.save(existing);
            return existing;
        } else {
            // No record found — create a new one with the correct ID from Kafka event
            log.warn("No scan record found for {}; creating new record", event.scanId());
            InventoryScan newScan = new InventoryScan(event.scanType(), event.imagePath());
            newScan.setId(event.scanId());
            newScan.setStatus(InventoryScan.ScanStatus.PROCESSING);
            scanRepository.save(newScan);
            return newScan;
        }
    }

}

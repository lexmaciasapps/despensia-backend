package com.despensia.scan.service;

import com.despensia.product.domain.ProductItem;
import com.despensia.scan.domain.InventoryScan;
import com.despensia.scan.domain.Receipt;
import com.despensia.scan.domain.event.ScanEvent;
import com.despensia.scan.repository.ScanRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case: submit image scans for async processing and execute synchronous scanning workflows.
 *
 * <p>Responsibilities (SRP):</p>
 * <ul>
 *   <li><b>Async submission:</b> Create scan records, publish events to Kafka, return immediately.</li>
 *   <li><b>Synchronous execution:</b> Orchestrate the actual scanning workflow when called by ScanImageConsumer.
 *       The {@code executeScan()} method is transactional so status + result_data persist atomically;
 *       rollback reverts InventoryScan for Kafka retry redelivery.</li>
 * </ul>
 */
@Service
public class ProcessImageUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessImageUseCase.class);

    private final ReceiptParserPort receiptParserPort;
    private final ProductScanningPort productScanningPort;
    private final ScanEventPublisher scanEventPublisher;
    private final ScanRepository scanRepository;
    private final ObjectMapper objectMapper;

    public ProcessImageUseCase(
            ReceiptParserPort receiptParserPort,
            ProductScanningPort productScanningPort,
            ScanEventPublisher scanEventPublisher,
            ScanRepository scanRepository,
            ObjectMapper objectMapper) {
        this.receiptParserPort = receiptParserPort;
        this.productScanningPort = productScanningPort;
        this.scanEventPublisher = scanEventPublisher;
        this.scanRepository = scanRepository;
        this.objectMapper = objectMapper;
    }

    // ── Public API (async entry point) ────────────────────────────────────────

    /**
     * Submit an image for async scanning. Publishes a ScanEvent to Kafka and returns immediately.
     * The actual processing happens asynchronously via {@link com.despensia.scan.infrastructure.ScanImageConsumer}.
     */
    public Object process(InventoryScan.ScanType scanType, String imagePath) {
        InventoryScan scan = new InventoryScan(scanType, imagePath);

        try {
            saveWithStatus(scan, InventoryScan.ScanStatus.PENDING);
            saveWithStatus(scan, InventoryScan.ScanStatus.PROCESSING);

            ScanEvent event = new ScanEvent(
                    scan.getId(),
                    scan.getScanType(),
                    scan.getImagePath(),
                    java.time.LocalDateTime.now()
            );
            scanEventPublisher.publish(event);

            log.info("Scan submitted for async processing: scanId={}, type={}", scan.getId(), scanType);
            return ScanResult.pending(scan.getId());

        } catch (Exception e) {
            failOrMarkFailed(scan, "Failed to publish event: " + e.getMessage());
            log.error("Scan submission failed for type {}: {}", scanType, e.getMessage());
            return ScanResult.failure("Error al enviar el escaneo: " + e.getMessage());
        }
    }

    // ── Internal synchronous processing (called by consumer) ─────────────────

    /**
     * Execute the scanning workflow synchronously — called by {@link com.despensia.scan.infrastructure.ScanImageConsumer}
     * after a scan event is consumed from Kafka.
     *
     * <p>Transactional: status transition + result_data persistence commit atomically;
     * rollback reverts InventoryScan for Kafka retry redelivery.</p>
     */
    @Transactional
    public ScanResult executeScan(InventoryScan.ScanType scanType, String imagePath) {
        InventoryScan scan = new InventoryScan(scanType, imagePath);
        try {
            return switch (scanType) {
                case RECEIPT -> processReceiptWithStatusTracking(scan);
                case PRODUCT -> processProductWithStatusTracking(scan);
            };
        } catch (Exception e) {
            log.error("Synchronous scan failed: {}", e.getMessage());
            markFailed(scan, "Error procesando imagen: " + e.getMessage());
            return ScanResult.failure("Error procesando imagen: " + e.getMessage());
        }
    }

    // ── Private helpers (DIP — depend on abstractions; SRP — one concern per method) ─

    private void saveWithStatus(InventoryScan scan, InventoryScan.ScanStatus status) {
        scan.setStatus(status);
        scanRepository.save(scan);
    }

    /**
     * Process a receipt image with proper status tracking.
     */
    private ScanResult processReceiptWithStatusTracking(InventoryScan scan) throws Exception {
        saveWithStatus(scan, InventoryScan.ScanStatus.PROCESSING);
        Receipt receipt = receiptParserPort.parse(scan);
        saveWithStatus(scan, InventoryScan.ScanStatus.COMPLETED);

        // Serialize result to JSONB for later retrieval via GET endpoint.
        persistResultData(scan, receipt);

        log.info("Receipt scan completed: store={}, total={}", receipt.getStoreName(), receipt.getTotalAmount());
        return ScanResult.success(receipt);
    }

    /**
     * Process a product image with proper status tracking.
     */
    private ScanResult processProductWithStatusTracking(InventoryScan scan) throws Exception {
        saveWithStatus(scan, InventoryScan.ScanStatus.PROCESSING);
        ProductItem product = productScanningPort.scan(scan);
        saveWithStatus(scan, InventoryScan.ScanStatus.COMPLETED);

        // Serialize result to JSONB for later retrieval via GET endpoint.
        persistResultData(scan, product);

        log.info("Product scan completed: name={}, type={}", product.getName(), product.getProductType());
        return ScanResult.success(product);
    }

    /**
     * Persist the scan result as a raw JSON blob into {@code result_data}.
     */
    private void persistResultData(InventoryScan scan, Object result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            scan.setResultData(json);
            scanRepository.save(scan);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize scan result for scan {}: {}", scan.getId(), e.getMessage());
            // Don't fail the whole transaction — status is already COMPLETED.
            // result_data will remain NULL; client can still poll and see completed status.
        }
    }

    private void markFailed(InventoryScan scan, String errorMessage) {
        saveWithStatus(scan, InventoryScan.ScanStatus.FAILED);
        scan.setErrorMessage(errorMessage);
        scanRepository.save(scan);
    }

    /**
     * Attempt to mark an existing saved scan as FAILED; fall back to creating a new failed record if needed.
     */
    private void failOrMarkFailed(InventoryScan scan, String errorMessage) {
        try {
            InventoryScan existing = (scan.getId() != null)
                    ? scanRepository.findById(scan.getId()).orElse(null)
                    : null;

            if (existing != null && existing.getStatus() != InventoryScan.ScanStatus.FAILED) {
                existing.setStatus(InventoryScan.ScanStatus.FAILED);
                existing.setErrorMessage(errorMessage);
                scanRepository.save(existing);
            } else if (scan.getId() == null) {
                // Never persisted — create a failed record directly
                scan.setStatus(InventoryScan.ScanStatus.FAILED);
                scan.setErrorMessage(errorMessage);
                scanRepository.save(scan);
            }
        } catch (Exception ex) {
            log.error("Error marking scan as FAILED: {}", errorMessage, ex);
        }
    }

}

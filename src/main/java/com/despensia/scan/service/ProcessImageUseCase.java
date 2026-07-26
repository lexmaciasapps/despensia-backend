package com.despensia.scan.service;

import com.despensia.product.domain.ProductItem;
import com.despensia.scan.domain.InventoryScan;
import com.despensia.scan.domain.Receipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Use case: process an image for product or receipt scanning.
 *
 * Orchestrates the scanning workflow:
 * 1. Determine scan type from the input
 * 2. Delegate to the appropriate port (ReceiptParser or ProductScanner)
 * 3. Save the scan record and results
 * 4. Return the result to the caller
 */
@Service
public class ProcessImageUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessImageUseCase.class);

    private final ReceiptParserPort receiptParserPort;
    private final ProductScanningPort productScanningPort;

    public ProcessImageUseCase(ReceiptParserPort receiptParserPort, ProductScanningPort productScanningPort) {
        this.receiptParserPort = receiptParserPort;
        this.productScanningPort = productScanningPort;
    }

    /**
     * Process an image for either PRODUCT or RECEIPT scanning.
     *
     * @param scanType the type of scan to perform
     * @param imagePath path to the image file
     * @return a typed ScanResult containing the data or error
     */
    public ScanResult process(InventoryScan.ScanType scanType, String imagePath) {
        InventoryScan scan = new InventoryScan(scanType, imagePath);

        try {
            return switch (scanType) {
                case RECEIPT -> processReceipt(scan);
                case PRODUCT -> processProduct(scan);
            };
        } catch (Exception e) {
            scan.setStatus(InventoryScan.ScanStatus.FAILED);
            scan.setErrorMessage(e.getMessage());
            log.error("Scan failed: {}", e.getMessage());
            return ScanResult.failure("Error procesando imagen: " + e.getMessage());
        }
    }

    private ScanResult processReceipt(InventoryScan scan) {
        log.info("Processing RECEIPT scan for image: {}", scan.getImagePath());
        scan.setStatus(InventoryScan.ScanStatus.PROCESSING);

        Receipt receipt = receiptParserPort.parse(scan);

        scan.setStatus(InventoryScan.ScanStatus.COMPLETED);
        log.info("Receipt scan completed: store={}, total={}", receipt.getStoreName(), receipt.getTotalAmount());
        return ScanResult.success(receipt);
    }

    private ScanResult processProduct(InventoryScan scan) {
        log.info("Processing PRODUCT scan for image: {}", scan.getImagePath());
        scan.setStatus(InventoryScan.ScanStatus.PROCESSING);

        ProductItem product = productScanningPort.scan(scan);

        scan.setStatus(InventoryScan.ScanStatus.COMPLETED);
        log.info("Product scan completed: name={}, type={}", product.getName(), product.getProductType());
        return ScanResult.success(product);
    }
}

package com.despensia.scan.infrastructure;

import com.despensia.product.domain.ProductItem;
import com.despensia.scan.domain.InventoryScan;
import com.despensia.scan.service.ProductScanningPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of ProductScanningPort.
 *
 * In production, this will connect to an LLM/Vision API (e.g., LmStudio)
 * to analyze product photos and extract product information.
 *
 * Currently logs the input and returns a dummy product for development.
 */
@Component
public class LmStudioProductScanner implements ProductScanningPort {

    private static final Logger log = LoggerFactory.getLogger(LmStudioProductScanner.class);

    @Override
    public ProductItem scan(InventoryScan scan) {
        log.warn("LmStudioProductScanner is a STUB — returning dummy data for scan: {}", scan.getId());
        log.info("Product scanning input: imagePath={}, scanType={}", scan.getImagePath(), scan.getScanType());

        // TODO: Replace with actual LLM/Vision API call
        // Example: POST /api/v1/chat/completions with vision model
        // Parse JSON response → map to ProductItem entity

        return new ProductItem("Leche Entera 1L", ProductItem.ProductType.PACKAGED);
    }
}

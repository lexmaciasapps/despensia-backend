package com.despensia.scan.infrastructure;

import com.despensia.scan.domain.InventoryScan;
import com.despensia.scan.domain.Receipt;
import com.despensia.scan.service.ReceiptParserPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Stub implementation of ReceiptParserPort.
 *
 * In production, this will connect to an LLM/Vision API (e.g., LmStudio)
 * to perform OCR and extract receipt data.
 *
 * Currently logs the input and returns a dummy receipt for development.
 */
@Component
public class LmStudioReceiptParser implements ReceiptParserPort {

    private static final Logger log = LoggerFactory.getLogger(LmStudioReceiptParser.class);

    @Override
    public Receipt parse(InventoryScan scan) {
        log.warn("LmStudioReceiptParser is a STUB — returning dummy data for scan: {}", scan.getId());
        log.info("Receipt parsing input: imagePath={}, scanType={}", scan.getImagePath(), scan.getScanType());

        // TODO: Replace with actual LLM/Vision API call
        // Example: POST /api/v1/chat/completions with vision model
        // Parse JSON response → map to Receipt entity

        return new Receipt(
                LocalDate.now(),
                "Tienda de Convenio Ejemplo",
                new BigDecimal("245.50")
        );
    }
}

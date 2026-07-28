package com.despensia.scan.service;

import com.despensia.scan.domain.InventoryScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service that orchestrates image scan submissions.
 */
@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final ProcessImageUseCase processImageUseCase;

    /**
     * Constructor injection for {@link ProcessImageUseCase}.
     */
    public ScanService(ProcessImageUseCase processImageUseCase) {
        this.processImageUseCase = processImageUseCase;
    }

    /**
     * Submit an image for async scanning. Returns a typed scan submission result.
     */
    public ScanSubmissionResult submitScan(InventoryScan.ScanType scanType, String imagePath) {
        log.info("Submitting {} scan for image: {}", scanType, imagePath);

        Object result = processImageUseCase.process(scanType, imagePath);

        if (result instanceof ScanResult sr && Boolean.TRUE.equals(sr.success())) {
            // For pending results, extract the scanId and return as a response DTO
            log.info("Scan submitted successfully with ID: {}", sr.data());
            String scanId = sr.data() != null ? sr.data().toString() : "unknown";
            return ScanSubmissionResult.pending(scanId);
        }

        if (result instanceof ScanResult sr && Boolean.FALSE.equals(sr.success())) {
            throw new RuntimeException("Scan submission failed: " + sr.errorMessage());
        }

        // Fallback — should not happen in normal flow
        log.warn("Unexpected result type from submitScan: {}", 
                result != null ? result.getClass().getName() : "null");
        return ScanSubmissionResult.pending(null);
    }
}

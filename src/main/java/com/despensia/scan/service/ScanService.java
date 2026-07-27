package com.despensia.scan.service;

import com.despensia.scan.domain.InventoryScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final ProcessImageUseCase processImageUseCase;

    public ScanService(ProcessImageUseCase processImageUseCase) {
        this.processImageUseCase = processImageUseCase;
    }

    /**
     * Submit an image for async scanning. Returns a response map with scanId and status.
     */
    public Object submitScan(InventoryScan.ScanType scanType, String imagePath) {
        log.info("Submitting {} scan for image: {}", scanType, imagePath);

        Object result = processImageUseCase.process(scanType, imagePath);

        if (result instanceof ScanResult sr && Boolean.TRUE.equals(sr.success())) {
            // For pending results, extract the scanId and return as a response DTO
            log.info("Scan submitted successfully with ID: {}", sr.data());
            return java.util.Map.of(
                    "scanId", sr.data(),
                    "status", "PROCESSING"
            );
        }

        if (result instanceof ScanResult sr && Boolean.FALSE.equals(sr.success())) {
            throw new RuntimeException("Scan submission failed: " + sr.errorMessage());
        }

        return result;
    }
}

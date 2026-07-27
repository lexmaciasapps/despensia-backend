package com.despensia.scan.api;

import com.despensia.scan.domain.InventoryScan;
import com.despensia.scan.service.ScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/scan")
public class ScanController {

    private static final Logger log = LoggerFactory.getLogger(ScanController.class);

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "module", "scan");
    }

    @PostMapping("/products")
    public ResponseEntity<?> submitProductScan(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "scanType", defaultValue = "PRODUCT") InventoryScan.ScanType scanType) {

        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Image file is required"));
        }

        try {
            // Save the uploaded file temporarily and get its path
            String imagePath = saveUploadedFile(image);

            Object result = scanService.submitScan(scanType, imagePath);

            if (result instanceof Map<?, ?> response) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            }

            log.warn("Unexpected result type from submitScan: {}", result.getClass().getName());
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("status", "PROCESSING", "message", "Scan accepted"));

        } catch (RuntimeException e) {
            log.error("Error processing scan request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process scan: " + e.getMessage()));
        }
    }

    private String saveUploadedFile(MultipartFile file) throws RuntimeException {
        try {
            java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"));
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            java.io.File tempFile = new File(tempDir, fileName);
            file.transferTo(tempFile);
            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save uploaded image: " + e.getMessage(), e);
        }
    }
}

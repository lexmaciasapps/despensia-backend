package com.despensia.scan.api;

import com.despensia.scan.api.dto.ScanStatusResponse;
import com.despensia.scan.domain.InventoryScan;
import com.despensia.scan.repository.ScanRepository;
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
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/scan")
public class ScanController {

    private static final Logger log = LoggerFactory.getLogger(ScanController.class);

    private final ScanService scanService;
    private final ScanRepository scanRepository;

    public ScanController(ScanService scanService, ScanRepository scanRepository) {
        this.scanService = scanService;
        this.scanRepository = scanRepository;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "module", "scan");
    }

    /**
     * Retrieve the current status of a scan by its ID.
     *
     * <p>Returns 200 for any found scan (including PENDING/PROCESSING) so clients can poll;
     * returns 404 when no scan exists for the given UUID.</p>
     */
    @GetMapping("/products/{scanId}")
    public ResponseEntity<ScanStatusResponse> getScanStatus(@PathVariable("scanId") UUID scanId) {
        return scanRepository.findById(scanId)
                .map(scan -> {
                    if (InventoryScan.ScanStatus.COMPLETED.equals(scan.getStatus()) && scan.getResultData() != null) {
                        return ResponseEntity.ok(ScanStatusResponse.withResult(
                                scan.getId(), scan.getStatus().name(), scan.getResultData()));
                    } else if (InventoryScan.ScanStatus.FAILED.equals(scan.getStatus())) {
                        return ResponseEntity.ok(ScanStatusResponse.failed(
                                scan.getId(), scan.getErrorMessage() != null ? scan.getErrorMessage() : "Unknown error"));
                    } else {
                        // PENDING, PROCESSING — no result yet
                        return ResponseEntity.ok(ScanStatusResponse.of(scan.getId(), scan.getStatus().name()));
                    }
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ScanStatusResponse(scanId, null, "Scan not found", null)));
    }

    @PostMapping("/products")
    public ResponseEntity<?> submitProductScan(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "scanType", defaultValue = "PRODUCT") InventoryScan.ScanType scanType) {

        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Image file is required"));
        }

        String originalFilename = image.getOriginalFilename();
        // Validate extension
        if (originalFilename != null && !isAllowedExtension(originalFilename)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Unsupported file type. Allowed: jpg, jpeg, png"));
        }

        // Validate MIME type
        String contentType = image.getContentType();
        if (!isValidImageMimeType(contentType)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid image content type. Expected: image/jpeg or image/png"));
        }

        // Validate size (10 MB max)
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (image.getSize() > maxSize) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Image too large. Maximum size: 10 MB"));
        }

        try {
            String imagePath = saveUploadedFile(image);

            com.despensia.scan.service.ScanSubmissionResult result = scanService.submitScan(scanType, imagePath);

            if (result.isPending()) {
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(Map.of("scanId", result.scanId(), "status", result.status()));
            }

            log.warn("Unexpected result from submitScan: status={} scanId={}", result.status(), result.scanId());
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("status", "PROCESSING"));

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
            // Sanitize filename to prevent path traversal attacks: extract basename only, replace all non-alphanumeric chars with _
            String originalName = file.getOriginalFilename() != null ? sanitizeFileName(file.getOriginalFilename()) : "image";
            String fileName = System.currentTimeMillis() + "_" + originalName;
            java.io.File tempFile = new File(tempDir, fileName);
            file.transferTo(tempFile);
            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save uploaded image: " + e.getMessage(), e);
        }
    }

    /**
     * Sanitize a filename by stripping all path components and replacing unsafe characters.
     */
    private String sanitizeFileName(String originalName) {
        // Extract basename only (handles both / and \ path separators)
        int lastSlash = Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\'));
        String safeName = lastSlash >= 0 ? originalName.substring(lastSlash + 1) : originalName;

        // Replace all non-alphanumeric characters with underscore to prevent injection attacks
        return safeName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private boolean isAllowedExtension(String filename) {
        if (filename == null || !filename.contains(".")) return false;
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return Set.of("jpg", "jpeg", "png").contains(ext);
    }

    private boolean isValidImageMimeType(String contentType) {
        if (contentType == null) return false;
        return contentType.equals("image/jpeg") || contentType.equals("image/png");
    }
}

package com.despensia.scan.api.dto;

import java.util.UUID;

/**
 * Response DTO for the GET /api/scan/products/{scanId} endpoint.
 *
 * <p>Carries scan status and optional result data without coupling to domain types.
 * The Flutter client deserializes {@code resultData} based on scanType context.</p>
 */
public record ScanStatusResponse(
        UUID scanId,
        String status,
        String errorMessage,
        String resultData) {

    public static ScanStatusResponse of(UUID scanId, String status) {
        return new ScanStatusResponse(scanId, status, null, null);
    }

    public static ScanStatusResponse withResult(UUID scanId, String status, String resultData) {
        return new ScanStatusResponse(scanId, status, null, resultData);
    }

    public static ScanStatusResponse failed(UUID scanId, String errorMessage) {
        return new ScanStatusResponse(scanId, "FAILED", errorMessage, null);
    }
}

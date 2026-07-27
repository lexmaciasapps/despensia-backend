package com.despensia.scan.service;

/**
 * Resultado tipado del escaneo (Clean Code: evita Object genérico).
 */
public record ScanResult(
    boolean success,
    Object data, // Puede ser Receipt o ProductItem
    String errorMessage
) {
    public static ScanResult success(Object data) {
        return new ScanResult(true, data, null);
    }

    public static ScanResult failure(String message) {
        return new ScanResult(false, null, message);
    }

    /**
     * Pending scan result — the operation was accepted and is being processed.
     * errorMessage is intentionally null to preserve semantic clarity:
     *   - success=true + data!=null = pending (scan submitted successfully)
     *   - success=false + errorMessage!=null = failed
     */
    /**
     * Create a pending result — the scan was submitted successfully and is being processed asynchronously.
     * Callers distinguish pending by checking success=true + data != null (not errorMessage).
     */
    public static ScanResult pending(Object scanId) {
        return new ScanResult(true, scanId, null);
    }
}

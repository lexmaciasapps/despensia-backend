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

    public static ScanResult pending(Object scanId) {
        return new ScanResult(true, scanId, "PENDING");
    }
}

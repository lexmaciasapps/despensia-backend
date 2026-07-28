package com.despensia.scan.service;

/**
 * Result of submitting an image scan for processing.
 */
public record ScanSubmissionResult(String status, String scanId) {

    public static ScanSubmissionResult pending(String scanId) {
        return new ScanSubmissionResult("PROCESSING", scanId);
    }

    public boolean isPending() {
        return "PROCESSING".equals(status);
    }
}

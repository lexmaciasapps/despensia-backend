package com.despensia.scan.service;

import java.util.UUID;

/**
 * Result of submitting an image scan for processing.
 */
public record ScanSubmissionResult(String status, String scanId) {

    /**
     * Create a pending result — the scan was submitted successfully and is being processed asynchronously.
     */
    public static ScanSubmissionResult pending(String scanId) {
        return new ScanSubmissionResult("PROCESSING", scanId);
    }

    /**
     * Returns true if this represents a pending (processing) state.
     */
    public boolean isPending() {
        return "PROCESSING".equals(status);
    }
}

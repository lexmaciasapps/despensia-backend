package com.despensia.scan.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class InventoryScanTest {

    @Test
    void testNewScanStartsWithPendingStatus() {
        InventoryScan scan = new InventoryScan(InventoryScan.ScanType.PRODUCT, "/path/to/image.jpg");

        assertThat(scan.getStatus()).isEqualTo(InventoryScan.ScanStatus.PENDING);
        assertThat(scan.getScanType()).isEqualTo(InventoryScan.ScanType.PRODUCT);
    }

    @Test
    void testTransitionPendingToProcessing() {
        InventoryScan scan = new InventoryScan(InventoryScan.ScanType.RECEIPT, "/path/to/receipt.jpg");
        scan.setStatus(InventoryScan.ScanStatus.PROCESSING);

        assertThat(scan.getStatus()).isEqualTo(InventoryScan.ScanStatus.PROCESSING);
    }

    @Test
    void testTransitionProcessingToCompleted() {
        InventoryScan scan = new InventoryScan(InventoryScan.ScanType.PRODUCT, "/path/to/image.jpg");
        scan.setStatus(InventoryScan.ScanStatus.PROCESSING);
        scan.setStatus(InventoryScan.ScanStatus.COMPLETED);

        assertThat(scan.getStatus()).isEqualTo(InventoryScan.ScanStatus.COMPLETED);
    }

    @Test
    void testTransitionProcessingToFailed() {
        InventoryScan scan = new InventoryScan(InventoryScan.ScanType.RECEIPT, "/path/to/receipt.jpg");
        scan.setStatus(InventoryScan.ScanStatus.PROCESSING);
        scan.setStatus(InventoryScan.ScanStatus.FAILED);

        assertThat(scan.getStatus()).isEqualTo(InventoryScan.ScanStatus.FAILED);
    }

    @Test
    void testIdempotency_skipAlreadyCompleted() {
        InventoryScan scan = new InventoryScan(InventoryScan.ScanType.PRODUCT, "/path/to/image.jpg");
        scan.setStatus(InventoryScan.ScanStatus.COMPLETED);

        boolean shouldProcess = switch (scan.getStatus()) {
            case COMPLETED, FAILED -> false;
            default -> true;
        };

        assertThat(shouldProcess).isFalse();
    }

    @Test
    void testIdempotency_skipAlreadyFailed() {
        InventoryScan scan = new InventoryScan(InventoryScan.ScanType.RECEIPT, "/path/to/receipt.jpg");
        scan.setStatus(InventoryScan.ScanStatus.FAILED);

        boolean shouldProcess = switch (scan.getStatus()) {
            case COMPLETED, FAILED -> false;
            default -> true;
        };

        assertThat(shouldProcess).isFalse();
    }

    @Test
    void testIdempotency_processPending() {
        InventoryScan scan = new InventoryScan(InventoryScan.ScanType.PRODUCT, "/path/to/image.jpg");

        boolean shouldProcess = switch (scan.getStatus()) {
            case COMPLETED, FAILED -> false;
            default -> true;
        };

        assertThat(shouldProcess).isTrue();
    }

    @Test
    void testIdempotency_processProcessing() {
        InventoryScan scan = new InventoryScan(InventoryScan.ScanType.RECEIPT, "/path/to/receipt.jpg");
        scan.setStatus(InventoryScan.ScanStatus.PROCESSING);

        boolean shouldProcess = switch (scan.getStatus()) {
            case COMPLETED, FAILED -> false;
            default -> true;
        };

        assertThat(shouldProcess).isTrue();
    }
}

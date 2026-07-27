package com.despensia.scan.domain.event;

import com.despensia.scan.domain.InventoryScan;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScanEvent(
    UUID scanId,
    InventoryScan.ScanType scanType,
    String imagePath,
    LocalDateTime timestamp
) {}

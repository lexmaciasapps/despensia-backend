package com.despensia.scan.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScanResultEvent(
    UUID scanId,
    String resultType,
    String data,
    String status,
    String errorMessage
) {}

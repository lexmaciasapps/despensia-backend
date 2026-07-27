package com.despensia.scan.service;

import com.despensia.scan.domain.event.ScanEvent;

public interface ScanEventPublisher {
    void publish(ScanEvent event);
}

package com.despensia.scan.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/scan")
public class ScanController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "module", "scan");
    }
}

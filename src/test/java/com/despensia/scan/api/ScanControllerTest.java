package com.despensia.scan.api;

import com.despensia.scan.domain.InventoryScan;
import com.despensia.scan.repository.ScanRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link ScanController#getScanStatus(UUID)}.
 * Uses real Spring context + MockMvc with PostgreSQL (via Testcontainers).
 */
@AutoConfigureMockMvc
class ScanControllerTest extends com.despensia.DespensiaBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScanRepository scanRepository;

    // S1: Successful PRODUCT scan query → 200 with resultData JSON string
    @Test
    void getScanStatus_completedWithResult_returns200WithData() throws Exception {

        InventoryScan scan = new InventoryScan(InventoryScan.ScanType.PRODUCT, "/tmp/product.jpg");
        scan.setStatus(InventoryScan.ScanStatus.COMPLETED);
        scan.setResultData("{\"name\": \"Leche Entera\", \"productType\": \"PACKAGED\"}");

    scanRepository.save(scan);

        mockMvc.perform(get("/api/scan/products/" + scan.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanId").value(scan.getId().toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.resultData").isNotEmpty())
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    // S3: PENDING scan polling → 200 with status=PENDING, null resultData
    @Test
    void getScanStatus_pending_returns200WithNullResult() throws Exception {
        InventoryScan scan = new InventoryScan(InventoryScan.ScanType.RECEIPT, "/tmp/receipt.jpg");
        // Status defaults to PENDING via constructor

    scanRepository.save(scan);

        mockMvc.perform(get("/api/scan/products/" + scan.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.resultData").doesNotExist());
    }

    // S4: PROCESSING scan polling → 200 with status=PROCESSING, null resultData
    @Test
    void getScanStatus_processing_returns200WithNullResult() throws Exception {
        InventoryScan scan = new InventoryScan(InventoryScan.ScanType.PRODUCT, "/tmp/product.jpg");
        scan.setStatus(InventoryScan.ScanStatus.PROCESSING);

    scanRepository.save(scan);

        mockMvc.perform(get("/api/scan/products/" + scan.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.resultData").doesNotExist());
    }

    // S5: FAILED scan query → 200 with errorMessage populated
    @Test
    void getScanStatus_failed_returns200WithErrorMessage() throws Exception {
        InventoryScan scan = new InventoryScan(InventoryScan.ScanType.RECEIPT, "/tmp/receipt.jpg");
        scan.setStatus(InventoryScan.ScanStatus.FAILED);
        scan.setErrorMessage("Error procesando imagen: timeout");

    scanRepository.save(scan);

        mockMvc.perform(get("/api/scan/products/" + scan.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("Error procesando imagen: timeout"))
                .andExpect(jsonPath("$.resultData").doesNotExist());
    }

    // S6: Non-existent scan ID → 404 (doesn't need DB — just verifies controller behavior)
    @Test
    void getScanStatus_notFound_returns404() throws Exception {
        UUID nonexistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/scan/products/" + nonexistentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorMessage").value("Scan not found"));
    }

}

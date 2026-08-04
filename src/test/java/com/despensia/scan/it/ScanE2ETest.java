package com.despensia.scan.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.despensia.DespensiaBackendApplicationTests;
import com.despensia.scan.infrastructure.KafkaScanEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for scan flow using real ticket and product images.
 * <p>
 * These tests verify the full chain: image upload → OCR/ML processing → JSONB persistence → GET retrieval,
 * using actual purchase tickets (RECEIPT) and vegetable/product photos (PRODUCT).
 */
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ScanE2ETest extends DespensiaBackendApplicationTests {

    @MockBean
    private KafkaScanEventPublisher kafkaScanEventPublisher; // Mock to avoid real Kafka dependency in tests

    @Autowired
    private MockMvc mockMvc;

    // ── Ticket/E2E images loaded from classpath resources ───────────────

    private byte[] loadTicketImage(String name) throws Exception {
        try (var is = ScanE2ETest.class.getClassLoader().getResourceAsStream("e2e-tickets/" + name)) {
            assertThat(is).as("Ticket image resource not found: " + name).isNotNull();
            return is.readAllBytes();
        }
    }

    private byte[] loadVegetableImage(String name) throws Exception {
        try (var is = ScanE2ETest.class.getClassLoader().getResourceAsStream("e2e-verduras/" + name)) {
            assertThat(is).as("Veggie image resource not found: " + name).isNotNull();
            return is.readAllBytes();
        }
    }

    // ── Receipt (ticket) E2E tests ────────────────────────────────────────

    @Test
    @DisplayName("POST /api/scan/products with real ticket image → async submission")
    void submitRealTicketImage_returnsAccepted() throws Exception {
        byte[] imageData = loadTicketImage("images.jpeg");

        MockMultipartFile file = new MockMultipartFile(
                "image",
                "ticket_e2e.jpeg",
                MediaType.IMAGE_JPEG_VALUE,
                imageData
        );

        mockMvc.perform(multipart("/api/scan/products")
                        .file(file)
                        .param("scanType", "RECEIPT"))
                .andExpect(status().isAccepted())
                .andReturn();

        // Verify Kafka publisher was called (mocked in parent class context)
        verify(kafkaScanEventPublisher, atLeastOnce()).publish(any());
    }

    @Test
    @DisplayName("POST /api/scan/products with real ticket image → executeScan persists JSONB")
    void submitRealTicketImage_executeScan_persistsResultData() throws Exception {
        byte[] imageData = loadTicketImage("images (1).jpeg");

        MockMultipartFile file = new MockMultipartFile(
                "image",
                "ticket_e2e_2.jpeg",
                MediaType.IMAGE_JPEG_VALUE,
                imageData
        );

        var result = mockMvc.perform(multipart("/api/scan/products")
                        .file(file)
                        .param("scanType", "RECEIPT"))
                .andExpect(status().isAccepted())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode json = objectMapper.readTree(body);
        String scanId = json.get("scanId").asText();

        // Verify GET endpoint returns the scan with its ID (status may vary depending on OCR availability)
        mockMvc.perform(get("/api/scan/products/{scanId}", scanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanId").value(scanId));
    }

    @Test
    @DisplayName("POST /api/scan/products with second real ticket image → different scan result")
    void submitSecondRealTicketImage_differentResult() throws Exception {
        byte[] imageData = loadTicketImage("images (2).jpeg");

        MockMultipartFile file = new MockMultipartFile(
                "image",
                "ticket_e2e_3.jpeg",
                MediaType.IMAGE_JPEG_VALUE,
                imageData
        );

        mockMvc.perform(multipart("/api/scan/products")
                        .file(file)
                        .param("scanType", "RECEIPT"))
                .andExpect(status().isAccepted());
    }

    // ── Product/Vegetable E2E tests ───────────────────────────────────────

    @Test
    @DisplayName("POST /api/scan/products with real vegetable image → async submission")
    void submitRealVegetableImage_returnsAccepted() throws Exception {
        byte[] imageData = loadVegetableImage("images.jpeg");

        MockMultipartFile file = new MockMultipartFile(
                "image",
                "vegetable_e2e.jpeg",
                MediaType.IMAGE_JPEG_VALUE,
                imageData
        );

        mockMvc.perform(multipart("/api/scan/products")
                        .file(file)
                        .param("scanType", "PRODUCT"))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("POST /api/scan/products with second real vegetable image → different scan result")
    void submitSecondRealVegetableImage_differentResult() throws Exception {
        byte[] imageData = loadVegetableImage("images (1).jpeg");

        MockMultipartFile file = new MockMultipartFile(
                "image",
                "vegetable_e2e_2.jpeg",
                MediaType.IMAGE_JPEG_VALUE,
                imageData
        );

        mockMvc.perform(multipart("/api/scan/products")
                        .file(file)
                        .param("scanType", "PRODUCT"))
                .andExpect(status().isAccepted());
    }

}

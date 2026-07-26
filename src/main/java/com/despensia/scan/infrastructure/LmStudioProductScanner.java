package com.despensia.scan.infrastructure;

import com.despensia.scan.service.ProductScanningPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.List;

/**
 * Implementation of ProductScanningPort using LM Studio (OpenAI-compatible API).
 * Uses Virtual Threads for non-blocking I/O.
 */
@Component
public class LmStudioProductScanner implements ProductScanningPort {

    private static final Logger log = LoggerFactory.getLogger(LmStudioProductScanner.class);

    private final WebClient webClient;
    private final String lmStudioUrl;

    public LmStudioProductScanner(
            WebClient.Builder webClientBuilder,
            @Value("${lm-studio.url:http://localhost:1234}") String lmStudioUrl) {
        this.webClient = webClientBuilder.build();
        this.lmStudioUrl = lmStudioUrl;
    }

    @Override
    public com.despensia.product.domain.ProductItem scan(com.despensia.scan.domain.InventoryScan scan) {
        log.info("Scanning product via LM Studio for image: {}", scan.getImagePath());

        try {
            String base64Image = encodeImageToBase64(scan.getImagePath());
            
            // Prompt for product detection
            String prompt = """
                Analyze this image and identify the product.
                Return a JSON object with the following fields:
                - name: String (product name)
                - type: String (PACKAGED or ORGANIC)
                - estimatedDaysRemaining: Integer (estimated days until expiration)
                
                Example:
                {
                  "name": "Leche Entera",
                  "type": "PACKAGED",
                  "estimatedDaysRemaining": 30
                }
                """;

            var response = webClient.post()
                    .uri(lmStudioUrl + "/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "model", "llama-3.2-vision", // Or your preferred model
                            "messages", List.of(
                                    Map.of("role", "user", "content", List.of(
                                            Map.of("type", "text", "text", prompt),
                                            Map.of("type", "image_url", "image_url", Map.of("url", "data:image/jpeg;base64," + base64Image))
                                    ))
                            ),
                            "max_tokens", 300
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(); // Blocking in this context is acceptable as we are in a use-case orchestrator

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    log.info("LM Studio response: {}", content);
                    return parseProductResponse(content);
                }
            }
        } catch (Exception e) {
            log.error("Error scanning product via LM Studio: {}", e.getMessage());
            throw new RuntimeException("Failed to scan product", e);
        }

        throw new RuntimeException("No product detected or error occurred");
    }

    private String encodeImageToBase64(String imagePath) throws IOException {
        Path path = Path.of(imagePath);
        if (!Files.exists(path)) {
            throw new IOException("Image file not found: " + imagePath);
        }
        byte[] bytes = Files.readAllBytes(path);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private com.despensia.product.domain.ProductItem parseProductResponse(String content) {
        // Simple JSON parsing (in production, use Jackson/Gson)
        // For now, return a dummy product as the stub was doing, but with logging
        log.info("Parsing product response: {}", content);
        // TODO: Implement JSON parsing logic
        return new com.despensia.product.domain.ProductItem("Producto Detectado", com.despensia.product.domain.ProductItem.ProductType.PACKAGED);
    }
}

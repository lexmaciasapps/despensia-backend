package com.despensia.scan.infrastructure;

import com.despensia.product.domain.ProductItem;
import com.despensia.scan.service.ProductScanningPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;

/**
 * Implementation of ProductScanningPort using LM Studio (OpenAI-compatible API).
 */
@SuppressWarnings("unchecked")
@Component
public class LmStudioProductScanner implements ProductScanningPort {

    private static final Logger log = LoggerFactory.getLogger(LmStudioProductScanner.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final WebClient webClient;
    private final String lmStudioUrl;

    public LmStudioProductScanner(
            WebClient.Builder webClientBuilder,
            @Value("${lm-studio.url:http://localhost:1234}") String lmStudioUrl) {
        this.webClient = webClientBuilder.build();
        this.lmStudioUrl = lmStudioUrl;
    }

    @Override
    public ProductItem scan(com.despensia.scan.domain.InventoryScan scan) {
        log.info("Scanning product via LM Studio for image: {}", scan.getImagePath());

        try {
            String base64Image = encodeImageToBase64(scan.getImagePath());

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
                            "model", "llama-3.2-vision",
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
                    .block();

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

    /**
     * Parse the JSON response from LM Studio into a ProductItem.
     * Validates required fields and applies sensible defaults for missing ones.
     */
    ProductItem parseProductResponse(String content) {
        String json = extractJson(content);

        if (json == null || !json.contains("\"name\"") && !json.contains("'name'")) {
            log.error("LM Studio response does not contain a 'name' field: {}", content);
            throw new IllegalArgumentException("Invalid product detection result from AI: missing required field 'name'");
        }

        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (Exception e) {
            log.error("Failed to parse LM Studio JSON response for product: {}", content, e);
            throw new IllegalArgumentException("Invalid product detection result from AI: malformed JSON", e);
        }

        String name = node.path("name").asText("");
        if (name.isBlank()) {
            log.warn("LM Studio returned empty 'name', using default");
            name = "Producto Desconocido";
        }

        String typeStr = node.path("type").asText("").trim();
        com.despensia.product.domain.ProductItem.ProductType productType;
        if (typeStr.isEmpty()) {
            log.warn("LM Studio response missing 'type' field, defaulting to PACKAGED");
            productType = ProductItem.ProductType.PACKAGED;
        } else {
            try {
                productType = ProductItem.ProductType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown product type '{}', defaulting to PACKAGED", typeStr);
                productType = ProductItem.ProductType.PACKAGED;
            }
        }

        int daysRemaining = node.path("estimatedDaysRemaining").isInt()
                ? node.path("estimatedDaysRemaining").intValue() : 30;

        var product = new ProductItem(name, productType);
        if (daysRemaining > 0) {
            product.updateEstimatedDays(daysRemaining);
        }
        return product;
    }

    private String extractJson(String content) {
        if (!content.contains("{")) {
            return content;
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}') + 1;
        if (start >= 0 && end > start) {
            return content.substring(start, end);
        }
        return content;
    }
}

package com.despensia.scan.infrastructure;

import com.despensia.product.domain.ProductItem;
import com.despensia.scan.service.ProductScanningPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;

/**
 * Implementation of ProductScanningPort using Spring AI (OpenAI-compatible API via LM Studio).
 */
@SuppressWarnings("unchecked")
@Component
public class LmStudioProductScanner implements ProductScanningPort {

    private static final Logger log = LoggerFactory.getLogger(LmStudioProductScanner.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ChatClient chatClient;

    public LmStudioProductScanner(ChatClient.Builder chatClientBuilder) {
        // Spring AI auto-configures OpenAI-compatible client from application.yml:
        // spring.ai.openai.base-url -> http://localhost:1234/v1/ (LM Studio endpoint)
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public ProductItem scan(com.despensia.scan.domain.InventoryScan scan) {
        log.info("Scanning product via Spring AI for image: {}", scan.getImagePath());

        try {
            byte[] imageData = Files.readAllBytes(java.nio.file.Path.of(scan.getImagePath()));

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

            String response = chatClient.prompt()
                    .system(s -> s.text("You are a product identification assistant. Always respond with valid JSON."))
                    .user(u -> u.text(prompt)
                            .media(org.springframework.util.MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(imageData)))
                    .call()
                    .content();

            log.info("Spring AI response: {}", response);
            return parseProductResponse(response);
        } catch (IOException e) {
            log.error("Error reading image file for Spring AI scan: {}", e.getMessage());
            throw new RuntimeException("Failed to read image", e);
        } catch (Exception e) {
            log.error("Error scanning product via Spring AI: {}", e.getMessage());
            throw new RuntimeException("Failed to scan product with Spring AI", e);
        }
    }

    /**
     * Parse the JSON response from LM Studio into a ProductItem.
     */
    ProductItem parseProductResponse(String content) {
        String json = extractJson(content);

        if (json == null || !json.contains("\"name\"") && !json.contains("'name'")) {
            log.error("Spring AI response does not contain a 'name' field: {}", content);
            throw new IllegalArgumentException("Invalid product detection result from AI: missing required field 'name'");
        }

        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (Exception e) {
            log.error("Failed to parse Spring AI JSON response for product: {}", content, e);
            throw new IllegalArgumentException("Invalid product detection result from AI: malformed JSON", e);
        }

        String name = node.path("name").asText("");
        if (name.isBlank()) {
            log.warn("Spring AI returned empty 'name', using default");
            name = "Producto Desconocido";
        }

        String typeStr = node.path("type").asText("").trim();
        ProductItem.ProductType productType;
        if (typeStr.isEmpty()) {
            log.warn("Spring AI response missing 'type' field, defaulting to PACKAGED");
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

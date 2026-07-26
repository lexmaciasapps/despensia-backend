package com.despensia.scan.infrastructure;

import com.despensia.scan.domain.InventoryScan;
import com.despensia.scan.service.ReceiptParserPort;
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
 * Implementation of ReceiptParserPort using LM Studio (OpenAI-compatible API).
 * Uses Virtual Threads for non-blocking I/O.
 */
@Component
public class LmStudioReceiptParser implements ReceiptParserPort {

    private static final Logger log = LoggerFactory.getLogger(LmStudioReceiptParser.class);

    private final WebClient webClient;
    private final String lmStudioUrl;

    public LmStudioReceiptParser(
            WebClient.Builder webClientBuilder,
            @Value("${lm-studio.url:http://localhost:1234}") String lmStudioUrl) {
        this.webClient = webClientBuilder.build();
        this.lmStudioUrl = lmStudioUrl;
    }

    @Override
    public com.despensia.scan.domain.Receipt parse(InventoryScan scan) {
        log.info("Parsing receipt via LM Studio for image: {}", scan.getImagePath());

        try {
            String base64Image = encodeImageToBase64(scan.getImagePath());
            
            // Prompt for receipt parsing
            String prompt = """
                Analyze this receipt image and extract the purchase data.
                Return a JSON object with the following fields:
                - storeName: String (name of the store)
                - date: String (date of purchase)
                - totalAmount: Double (total amount)
                - items: List of objects with:
                  - name: String (product name)
                  - quantity: Double (quantity)
                  - price: Double (price per item)
                
                Example:
                {
                  "storeName": "Walmart",
                  "date": "2023-10-27",
                  "totalAmount": 150.50,
                  "items": [
                    {"name": "Leche", "quantity": 2, "price": 25.00},
                    {"name": "Pan", "quantity": 1, "price": 35.50}
                  ]
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
                            "max_tokens", 500
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
                    return parseReceiptResponse(content);
                }
            }
        } catch (Exception e) {
            log.error("Error parsing receipt via LM Studio: {}", e.getMessage());
            throw new RuntimeException("Failed to parse receipt", e);
        }

        throw new RuntimeException("No receipt detected or error occurred");
    }

    private String encodeImageToBase64(String imagePath) throws IOException {
        Path path = Path.of(imagePath);
        if (!Files.exists(path)) {
            throw new IOException("Image file not found: " + imagePath);
        }
        byte[] bytes = Files.readAllBytes(path);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private com.despensia.scan.domain.Receipt parseReceiptResponse(String content) {
        log.info("Parsing receipt response: {}", content);
        // TODO: Implement JSON parsing logic
        return new com.despensia.scan.domain.Receipt(java.time.LocalDate.now(), "Tienda Ejemplo", java.math.BigDecimal.valueOf(100.0));
    }
}

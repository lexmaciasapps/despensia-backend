package com.despensia.scan.infrastructure;

import com.despensia.product.domain.ProductItem;
import com.despensia.scan.domain.InventoryScan;
import com.despensia.scan.domain.Receipt;
import com.despensia.scan.service.ReceiptParserPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

/**
 * Implementation of ReceiptParserPort using Spring AI (OpenAI-compatible API via LM Studio).
 */
@SuppressWarnings("unchecked")
@Component
public class LmStudioReceiptParser implements ReceiptParserPort {

    private static final Logger log = LoggerFactory.getLogger(LmStudioReceiptParser.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ChatClient chatClient;

    /**
     * Constructor injection for the Spring AI {@link ChatClient.Builder}.
     */
    /**
     * Constructor injection for the Spring AI {@link ChatClient.Builder}.
     */
    public LmStudioReceiptParser(ChatClient.Builder chatClientBuilder) {
        // Spring AI auto-configures OpenAI-compatible client from application.yml:
        // spring.ai.openai.base-url -> http://localhost:1234/v1/ (LM Studio endpoint)
        this.chatClient = chatClientBuilder != null ? chatClientBuilder.build() : null;
    }

    /**
     * Package-private constructor for testing — skips ChatClient initialization.
     */
    LmStudioReceiptParser() {
        // No-op: used only by unit tests that call parseReceiptResponse directly (no AI needed).
        this.chatClient = null;
    }


    @Override
    public Receipt parse(InventoryScan scan) {
        log.info("Parsing receipt via Spring AI for image: {}", scan.getImagePath());

        try {
            byte[] imageData = Files.readAllBytes(java.nio.file.Path.of(scan.getImagePath()));

            String prompt = """
                Analyze this receipt image and extract the purchase data.
                Return a JSON object with the following fields:
                - storeName: String (name of the store)
                - date: String (date of purchase, YYYY-MM-DD format)
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

            String response = chatClient.prompt()
                    .system(s -> s.text("You are a receipt parsing assistant. Always respond with valid JSON."))
                    .user(u -> u.text(prompt)
                            .media(org.springframework.util.MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(imageData)))
                    .call()
                    .content();

            log.info("Spring AI response: {}", response);
            return parseReceiptResponse(response);
        } catch (IOException e) {
            log.error("Error reading image file for Spring AI receipt parsing: {}", e.getMessage());
            throw new RuntimeException("Failed to read image", e);
        } catch (Exception e) {
            log.error("Error parsing receipt via Spring AI: {}", e.getMessage());
            throw new RuntimeException("Failed to parse receipt with Spring AI", e);
        }
    }

    /**
     * Parse the JSON response from LM Studio into a Receipt with line items.
     */
    @SuppressWarnings("unchecked")
    Receipt parseReceiptResponse(String content) {
        String json = extractJson(content);

        if (json == null || !json.contains("\"storeName\"")) {
            log.error("Spring AI response does not contain a 'storeName' field: {}", content);
            throw new IllegalArgumentException("Invalid receipt parsing result from AI: missing required field 'storeName'");
        }

        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (Exception e) {
            log.error("Failed to parse Spring AI JSON response for receipt: {}", content, e);
            throw new IllegalArgumentException("Invalid receipt parsing result from AI: malformed JSON", e);
        }

        String storeName = node.path("storeName").asText("").trim();
        if (storeName.isBlank()) {
            log.warn("Spring AI returned empty 'storeName', using default");
            storeName = "Tienda Desconocida";
        }

        String dateStr = node.path("date").asText(LocalDate.now().toString()).trim();
        double totalAmount = 0.0;
        if (node.has("totalAmount") && !node.get("totalAmount").isNull()) {
            JsonNode ta = node.get("totalAmount");
            if (ta.isDouble() || ta.isLong() || ta.isInt()) {
                totalAmount = ta.asDouble();
            } else if (ta.isTextual()) {
                try {
                    totalAmount = Double.parseDouble(ta.asText());
                } catch (NumberFormatException e) {
                    log.warn("Invalid totalAmount '{}', using 0.0", ta.asText());
                }
            }
        }

        LocalDate parsedDate;
        try {
            parsedDate = LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.warn("Invalid date '{}' in receipt response, using today", dateStr);
            parsedDate = LocalDate.now();
        }

        Receipt receipt = new Receipt(parsedDate, storeName, BigDecimal.valueOf(totalAmount));

        JsonNode itemsNode = node.path("items");
        if (itemsNode.isArray()) {
            Iterator<JsonNode> it = itemsNode.iterator();
            while (it.hasNext()) {
                JsonNode itemNode = it.next();
                String itemName = itemNode.path("name").asText("");
                double quantityVal = 1.0;
                if (itemNode.has("quantity") && !itemNode.get("quantity").isNull()) {
                    JsonNode qn = itemNode.get("quantity");
                    quantityVal = qn.isDouble() || qn.isLong() ? qn.asDouble() : qn.asInt();
                }
                double priceVal = 0.0;
                if (itemNode.has("price") && !itemNode.get("price").isNull()) {
                    JsonNode pn = itemNode.get("price");
                    if (pn.isDouble() || pn.isLong() || pn.isInt()) {
                        priceVal = pn.asDouble();
                    } else if (pn.isTextual()) {
                        try {
                            priceVal = Double.parseDouble(pn.asText());
                        } catch (NumberFormatException e) {
                            log.warn("Invalid item price '{}', using 0.0", pn.asText());
                        }
                    }
                }

                int qty = (int) Math.round(quantityVal);
                var lineItem = new com.despensia.scan.domain.ReceiptLineItem(null, itemName, qty, BigDecimal.valueOf(priceVal));
                receipt.addLineItem(lineItem); // attach to receipt so JPA cascade persists it
            }
        }

        return receipt;
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

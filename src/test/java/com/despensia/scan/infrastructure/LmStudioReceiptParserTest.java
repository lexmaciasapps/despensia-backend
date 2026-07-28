package com.despensia.scan.infrastructure;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for LmStudioReceiptParser.parseReceiptResponse — verifies that parsed line items 
 * are attached to the receipt (fixing the previous dead-code bug where they were created but not saved).
 */
class LmStudioReceiptParserTest {

    // parseReceiptResponse is package-private and needs a ChatClient instance.
    // We use reflection + a minimal mock to test the parsing logic in isolation.
    private final Method parseMethod;

    {
        try {
            parseMethod = com.despensia.scan.infrastructure.LmStudioReceiptParser.class.getDeclaredMethod("parseReceiptResponse", String.class);
            parseMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private Object invokeParse(String jsonContent) throws Exception {
        var parser = new LmStudioReceiptParser(); // package-private no-arg constructor for tests
        
        return parseMethod.invoke(parser, jsonContent);
    }

    @Test
    void parse_validResponse_attachesLineItemsToReceipt() throws Exception {
        String jsonContent = """
            Here is the parsed receipt data:
            
            ```json
            {
              "storeName": "Walmart",
              "date": "2024-10-27",
              "totalAmount": 150.50,
              "items": [
                {"name": "Leche Entera", "quantity": 2, "price": 25.00},
                {"name": "Pan de Caja", "quantity": 1, "price": 35.50},
                {"name": "Huevos (docena)", "quantity": 3, "price": 40.00}
              ]
            }
            ```
            
            Let me know if you need anything else!
            """;

        com.despensia.scan.domain.Receipt receipt = 
            (com.despensia.scan.domain.Receipt) invokeParse(jsonContent);

        // Verify core fields parsed correctly
        assertThat(receipt.getStoreName()).isEqualTo("Walmart");
        assertThat(receipt.getTotalAmount()).isEqualByComparingTo(new BigDecimal("150.50"));

        // CRITICAL: line items MUST be attached to the receipt (not orphaned)
        List<com.despensia.scan.domain.ReceiptLineItem> items = receipt.getLineItems();
        assertThat(items).isNotEmpty()
            .hasSize(3);

        assertThat(items.get(0).getName()).isEqualTo("Leche Entera");
        assertThat(items.get(0).getQuantity()).isEqualTo(2);
        assertThat(items.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("25.00"));

        assertThat(items.get(1).getName()).isEqualTo("Pan de Caja");
        assertThat(items.get(1).getQuantity()).isEqualTo(1);
        assertThat(items.get(1).getPrice()).isEqualByComparingTo(new BigDecimal("35.50"));

        assertThat(items.get(2).getName()).isEqualTo("Huevos (docena)");
        assertThat(items.get(2).getQuantity()).isEqualTo(3);
    }

    @Test
    void parse_emptyItemsArray_returnsReceiptWithNoLineItems() throws Exception {
        String jsonContent = """
            ```json
            {
              "storeName": "Oxxo",
              "date": "2024-11-01",
              "totalAmount": 50.00,
              "items": []
            }
            ```
            """;

        com.despensia.scan.domain.Receipt receipt = 
            (com.despensia.scan.domain.Receipt) invokeParse(jsonContent);

        assertThat(receipt.getStoreName()).isEqualTo("Oxxo");
        assertThat(receipt.getLineItems()).isEmpty();
    }

    @Test
    void parse_missingTotalAmount_defaultsToZero() throws Exception {
        String jsonContent = """
            ```json
            {
              "storeName": "Soriana",
              "date": "2024-11-15",
              "items": [{"name": "Arroz", "quantity": 1, "price": 38.00}]
            }
            ```
            """;

        com.despensia.scan.domain.Receipt receipt = 
            (com.despensia.scan.domain.Receipt) invokeParse(jsonContent);

        assertThat(receipt.getTotalAmount()).isEqualByComparingTo(new BigDecimal("0"));
    }

    @Test
    void parse_invalidDate_usesToday() throws Exception {
        String jsonContent = """
            ```json
            {
              "storeName": "Chedraui",
              "date": "not-a-date",
              "totalAmount": 100.00,
              "items": []
            }
            ```
            """;

        com.despensia.scan.domain.Receipt receipt = 
            (com.despensia.scan.domain.Receipt) invokeParse(jsonContent);

        assertThat(receipt.getDate()).isEqualTo(java.time.LocalDate.now());
    }

    @Test
    void parse_noJsonMarkers_returnsParsedData() throws Exception {
        // AI response without markdown code blocks — raw JSON
        String jsonContent = """
            {"storeName": "Mercado Libre", "date": "2024-12-01", "totalAmount": 250.75, "items": [{"name": "Aceite", "quantity": 2, "price": 45.50}]}
            """;

        com.despensia.scan.domain.Receipt receipt = 
            (com.despensia.scan.domain.Receipt) invokeParse(jsonContent);

        assertThat(receipt.getStoreName()).isEqualTo("Mercado Libre");
        assertThat(receipt.getTotalAmount()).isEqualByComparingTo(new BigDecimal("250.75"));
    }
}

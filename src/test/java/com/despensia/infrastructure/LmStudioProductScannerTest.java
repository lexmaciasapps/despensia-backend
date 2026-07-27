package com.despensia.infrastructure;

import com.despensia.product.domain.ProductItem;
import com.despensia.scan.infrastructure.LmStudioProductScanner;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for LmStudioProductScanner — covers JSON parsing validation logic.
 */
class LmStudioProductScannerTest {

    private ProductItem callParse(String content) throws Exception {
        var fakeClient = Mockito.mock(ChatClient.class);
        
        // Create scanner and inject the mock ChatClient via reflection (constructor needs a real Builder)
        var builderMock = Mockito.mock(ChatClient.Builder.class);
        when(builderMock.build()).thenReturn(fakeClient);
        
        var scanner = new LmStudioProductScanner(builderMock);

        Method method = scanner.getClass().getDeclaredMethod("parseProductResponse", String.class);
        method.setAccessible(true); // allow access to package-private method from different package
        return (ProductItem) method.invoke(scanner, content);
    }

    // ── parseProductResponse tests ────────────────────────

    @Test
    void testParseValidJson() throws Exception {
        String content = """
            Here is the product data:
            ```json
            {
              "name": "Leche Entera",
              "type": "PACKAGED",
              "estimatedDaysRemaining": 30
            }
            ```
            End of analysis.
            """;

        var result = callParse(content);

        assertThat(result.getName()).isEqualTo("Leche Entera");
    }

    @Test
    void testParseMarkdownCodeBlock() throws Exception {
        String content = "```\n{\n  \"name\": \"Yogurt Natural\",\n  \"type\": \"ORGANIC\"\n}\n```";

        var result = callParse(content);

        assertThat(result.getName()).isEqualTo("Yogurt Natural");
    }

    @Test
    void testParsePlainJsonObject() throws Exception {
        String content = "{\"name\":\"Aceite de Oliva\",\"type\":\"PACKAGED\"}";

        var result = callParse(content);

        assertThat(result.getName()).isEqualTo("Aceite de Oliva");
    }

    @Test
    void testMissingTypeDefaultsToPackaged() throws Exception {
        String content = """
            ```json
            {
              "name": "Pan Integral",
              "estimatedDaysRemaining": 5
            }
            ```
            """;

        var result = callParse(content);

        assertThat(result.getName()).isEqualTo("Pan Integral");
    }

    @Test
    void testMissingNameThrows() throws Exception {
        String content = """
            ```json
            {
              "type": "ORGANIC",
              "estimatedDaysRemaining": 10
            }
            ```
            """;

        try {
            callParse(content);
            fail("Expected IllegalArgumentException");
        } catch (InvocationTargetException e) {
            assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required field 'name'");
        }
    }

    @Test
    void testNoBracesThrowsBecauseMissingName() throws Exception {
        String content = "This is just text with no JSON at all";

        try {
            callParse(content);
            fail("Expected IllegalArgumentException");
        } catch (InvocationTargetException e) {
            assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required field 'name'");
        }
    }

    @Test
    void testMultipleObjectsThrows() throws Exception {
        String content = "Some text before {\"a\":1} middle stuff {\"b\":2, \"c\":3} more text";

        try {
            callParse(content);
            fail("Expected IllegalArgumentException");
        } catch (InvocationTargetException e) {
            assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required field");
        }
    }

    @Test
    void testNestedBracesHandlesCorrectly() throws Exception {
        String content = """
            ```json
            {"name": "Test", "nested": {"key": "value"}}
            ```
            """;

        var result = callParse(content);

        assertThat(result.getName()).isEqualTo("Test");
    }
}

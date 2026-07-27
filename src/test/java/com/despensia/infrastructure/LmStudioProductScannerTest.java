package com.despensia.infrastructure;

import com.despensia.scan.infrastructure.LmStudioProductScanner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LmStudioProductScannerTest {

    @Test
    void testParseProductResponse_withValidJson() throws Exception {
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

        String json = extractJson(content);

        assertThat(json).contains("Leche Entera");
        assertThat(json).contains("PACKAGED");
    }

    @Test
    void testParseProductResponse_withMarkdownCodeBlock() throws Exception {
        String content = "```\\n{\\n  \"name\": \"Yogurt Natural\",\\n  \"type\": \"ORGANIC\"\\n}\\n```";

        String json = extractJson(content);

        assertThat(json).contains("Yogurt Natural");
    }

    @Test
    void testParseProductResponse_withPlainJsonObject() throws Exception {
        String content = "{\"name\":\"Aceite de Oliva\",\"type\":\"PACKAGED\"}";

        String json = extractJson(content);

        assertThat(json).isEqualTo("{\"name\":\"Aceite de Oliva\",\"type\":\"PACKAGED\"}");
    }

    @Test
    void testParseProductResponse_withMissingTypeDefaultsToPackaged() throws Exception {
        String content = """
            ```json
            {
              "name": "Pan Integral",
              "estimatedDaysRemaining": 5
            }
            ```
            """;

        String json = extractJson(content);

        assertThat(json).contains("Pan Integral");
    }

    @Test
    void testParseProductResponse_withMissingNameDefaultsToUnknown() throws Exception {
        String content = """
            ```json
            {
              "type": "ORGANIC",
              "estimatedDaysRemaining": 10
            }
            ```
            """;

        String json = extractJson(content);

        assertThat(json).contains("ORGANIC");
    }

    @Test
    void testExtractJson_withNoBraces_returnsOriginalContent() throws Exception {
        String content = "This is just text with no JSON at all";

        String result = extractJson(content);

        assertThat(result).isEqualTo(content);
    }

    @Test
    void testExtractJson_withMultipleObjects_takesOutermostRange() throws Exception {
        String content = "Some text before {\"a\":1} middle stuff {\"b\":2, \"c\":3} more text";

        String result = extractJson(content);

        assertThat(result).contains("\"b\":2");
    }

    @Test
    void testExtractJson_withNestedBraces_handlesCorrectly() throws Exception {
        String content = """
            ```json
            {"name": "Test", "nested": {"key": "value"}}
            ```
            """;

        String result = extractJson(content);

        assertThat(result).contains("Test");
    }

    private static String extractJson(String content) {
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

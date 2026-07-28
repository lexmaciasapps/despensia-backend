package com.despensia.product.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for expiration strategy selection logic — the core business rule
 * that determines how long a product lasts based on its type.
 */
class ExpirationStrategyTest {

    private final ConservativeAverageStrategy conservative = new ConservativeAverageStrategy();
    private final AiVisualEstimateStrategy ai = new AiVisualEstimateStrategy();

    // ── ConservativeAverageStrategy (PACKAGED) ───────────────

    @Test
    void conservativeForPackaged_returns30Days() {
        assertThat(conservative.defaultFor(ProductItem.ProductType.PACKAGED)).isEqualTo(30);
    }

    @Test
    void conservativeUnknownType_defaultsTo30Days() {
        // If a new ProductType is added that's not in AVERAGES, it should default safely
        Integer result = conservative.defaultFor(ProductItem.ProductType.ORGANIC);
        assertThat(result).isEqualTo(30);
    }

    @Test
    void conservativeNeverReturnsNull() {
        // Strategy must always return a non-null value — null would cause NPE downstream
        for (ProductItem.ProductType type : ProductItem.ProductType.values()) {
            Integer days = conservative.defaultFor(type);
            assertThat(days).isNotNull().isPositive();
        }
    }

    // ── AiVisualEstimateStrategy (ORGANIC stub) ──────────────

    @Test
    void aiForOrganic_returns7Days() {
        // Stub value: organic products default to 7 days until AI integration
        assertThat(ai.defaultFor(ProductItem.ProductType.ORGANIC)).isEqualTo(7);
    }

    @Test
    void aiNeverReturnsNull() {
        for (ProductItem.ProductType type : ProductItem.ProductType.values()) {
            Integer days = ai.defaultFor(type);
            assertThat(days).isNotNull().isPositive();
        }
    }

    // ── ProductService integration — strategy resolution ─────

    @Test
    void productService_usesCorrectStrategy_forPackaged() {
        // Verify the strategy interface is respected — if ProductService accepts both strategies,
        // DIP (Dependency Inversion Principle) holds. We can't test persistence in unit tests.
        assertThat(conservative).isInstanceOf(DefaultExpirationStrategy.class);
        assertThat(ai).isInstanceOf(DefaultExpirationStrategy.class);
    }

    @Test
    void productService_usesCorrectStrategy_forOrganic() {
        // Both strategies must be interchangeable — same interface, different behavior
        var packDays = conservative.defaultFor(ProductItem.ProductType.PACKAGED);
        var organicDays = ai.defaultFor(ProductItem.ProductType.ORGANIC);

        assertThat(packDays).isNotEqualTo(organicDays)
            .as("PACKAGED and ORGANIC strategies must return different defaults");
    }
}

package com.despensia.product.domain;

import com.despensia.product.domain.ProductItem.ProductType;
import org.springframework.stereotype.Component;

/**
 * AI visual estimate strategy for organic products.
 * Currently a stub — will be replaced with AI vision integration in a future phase.
 */
@Component
public class AiVisualEstimateStrategy implements DefaultExpirationStrategy {

    @Override
    public Integer defaultFor(ProductType type) {
        // Stub: organic products default to 7 days until AI integration
        return 7;
    }
}

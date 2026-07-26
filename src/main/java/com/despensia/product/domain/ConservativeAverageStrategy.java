package com.despensia.product.domain;

import com.despensia.product.domain.ProductItem.ProductType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Conservative average strategy for packaged products.
 * Uses pre-defined average shelf-life values per packaged product category.
 */
@Component
public class ConservativeAverageStrategy implements DefaultExpirationStrategy {

    private static final Map<ProductType, Integer> AVERAGES = Map.of(
            ProductType.PACKAGED, 30
    );

    @Override
    public Integer defaultFor(ProductType type) {
        return AVERAGES.getOrDefault(type, 30);
    }
}

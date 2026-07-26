package com.despensia.product.service;

import com.despensia.product.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ConservativeAverageStrategy conservativeStrategy;
    private final AiVisualEstimateStrategy aiStrategy;

    public ProductItem create(String name, ProductItem.ProductType productType) {
        ProductItem product = new ProductItem(name, productType);
        DefaultExpirationStrategy strategy = resolveStrategy(productType);
        product.applyDefaultStrategy(strategy);
        // TODO: persist via repository
        return product;
    }

    private DefaultExpirationStrategy resolveStrategy(ProductItem.ProductType type) {
        return switch (type) {
            case PACKAGED -> conservativeStrategy;
            case ORGANIC -> aiStrategy;
        };
    }
}

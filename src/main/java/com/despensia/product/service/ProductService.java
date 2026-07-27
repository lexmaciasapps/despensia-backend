package com.despensia.product.service;

import com.despensia.product.domain.AiVisualEstimateStrategy;
import com.despensia.product.domain.ConservativeAverageStrategy;
import com.despensia.product.domain.DefaultExpirationStrategy;
import com.despensia.product.domain.ProductItem;
import com.despensia.product.repository.ProductItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductItemRepository productItemRepository;
    private final ConservativeAverageStrategy conservativeStrategy;
    private final AiVisualEstimateStrategy aiStrategy;

    public ProductItem create(String name, ProductItem.ProductType productType) {
        ProductItem product = new ProductItem(name, productType);
        DefaultExpirationStrategy strategy = resolveStrategy(productType);
        product.applyDefaultStrategy(strategy);
        return productItemRepository.save(product);
    }

    public ProductItem findBy(UUID id) {
        return productItemRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found: " + id));
    }

    public Page<ProductItem> list(Pageable pageable) {
        return productItemRepository.findAll(pageable);
    }

    private DefaultExpirationStrategy resolveStrategy(ProductItem.ProductType type) {
        return switch (type) {
            case PACKAGED -> conservativeStrategy;
            case ORGANIC -> aiStrategy;
        };
    }
}

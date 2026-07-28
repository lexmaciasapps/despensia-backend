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

/**
 * Service for managing {@link ProductItem} CRUD operations with automatic expiration strategies.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductItemRepository productItemRepository;
    private final ConservativeAverageStrategy conservativeStrategy;
    private final AiVisualEstimateStrategy aiStrategy;

    /**
     * Create a new product entry with an appropriate default expiration strategy based on its type.
     */
    public ProductItem create(String name, ProductItem.ProductType productType) {
        ProductItem product = new ProductItem(name, productType);
        DefaultExpirationStrategy strategy = resolveStrategy(productType);
        product.applyDefaultStrategy(strategy);
        return productItemRepository.save(product);
    }

    /**
     * Find a product by its ID. Throws {@link IllegalArgumentException} if not found.
     */
    public ProductItem findBy(UUID id) {
        return productItemRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
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

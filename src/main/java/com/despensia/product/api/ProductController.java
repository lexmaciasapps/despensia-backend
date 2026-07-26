package com.despensia.product.api;

import com.despensia.product.domain.ProductItem;
import com.despensia.product.domain.ProductItem.ProductType;
import com.despensia.product.domain.DefaultExpirationStrategy;
import com.despensia.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam String name,
            @RequestParam ProductType productType) {
        ProductItem product = productService.create(name, productType);
        return ResponseEntity.ok(Map.of(
                "id", product.getId().toString(),
                "name", product.getName(),
                "productType", product.getProductType(),
                "estimatedDaysRemaining", product.getEstimatedDaysRemaining(),
                "expirationSource", product.getExpirationSource()
        ));
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "module", "product");
    }
}

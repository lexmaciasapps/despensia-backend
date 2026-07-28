package com.despensia.product.api;

import com.despensia.product.domain.ProductItem;
import com.despensia.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management operations")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @Operation(summary = "Create a new product entry", description = "Creates a ProductItem with automatic expiration strategy based on type (PACKAGED -> conservative average, ORGANIC -> AI estimate).")
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam String name,
            @RequestParam ProductItem.ProductType productType) {
        ProductItem product = productService.create(name, productType);
        return ResponseEntity.ok(toMap(product));
    }

    @GetMapping("/health")
    @Operation(summary = "Product module health check", description = "Returns the status of the product module.")
    public Map<String, String> health() {
        return Map.of("status", "ok", "module", "product");
    }

    @GetMapping
    public Page<ProductItem> list(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return productService.list(pageable);
    }

    /**
     * Get a product by its ID. Validates UUID format and returns 404 if not found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
        // Validate UUID format first — before calling the service layer
        java.util.UUID uuid;
        try {
            uuid = java.util.UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid UUID format"));
        }

        ProductItem product;
        try {
            product = productService.findBy(uuid);
        } catch (IllegalArgumentException e) {
            // Service layer uses IllegalArgumentException for not-found — map to 404
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(toMap(product));
    }

    /**
     * Convert a {@link ProductItem} to a Map representation for JSON serialization.
     */
    private Map<String, Object> toMap(ProductItem product) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", product.getId().toString());
        map.put("name", product.getName() != null ? product.getName() : "");
        map.put("productType", product.getProductType());
        map.put("estimatedDaysRemaining", product.getEstimatedDaysRemaining());
        if (product.getExpirationDate() != null) {
            map.put("expirationDate", product.getExpirationDate().toString());
        } else {
            map.put("expirationDate", null);
        }
        if (product.getExpirationSource() != null) {
            map.put("expirationSource", product.getExpirationSource().name());
        } else {
            map.put("expirationSource", null);
        }
        map.put("isExpired", product.getIsExpired() != null ? product.getIsExpired() : false);
        if (product.getCreatedAt() != null) {
            map.put("createdAt", product.getCreatedAt().toString());
        } else {
            map.put("createdAt", null);
        }
        return map;
    }
}

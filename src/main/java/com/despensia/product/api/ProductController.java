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

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
        try {
            ProductItem product = productService.findBy(java.util.UUID.fromString(id));
            return ResponseEntity.ok(toMap(product));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid UUID format"));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private Map<String, Object> toMap(ProductItem product) {
        return Map.of(
                "id", product.getId().toString(),
                "name", product.getName(),
                "productType", product.getProductType(),
                "estimatedDaysRemaining", product.getEstimatedDaysRemaining(),
                "expirationDate", product.getExpirationDate() != null ? product.getExpirationDate().toString() : null,
                "expirationSource", product.getExpirationSource() != null ? product.getExpirationSource().name() : null,
                "isExpired", product.getIsExpired() != null ? product.getIsExpired() : false,
                "createdAt", product.getCreatedAt() != null ? product.getCreatedAt().toString() : null
        );
    }
}

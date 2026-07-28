package com.despensia.inventory.api;

import com.despensia.inventory.domain.InventoryItem;
import com.despensia.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management operations")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/items")
    @Operation(summary = "Add an item to inventory", description = "Creates a new inventory entry linked to a product.")
    public ResponseEntity<Map<String, Object>> addItem(@RequestBody AddItemRequest request) {
        try {
            if (request.name() == null || request.name().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
            }
            InventoryItem item = inventoryService.add(
                request.productId(),
                request.name(),
                request.quantity(),
                request.unit()
            );
            return ResponseEntity.status(201).body(toMap(item));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/items")
    @Operation(summary = "List all inventory items", description = "Returns paginated list of inventory items.")
    public Page<InventoryItem> listItems(@PageableDefault(size = 20) Pageable pageable) {
        return inventoryService.list(pageable);
    }

    @GetMapping("/items/{id}")
    @Operation(summary = "Get an item by ID", description = "Returns a single inventory item or 404 if not found.")
    public ResponseEntity<Map<String, Object>> getItemById(@PathVariable UUID id) {
        return inventoryService.findById(id)
            .map(item -> ResponseEntity.ok(toMap(item)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/items/{id}/quantity")
    @Operation(summary = "Update item quantity", description = "Updates the quantity of an existing inventory item.")
    public ResponseEntity<Map<String, Object>> updateQuantity(
            @PathVariable UUID id,
            @RequestBody UpdateQuantityRequest request) {
        try {
            InventoryItem item = inventoryService.updateQuantity(id, request.quantity());
            return ResponseEntity.ok(toMap(item));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record AddItemRequest(String productId, String name, Integer quantity, String unit) {}
    public record UpdateQuantityRequest(Integer quantity) {}

    /**
     * Convert an {@link InventoryItem} to a Map representation for JSON serialization.
     */
    private Map<String, Object> toMap(InventoryItem item) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", item.getId().toString());
        map.put("productId", item.getProductId() != null ? item.getProductId() : "");
        map.put("name", item.getName() != null ? item.getName() : "");
        map.put("quantity", item.getQuantity());
        map.put("unit", item.getUnit() != null ? item.getUnit() : "");
        map.put("location", item.getLocation());
        if (item.getCreatedAt() != null) {
            map.put("createdAt", item.getCreatedAt().toString());
        } else {
            map.put("createdAt", null);
        }
        if (item.getUpdatedAt() != null) {
            map.put("updatedAt", item.getUpdatedAt().toString());
        } else {
            map.put("updatedAt", null);
        }
        return map;
    }
}

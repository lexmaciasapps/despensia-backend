package com.despensia.inventory.service;

import com.despensia.inventory.domain.InventoryItem;
import com.despensia.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryItem add(String productId, String name, Integer quantity, String unit) {
        InventoryItem item = new InventoryItem(productId, name, quantity, unit);
        return inventoryRepository.save(item);
    }

    public Optional<InventoryItem> findById(UUID id) {
        return inventoryRepository.findById(id);
    }

    public Page<InventoryItem> list(Pageable pageable) {
        return inventoryRepository.findAll(pageable);
    }

    public List<InventoryItem> findByProductId(String productId) {
        return inventoryRepository.findByProductId(productId);
    }

    public InventoryItem updateQuantity(UUID id, Integer newQuantity) {
        InventoryItem item = inventoryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Inventory item not found: " + id));
        item.setQuantity(newQuantity);
        return inventoryRepository.save(item);
    }
}

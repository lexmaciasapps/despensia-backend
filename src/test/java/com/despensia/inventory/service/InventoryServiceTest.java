package com.despensia.inventory.service;

import com.despensia.inventory.domain.InventoryItem;
import com.despensia.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    // ── add() ────────────────────────────────────────────────

    @Test
    void testAdd_createsAndReturnsPersistedItem() {
        String productId = "prod-123";
        String name = "Leche Entera";
        int quantity = 5;
        String unit = "LITERS";

        InventoryItem saved = new InventoryItem(productId, name, quantity, unit);
        when(inventoryRepository.save(any(InventoryItem.class))).thenReturn(saved);

        InventoryItem result = inventoryService.add(productId, name, quantity, unit);

        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo(productId);
        assertThat(result.getName()).isEqualTo(name);
        assertThat(result.getQuantity()).isEqualTo(quantity);
        verify(inventoryRepository).save(any(InventoryItem.class));
    }

    // ── findById() ───────────────────────────────────────────

    @Test
    void testFindById_returnsUserWhenFound() {
        UUID id = UUID.randomUUID();
        InventoryItem item = new InventoryItem("prod-1", "Pan", 3, "UNITS");
        when(inventoryRepository.findById(id)).thenReturn(Optional.of(item));

        Optional<InventoryItem> result = inventoryService.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Pan");
    }

    @Test
    void testFindById_returnsEmptyWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(inventoryRepository.findById(id)).thenReturn(Optional.empty());

        Optional<InventoryItem> result = inventoryService.findById(id);

        assertThat(result).isEmpty();
    }

    // ── list() with pagination ───────────────────────────────

    @Test
    void testList_returnsPaginatedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        InventoryItem item = new InventoryItem("prod-1", "Aceite", 2, "LITERS");
        Page<InventoryItem> page = new PageImpl<>(List.of(item));
        when(inventoryRepository.findAll(pageable)).thenReturn(page);

        Page<InventoryItem> result = inventoryService.list(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Aceite");
    }

    // ── findByProductId() ────────────────────────────────────

    @Test
    void testFindByProductId_returnsItemsForGivenProductId() {
        String productId = "prod-456";
        InventoryItem item1 = new InventoryItem(productId, "Leche", 2, "LITERS");
        InventoryItem item2 = new InventoryItem(productId, "Yogurt", 3, "UNITS");
        when(inventoryRepository.findByProductId(productId)).thenReturn(List.of(item1, item2));

        List<InventoryItem> result = inventoryService.findByProductId(productId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProductId()).isEqualTo(productId);
    }

    @Test
    void testFindByProductId_returnsEmptyListWhenNoItems() {
        String productId = "nonexistent";
        when(inventoryRepository.findByProductId(productId)).thenReturn(List.of());

        List<InventoryItem> result = inventoryService.findByProductId(productId);

        assertThat(result).isEmpty();
    }

    // ── updateQuantity() ─────────────────────────────────────

    @Test
    void testUpdateQuantity_updatesAndReturnsUpdatedItem() {
        UUID id = UUID.randomUUID();
        InventoryItem existing = new InventoryItem("prod-1", "Leche", 2, "LITERS");
        when(inventoryRepository.findById(id)).thenReturn(Optional.of(existing));
        when(inventoryRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryItem result = inventoryService.updateQuantity(id, 5);

        assertThat(result).isNotNull();
        assertThat(result.getQuantity()).isEqualTo(5);
    }

    @Test
    void testUpdateQuantity_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(inventoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.updateQuantity(id, 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Inventory item not found");
    }
}

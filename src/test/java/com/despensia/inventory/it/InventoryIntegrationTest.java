package com.despensia.inventory.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for inventory CRUD via REST API.
 */
@AutoConfigureMockMvc
class InventoryIntegrationTest extends com.despensia.DespensiaBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    // ── Add item ────────────────────────────────────────

    @Test
    void addItem_valid_returns201() throws Exception {
        var result = mockMvc.perform(post("/api/inventory/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId":"00000000-0000-0000-0000-000000000001","name":"Manzana Roja","quantity":5,"unit":"unidades"}
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        // Verify response contains inventory item fields
        var mapper = new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder()
                .build();
        var json = mapper.readTree(result.getResponse().getContentAsString());
        
        assertThat(json.get("id")).isNotNull();
        assertThat(json.get("name")).isEqualTo("Manzana Roja");
    }

    @Test
    void addItem_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/inventory/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId":"00000000-0000-0000-0000-000000000001","name":"","quantity":5,"unit":"unidades"}
                    """))
            .andExpect(status().isBadRequest());
    }

    // ── List items ───────────────────────────────────────

    @Test
    void listItems_returns200() throws Exception {
        mockMvc.perform(get("/api/inventory/items")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    // ── Get item by ID ───────────────────────────────────

    @Test
    void getItemById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/inventory/items/{id}", "nonexistent-id"))
            .andExpect(status().isNotFound());
    }

    // ── Update quantity ──────────────────────────────────

    @Test
    void updateQuantity_valid_returns200() throws Exception {
        var result = mockMvc.perform(post("/api/inventory/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId":"00000000-0000-0000-0000-000000000001","name":"Manzana Roja","quantity":5,"unit":"unidades"}
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        var mapper = new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder()
                .build();
        var json = mapper.readTree(result.getResponse().getContentAsString());
        
        String itemId = json.get("id").asText();

        mockMvc.perform(put("/api/inventory/items/{id}/quantity", itemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"quantity":10}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    void updateQuantity_invalidId_returns400() throws Exception {
        mockMvc.perform(put("/api/inventory/items/{id}/quantity", "invalid-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"quantity":5}
                    """))
            .andExpect(status().isBadRequest());
    }

    // ── Health check endpoints ───────────────────────────

    @Test
    void productHealth_returnsOk() throws Exception {
        mockMvc.perform(get("/api/products/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void scanHealth_returnsOk() throws Exception {
        mockMvc.perform(get("/api/scan/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }
}

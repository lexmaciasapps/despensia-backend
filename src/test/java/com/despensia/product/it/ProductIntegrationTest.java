package com.despensia.product.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for product CRUD via REST API.
 */
@AutoConfigureMockMvc
class ProductIntegrationTest extends com.despensia.DespensiaBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    // ── Create product ───────────────────────────────────

    @Test
    void createProduct_valid_returns200() throws Exception {
        var result = mockMvc.perform(post("/api/products")
                .param("name", "Manzana Roja")
                .param("productType", "ORGANIC"))
            .andExpect(status().isOk())
            .andReturn();

        // Verify product was persisted (check DB via ProductService)
    }

    @Test
    void createProduct_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/products")
                .param("productType", "PACKAGED"))
            .andExpect(status().isBadRequest());
    }

    // ── List products ───────────────────────────────────

    @Test
    void listProducts_emptyPage_returns200() throws Exception {
        mockMvc.perform(get("/api/products")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    // ── Get product by ID ───────────────────────────────

    @Test
    void getProductById_notFound_returns404() throws Exception {
        // Use a valid UUID that doesn't exist in the DB (not an invalid format string)
        mockMvc.perform(get("/api/products/{id}", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
            .andExpect(status().isNotFound());
    }
}

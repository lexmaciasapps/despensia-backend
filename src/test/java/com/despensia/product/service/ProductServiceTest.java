package com.despensia.product.service;

import com.despensia.product.domain.AiVisualEstimateStrategy;
import com.despensia.product.domain.ConservativeAverageStrategy;
import com.despensia.product.domain.ProductItem;
import com.despensia.product.repository.ProductItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductItemRepository productItemRepository;

    @Mock
    private ConservativeAverageStrategy conservativeStrategy;

    @Mock
    private AiVisualEstimateStrategy aiStrategy;

    @InjectMocks
    private ProductService productService;

    @Test
    void testCreate_packagedTypeAppliesConservativeStrategy() {
        when(conservativeStrategy.defaultFor(any())).thenReturn(30);
        when(productItemRepository.save(any(ProductItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductItem result = productService.create("Leche Entera", ProductItem.ProductType.PACKAGED);

        assertThat(result).isNotNull();
        assertThat(result.getProductType()).isEqualTo(ProductItem.ProductType.PACKAGED);
        verify(productItemRepository).save(any(ProductItem.class));
    }

    @Test
    void testCreate_organicTypeAppliesAiStrategy() {
        when(aiStrategy.defaultFor(any())).thenReturn(14);
        when(productItemRepository.save(any(ProductItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductItem result = productService.create("Manzanas", ProductItem.ProductType.ORGANIC);

        assertThat(result).isNotNull();
        assertThat(result.getProductType()).isEqualTo(ProductItem.ProductType.ORGANIC);
    }

    @Test
    void testCreate_appliesDefaultExpirationAndReturnsPersistedEntity() {
        ArgumentCaptor<ProductItem> captor = ArgumentCaptor.forClass(ProductItem.class);
        when(conservativeStrategy.defaultFor(any())).thenReturn(45);
        when(productItemRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        ProductItem result = productService.create("Yogurt", ProductItem.ProductType.PACKAGED);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Yogurt");
    }

    @Test
    void testCreate_returnsEntityWithExpirationDateSet() {
        when(conservativeStrategy.defaultFor(any())).thenReturn(60);
        when(productItemRepository.save(any(ProductItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductItem result = productService.create("Queso", ProductItem.ProductType.PACKAGED);

        assertThat(result).isNotNull();
        assertThat(result.getEstimatedDaysRemaining()).isEqualTo(60);
    }
}

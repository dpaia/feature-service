package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.domain.dtos.ProductDto;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.mappers.ProductMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceFindAllProductsTest {
    @Mock
    ProductRepository productRepository;

    @Mock
    ProductMapper productMapper;

    @InjectMocks
    ProductService service;

    @Test
    void shouldReturnAllProductsMappedToDtos() {
        var product1 = mock(Product.class);
        var product2 = mock(Product.class);
        var dto1 = new ProductDto(1L, "intellij", "IDEA", "IntelliJ IDEA", "JetBrains IDE", "idea.png", false, "admin");
        var dto2 = new ProductDto(2L, "goland", "GO", "GoLand", "JetBrains Go IDE", "go.png", false, "admin");
        when(productRepository.findAll()).thenReturn(List.of(product1, product2));
        when(productMapper.toDto(product1)).thenReturn(dto1);
        when(productMapper.toDto(product2)).thenReturn(dto2);

        List<ProductDto> result = service.findAllProducts();

        assertThat(result).containsExactly(dto1, dto2);
        verify(productRepository).findAll();
        verify(productMapper).toDto(product1);
        verify(productMapper).toDto(product2);
    }

    @Test
    void shouldReturnEmptyListWhenNoProducts() {
        when(productRepository.findAll()).thenReturn(List.of());

        List<ProductDto> result = service.findAllProducts();

        assertThat(result).isEmpty();
    }
}

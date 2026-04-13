package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.domain.Commands.UpdateProductCommand;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.ProductMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceUpdateProductTest {
    @Mock
    ProductRepository productRepository;

    @Mock
    ProductMapper productMapper;

    @InjectMocks
    ProductService service;

    @Test
    void shouldUpdateExistingProduct() {
        var cmd = new UpdateProductCommand("code1", "PFX", "New Name", "New Desc", "img.png", "admin");
        var product = new Product();
        when(productRepository.findByCode("code1")).thenReturn(Optional.of(product));

        service.updateProduct(cmd);

        verify(productRepository).save(product);
        assertThat(product.getPrefix()).isEqualTo("PFX");
        assertThat(product.getName()).isEqualTo("New Name");
        assertThat(product.getDescription()).isEqualTo("New Desc");
        assertThat(product.getImageUrl()).isEqualTo("img.png");
        assertThat(product.getUpdatedBy()).isEqualTo("admin");
    }

    @Test
    void shouldThrowWhenProductNotFound() {
        var cmd = new UpdateProductCommand("missing", "PFX", "N", "D", "img", "u");
        when(productRepository.findByCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProduct(cmd)).isInstanceOf(ResourceNotFoundException.class);
        verify(productRepository, never()).save(any());
    }
}

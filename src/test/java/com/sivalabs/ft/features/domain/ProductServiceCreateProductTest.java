package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.domain.Commands.CreateProductCommand;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.mappers.ProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceCreateProductTest {
    @Mock
    ProductRepository productRepository;

    @Mock
    ProductMapper productMapper;

    @InjectMocks
    ProductService service;

    @Test
    void shouldCreateProductAndReturnId() {
        var cmd = new CreateProductCommand("code1", "PFX", "Name", "Desc", "img.png", "admin");
        var saved = new Product();
        saved.setId(42L);
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        Long id = service.createProduct(cmd);

        assertThat(id).isEqualTo(42L);
        var captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("code1");
        assertThat(captor.getValue().getPrefix()).isEqualTo("PFX");
        assertThat(captor.getValue().getName()).isEqualTo("Name");
        assertThat(captor.getValue().getDescription()).isEqualTo("Desc");
        assertThat(captor.getValue().getImageUrl()).isEqualTo("img.png");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("admin");
        assertThat(captor.getValue().getDisabled()).isFalse();
    }
}

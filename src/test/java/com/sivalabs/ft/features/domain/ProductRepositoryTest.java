package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProductRepositoryTest extends AbstractIT {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void testFindByCode() {
        Product productByCode = productRepository
                .findByCode("intellij")
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        assertThat(productByCode.getCode())
                .as("Product code does not match condition")
                .isEqualTo("intellij");
    }
}

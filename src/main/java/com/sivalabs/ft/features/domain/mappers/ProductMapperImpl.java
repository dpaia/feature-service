package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.ProductDto;
import com.sivalabs.ft.features.domain.entities.Product;

public class ProductMapperImpl implements ProductMapper {
    @Override
    public ProductDto toDto(Product product) {
        if (product == null) {
            return null;
        }

        return new ProductDto(
                product.getId(),
                product.getCode(),
                product.getPrefix(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getDisabled(),
                product.getCreatedBy());
    }
}

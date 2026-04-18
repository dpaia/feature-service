package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.CategoryDto;
import com.sivalabs.ft.features.domain.entities.Category;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {
    CategoryDto toDto(Category category);
}

package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.entities.Release;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReleaseMapper {
    @Mapping(target = "milestoneCode", source = "milestone.code")
    @Mapping(target = "productCode", source = "product.code")
    ReleaseDto toDto(Release release);
}

package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.entities.Release;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReleaseMapper {
    @Mapping(target = "parentCode", source = "parent.code", defaultExpression = "java( null )")
    ReleaseDto toDto(Release release);
}

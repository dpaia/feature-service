package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.MilestoneDto;
import com.sivalabs.ft.features.domain.entities.Milestone;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MilestoneMapper {
    @Mapping(target = "completedFeatures", source = "resolvedFeatures")
    MilestoneDto toDto(Milestone milestone);

    @Mapping
    Milestone toEntity(MilestoneDto dto);
}

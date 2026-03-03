package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.MilestoneDto;
import com.sivalabs.ft.features.domain.entities.Milestone;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MilestoneMapper {

    MilestoneDto toDto(Milestone milestone);

    Milestone toEntity(MilestoneDto dto);
}

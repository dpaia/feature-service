package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.PlanningHistoryDto;
import com.sivalabs.ft.features.domain.entities.PlanningHistory;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PlanningHistoryMapper {
    PlanningHistoryDto toDto(PlanningHistory planningHistory);
}

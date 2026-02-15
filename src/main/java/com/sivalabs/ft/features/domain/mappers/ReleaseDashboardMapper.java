package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.ReleaseDashboardDto;
import com.sivalabs.ft.features.domain.entities.Release;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReleaseDashboardMapper {

    @Mapping(source = "code", target = "releaseCode")
    @Mapping(source = "description", target = "releaseName")
    @Mapping(target = "overview", ignore = true)
    @Mapping(target = "healthIndicators", ignore = true)
    @Mapping(target = "timeline", ignore = true)
    @Mapping(target = "featureBreakdown", ignore = true)
    ReleaseDashboardDto toDto(Release release);
}

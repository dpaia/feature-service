package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.FeatureUsageDto;
import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FeatureUsageMapper {
    FeatureUsageDto toDto(FeatureUsage featureUsage);
}

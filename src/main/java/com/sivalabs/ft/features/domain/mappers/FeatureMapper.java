package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.entities.Feature;

public interface FeatureMapper {
    FeatureDto toDto(Feature feature);
}

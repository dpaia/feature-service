package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.entities.Feature;

public class FeatureMapperImpl implements FeatureMapper {
    @Override
    public FeatureDto toDto(Feature feature) {
        if (feature == null) {
            return null;
        }

        var release = feature.getRelease();
        String releaseCode = release != null ? release.getCode() : null;

        return new FeatureDto(
                feature.getId(),
                feature.getCode(),
                feature.getTitle(),
                feature.getDescription(),
                feature.getStatus(),
                releaseCode,
                false,
                feature.getAssignedTo(),
                feature.getCreatedBy(),
                feature.getCreatedAt(),
                feature.getUpdatedBy(),
                feature.getUpdatedAt(),
                feature.getPlannedCompletionDate(),
                feature.getPlanningStatus(),
                feature.getFeatureOwner(),
                feature.getNotes(),
                feature.getBlockageReason());
    }
}

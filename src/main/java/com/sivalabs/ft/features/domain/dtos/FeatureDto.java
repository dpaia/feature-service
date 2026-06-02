package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record FeatureDto(
        Long id,
        String code,
        String title,
        String description,
        FeatureStatus status,
        String releaseCode,
        boolean isFavorite,
        String assignedTo,
        List<TagDto> tags,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt)
        implements Serializable {

    public FeatureDto makeFavorite(boolean favorite) {
        return new FeatureDto(
                id,
                code,
                title,
                description,
                status,
                releaseCode,
                favorite,
                assignedTo,
                tags,
                createdBy,
                createdAt,
                updatedBy,
                updatedAt);
    }
}

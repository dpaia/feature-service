package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.entities.Release;

public class ReleaseMapperImpl implements ReleaseMapper {
    @Override
    public ReleaseDto toDto(Release release) {
        if (release == null) {
            return null;
        }

        return new ReleaseDto(
                release.getId(),
                release.getCode(),
                release.getDescription(),
                release.getStatus(),
                release.getReleasedAt(),
                release.getCreatedBy(),
                release.getCreatedAt(),
                release.getUpdatedBy(),
                release.getUpdatedAt());
    }
}

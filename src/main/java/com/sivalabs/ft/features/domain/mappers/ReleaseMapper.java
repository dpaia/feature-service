package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.entities.Release;

public interface ReleaseMapper {
    ReleaseDto toDto(Release release);
}

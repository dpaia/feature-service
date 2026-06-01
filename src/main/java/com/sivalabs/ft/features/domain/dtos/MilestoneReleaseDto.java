package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.io.Serializable;

public record MilestoneReleaseDto(Long id, String code, String description, ReleaseStatus status)
        implements Serializable {}

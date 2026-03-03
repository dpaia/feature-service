package com.sivalabs.ft.features.domain.dtos;

import java.io.Serializable;
import java.time.LocalDate;

public record MilestoneDto(
        Long id,
        String code,
        String releaseCode,
        LocalDate targetDate,
        int completedFeatures,
        int totalFeatures)
        implements Serializable {}

package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.MilestoneStatus;
import java.io.Serializable;
import java.time.Instant;

public record MilestoneSummaryDto(
        Long id,
        String code,
        String name,
        String description,
        Instant targetDate,
        Instant actualDate,
        MilestoneStatus status,
        String productCode,
        String owner,
        String notes,
        Integer progress,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt)
        implements Serializable {}

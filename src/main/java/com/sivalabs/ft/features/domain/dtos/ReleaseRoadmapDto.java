package com.sivalabs.ft.features.domain.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.io.Serializable;
import java.time.Instant;

public record ReleaseRoadmapDto(
        Long id,
        String code,
        String description,
        ReleaseStatus status,
        Instant releasedAt,
        Instant plannedStartDate,
        Instant plannedReleaseDate,
        Instant actualReleaseDate,
        String owner,
        String notes,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt,
        @JsonIgnore String productCode,
        @JsonIgnore String productName)
        implements Serializable {}

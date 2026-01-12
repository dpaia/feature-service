package com.sivalabs.ft.features.api.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

public record RoadmapRelease(
        Long id,
        String code,
        String description,
        String status,
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
        @JsonIgnore ProductInfo product) {}

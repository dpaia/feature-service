package com.sivalabs.ft.features.domain.dtos;

import java.time.Instant;

public record RoadmapFeatureDto(
        Long id,
        String code,
        String title,
        String description,
        String status,
        String releaseCode,
        String assignedTo,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) {}

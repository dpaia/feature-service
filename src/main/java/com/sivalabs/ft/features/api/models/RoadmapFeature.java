package com.sivalabs.ft.features.api.models;

import java.time.Instant;

public record RoadmapFeature(
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

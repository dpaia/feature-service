package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;

public record ReleaseEvent(
        Long id,
        String code,
        String description,
        ReleaseStatus status,
        Instant releasedAt,
        String milestoneCode,
        String productCode,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt,
        String eventType) {}

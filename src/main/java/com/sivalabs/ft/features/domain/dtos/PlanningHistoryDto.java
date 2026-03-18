package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ChangeType;
import com.sivalabs.ft.features.domain.models.EntityType;
import java.io.Serializable;
import java.time.Instant;

public record PlanningHistoryDto(
        Long id,
        EntityType entityType,
        Long entityId,
        String entityCode,
        ChangeType changeType,
        String fieldName,
        String oldValue,
        String newValue,
        String rationale,
        String changedBy,
        Instant changedAt)
        implements Serializable {}

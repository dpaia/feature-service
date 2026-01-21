package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ChangeType;
import com.sivalabs.ft.features.domain.models.EntityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record PlanningHistoryDto(
        Long id,
        @NotNull EntityType entityType,
        @NotNull Long entityId,
        @NotNull @Size(max = 100) String entityCode,
        @NotNull ChangeType changeType,
        @Size(max = 100) String fieldName,
        @Size(max = 1000) String oldValue,
        @Size(max = 1000) String newValue,
        @Size(max = 500) String rationale,
        @NotNull @Size(max = 100) String changedBy,
        @NotNull Instant changedAt) {}

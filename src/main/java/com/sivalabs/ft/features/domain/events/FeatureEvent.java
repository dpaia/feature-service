package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
import java.time.LocalDate;

public record FeatureEvent(
        Long id,
        String code,
        String title,
        String description,
        FeatureStatus status,
        String releaseCode,
        String assignedTo,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt,
        // Planning fields
        LocalDate plannedCompletionDate,
        FeaturePlanningStatus planningStatus,
        String featureOwner,
        String notes,
        String blockageReason,
        String eventType) {}

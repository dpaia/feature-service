package com.sivalabs.ft.features.api.models;

import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateFeaturePayload(
        @NotEmpty(message = "Title is required") @Size(max = 500, message = "Title cannot exceed 500 characters") String title,
        String description,
        String releaseCode,
        String assignedTo,
        FeatureStatus status,
        Instant plannedCompletionAt,
        Instant actualCompletionAt,
        FeaturePlanningStatus featurePlanningStatus,
        String featureOwner,
        String blockageReason) {}

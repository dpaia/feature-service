package com.sivalabs.ft.features.api.models;

import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import java.time.Instant;

public record UpdateFeaturePlanningPayload(
        Instant plannedCompletionDate,
        FeaturePlanningStatus status,
        String featureOwner,
        String notes,
        String blockageReason) {}

package com.sivalabs.ft.features.api.models;

import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import java.time.LocalDate;

public record UpdateFeaturePlanningPayload(
        LocalDate plannedCompletionDate,
        FeaturePlanningStatus planningStatus,
        String featureOwner,
        String notes,
        String blockageReason) {}

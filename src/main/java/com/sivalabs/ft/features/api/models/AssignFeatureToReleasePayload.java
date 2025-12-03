package com.sivalabs.ft.features.api.models;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record AssignFeatureToReleasePayload(
        @NotBlank String featureCode, Instant plannedCompletionDate, String featureOwner, String notes) {}

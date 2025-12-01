package com.sivalabs.ft.features.api.models;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record AssignFeatureToReleasePayload(
        @NotBlank String featureCode, LocalDate plannedCompletionDate, String featureOwner, String notes) {}

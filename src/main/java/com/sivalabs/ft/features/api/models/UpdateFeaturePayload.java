package com.sivalabs.ft.features.api.models;

import com.sivalabs.ft.features.domain.models.FeatureStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record UpdateFeaturePayload(
        String eventId, // Optional - if not provided, will be generated
        @NotEmpty(message = "Title is required") @Size(max = 500, message = "Title cannot exceed 500 characters") String title,
        String description,
        String releaseCode,
        String assignedTo,
        FeatureStatus status) {}

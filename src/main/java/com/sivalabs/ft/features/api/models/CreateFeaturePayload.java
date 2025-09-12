package com.sivalabs.ft.features.api.models;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateFeaturePayload(
        String eventId, // Optional - if not provided, will be generated
        @NotEmpty(message = "Product code is required") String productCode,
        @NotEmpty(message = "Title is required") @Size(max = 500, message = "Title cannot exceed 500 characters") String title,
        String description,
        String releaseCode,
        String assignedTo) {}

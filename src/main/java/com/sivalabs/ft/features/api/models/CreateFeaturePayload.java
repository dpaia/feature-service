package com.sivalabs.ft.features.api.models;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateFeaturePayload(
        @NotEmpty(message = "At least one product code is required") List<String> productCodes,
        @NotEmpty(message = "Title is required") @Size(max = 500, message = "Title cannot exceed 500 characters") String title,
        String description,
        String releaseCode,
        String assignedTo) {}

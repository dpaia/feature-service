package com.sivalabs.ft.features.api.models;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record UpdateCategoryPayload(
        @NotEmpty(message = "Name is required") @Size(max = 50, message = "Name cannot exceed 50 characters") String name,
        String description,
        Long parentCategoryId) {}

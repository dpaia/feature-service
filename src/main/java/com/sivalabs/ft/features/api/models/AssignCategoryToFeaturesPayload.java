package com.sivalabs.ft.features.api.models;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AssignCategoryToFeaturesPayload(
        @NotEmpty(message = "Feature codes are required") List<String> featureCodes,
        @NotNull(message = "Category ID is required") Long categoryId) {}

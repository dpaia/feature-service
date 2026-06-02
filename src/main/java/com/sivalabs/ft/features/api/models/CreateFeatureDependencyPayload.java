package com.sivalabs.ft.features.api.models;

import jakarta.validation.constraints.NotEmpty;

public record CreateFeatureDependencyPayload(
        @NotEmpty(message = "Depends on feature code is required") String dependsOnFeatureCode,
        @NotEmpty(message = "Dependency type is required") String dependencyType,
        String notes) {}

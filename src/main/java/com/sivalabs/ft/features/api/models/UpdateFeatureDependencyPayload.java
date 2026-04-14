package com.sivalabs.ft.features.api.models;

import jakarta.validation.constraints.NotEmpty;

public record UpdateFeatureDependencyPayload(
        @NotEmpty(message = "Dependency type is required") String dependencyType, String notes) {}

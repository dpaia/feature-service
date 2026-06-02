package com.sivalabs.ft.features.api.models;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AssignTagsToFeaturesPayload(
        @NotEmpty(message = "Feature codes are required") List<String> featureCodes,
        @NotEmpty(message = "Tag IDs are required") List<Long> tagIds) {}

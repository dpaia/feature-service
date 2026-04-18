package com.sivalabs.ft.features.api.models;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RemoveCategoryFromFeaturesPayload(
        @NotEmpty(message = "Feature codes are required") List<String> featureCodes) {}

package com.sivalabs.ft.features.api.models;

import jakarta.validation.constraints.NotBlank;

public record MoveFeaturePayload(@NotBlank String targetReleaseCode, String rationale) {}

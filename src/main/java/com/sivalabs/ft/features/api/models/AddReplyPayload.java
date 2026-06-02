package com.sivalabs.ft.features.api.models;

import jakarta.validation.constraints.NotBlank;

public record AddReplyPayload(@NotBlank String content) {}

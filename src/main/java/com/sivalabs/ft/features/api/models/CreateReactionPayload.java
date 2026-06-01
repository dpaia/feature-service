package com.sivalabs.ft.features.api.models;

import com.sivalabs.ft.features.domain.models.ReactionType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateReactionPayload(
        @NotEmpty(message = "Feature code is required") String featureCode,
        @NotNull(message = "Reaction type is required") ReactionType reactionType) {}

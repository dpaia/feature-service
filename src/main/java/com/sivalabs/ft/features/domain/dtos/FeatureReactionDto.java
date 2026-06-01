package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ReactionType;

import java.time.Instant;

public record FeatureReactionDto(
    Long id,
    String featureCode,
    String userId,
    ReactionType reactionType,
    Instant createdAt,
    Instant updatedAt
) {}
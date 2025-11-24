package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ActionType;
import java.time.Instant;

public record FeatureUsageDto(
        Long id,
        String userId,
        String featureCode,
        String productCode,
        String releaseCode,
        ActionType actionType,
        Instant timestamp,
        String context) {}

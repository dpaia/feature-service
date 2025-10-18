package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ActionType;
import java.util.Map;

public record ProductStatsDto(
        String productCode,
        long totalUsageCount,
        long uniqueUserCount,
        long uniqueFeatureCount,
        Map<ActionType, Long> usageByActionType,
        Map<String, Long> topFeatures,
        Map<String, Long> topUsers) {}

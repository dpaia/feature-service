package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ActionType;
import java.util.List;
import java.util.Map;

public record ProductStatsDto(
        String productCode,
        long totalUsageCount,
        long uniqueUserCount,
        long uniqueFeatureCount,
        Map<ActionType, Long> usageByActionType,
        List<TopItemDto> topFeatures,
        List<TopItemDto> topUsers) {}

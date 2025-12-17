package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ActionType;
import java.util.List;
import java.util.Map;

public record UsageStatsDto(
        long totalUsageCount,
        long uniqueUserCount,
        long uniqueFeatureCount,
        long uniqueProductCount,
        Map<ActionType, Long> usageByActionType,
        List<TopItemDto> topFeatures,
        List<TopItemDto> topProducts,
        List<TopItemDto> topUsers) {}

package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ActionType;
import java.util.List;
import java.util.Map;

public record FeatureStatsDto(
        String featureCode,
        long totalUsageCount,
        long uniqueUserCount,
        Map<ActionType, Long> usageByActionType,
        List<TopItemDto> topUsers,
        List<TopItemDto> usageByProduct) {}

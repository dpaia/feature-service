package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ActionType;
import java.util.Map;

public record UsageStatsDto(
        long totalEvents,
        long uniqueUsers,
        long uniqueFeatures,
        Map<ActionType, Long> eventsByActionType,
        Map<String, Long> topFeatures,
        Map<String, Long> topUsers) {}

package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ActionType;
import java.util.List;
import java.util.Map;

/**
 * DTO representing analytics for a user segment.
 * Provides aggregated usage metrics and top features for a specific segment.
 *
 * @param segmentName Name of the segment (e.g., "Mobile Users", "Power Users")
 * @param segmentCriteria Criteria defining the segment (e.g., {"device": "mobile"})
 * @param totalUsage Total usage count for this segment
 * @param uniqueUsers Number of unique users in this segment
 * @param topFeatures Most used features in this segment
 * @param usageByActionType Usage breakdown by action type
 */
public record SegmentAnalyticsDto(
        String segmentName,
        Map<String, String> segmentCriteria,
        long totalUsage,
        long uniqueUsers,
        List<TopItemDto> topFeatures,
        Map<ActionType, Long> usageByActionType) {}

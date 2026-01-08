package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ActionType;
import com.sivalabs.ft.features.domain.models.PeriodType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO for advanced multi-dimensional filtering of usage events.
 * Supports filtering by multiple criteria including custom context tags.
 *
 * @param featureCodes List of feature codes to filter by
 * @param productCodes List of product codes to filter by
 * @param releaseCodes List of release codes to filter by
 * @param actionTypes List of action types to filter by
 * @param startDate Start of time range
 * @param endDate End of time range
 * @param contextTags Custom tags from context JSON for filtering
 * @param userSegment User segment identifier for filtering
 * @param groupBy Optional grouping dimension
 */
public record AdvancedFilterDto(
        List<String> featureCodes,
        List<String> productCodes,
        List<String> releaseCodes,
        List<ActionType> actionTypes,
        Instant startDate,
        Instant endDate,
        Map<String, String> contextTags,
        String userSegment,
        PeriodType groupBy) {}

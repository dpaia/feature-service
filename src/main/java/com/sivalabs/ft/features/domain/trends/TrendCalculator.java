package com.sivalabs.ft.features.domain.trends;

import com.sivalabs.ft.features.domain.dtos.UsageTrendDto;
import com.sivalabs.ft.features.domain.models.PeriodType;
import java.util.List;

/**
 * Strategy interface for calculating usage trends for different period types.
 * Each implementation handles specific period grouping logic (daily, weekly, monthly).
 */
public interface TrendCalculator {

    /**
     * Calculate usage trends from raw database results.
     *
     * @param rawData List of Object[] containing [period_timestamp, usage_count, unique_user_count]
     * @return List of UsageTrendDto with calculated growth rates and formatted periods
     */
    List<UsageTrendDto> calculate(List<Object[]> rawData);

    /**
     * Get the period type this calculator handles.
     *
     * @return PeriodType this calculator is designed for
     */
    PeriodType getPeriodType();

    /**
     * Format period timestamp into human-readable period string.
     *
     * @param periodTimestamp The period timestamp from DATE_TRUNC
     * @return Formatted period string (e.g., "2024-01-15", "2024-W03", "2024-01")
     */
    String formatPeriod(java.time.Instant periodTimestamp);
}

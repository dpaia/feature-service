package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.TrendDirection;
import jakarta.validation.constraints.NotNull;

/**
 * DTO representing summary statistics for a trend analysis.
 * Provides aggregated metrics across all periods in the trend.
 */
public record TrendSummaryDto(
        /**
         * Total usage count across all periods
         */
        long totalUsage,

        /**
         * Average usage per period
         */
        double averageUsagePerPeriod,

        /**
         * Overall growth rate from first to last period (percentage)
         */
        double overallGrowthRate,

        /**
         * General direction of the trend
         */
        @NotNull TrendDirection trendDirection) {}

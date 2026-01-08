package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.PeriodType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * DTO representing usage trend data for a specific time period.
 * Contains usage metrics and growth information for trend analysis.
 */
public record UsageTrendDto(
        /**
         * Period identifier (e.g., "2024-01-15", "2024-W03", "2024-01")
         */
        @NotNull String period,

        /**
         * Type of period (DAILY, WEEKLY, MONTHLY)
         */
        @NotNull PeriodType periodType,

        /**
         * Total usage count for this period
         */
        long usageCount,

        /**
         * Number of unique users in this period
         */
        long uniqueUserCount,

        /**
         * Growth rate compared to previous period (percentage)
         * Positive values indicate growth, negative indicate decline
         */
        double growthRate,

        /**
         * Start timestamp of the period
         */
        @NotNull Instant periodStart,

        /**
         * End timestamp of the period
         */
        @NotNull Instant periodEnd) {}

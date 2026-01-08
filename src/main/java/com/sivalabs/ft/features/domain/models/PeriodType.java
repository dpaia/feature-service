package com.sivalabs.ft.features.domain.models;

/**
 * Enum representing different time period types for trend analysis.
 * Used for grouping usage data by different time intervals.
 */
public enum PeriodType {
    /**
     * Daily grouping - groups data by calendar day
     */
    DAY,

    /**
     * Weekly grouping - groups data by ISO-8601 week (Monday to Sunday)
     */
    WEEK,

    /**
     * Monthly grouping - groups data by calendar month
     */
    MONTH
}

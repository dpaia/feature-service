package com.sivalabs.ft.features.domain.models;

/**
 * Enum representing the overall direction of a usage trend.
 * Used to categorize trends based on their growth pattern.
 */
public enum TrendDirection {
    /**
     * Trend is generally increasing over time
     */
    INCREASING,

    /**
     * Trend is generally decreasing over time
     */
    DECREASING,

    /**
     * Trend is relatively stable with minimal change
     */
    STABLE
}

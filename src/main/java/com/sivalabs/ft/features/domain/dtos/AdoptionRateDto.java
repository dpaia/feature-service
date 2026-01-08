package com.sivalabs.ft.features.domain.dtos;

import java.time.Instant;
import java.util.Map;

/**
 * DTO representing adoption rate metrics for a feature after its release.
 * Tracks how quickly and effectively users adopt a newly released feature.
 *
 * @param featureCode Unique feature code
 * @param releaseDate Date when the feature was released
 * @param adoptionWindows Map of time windows (7, 30, 90 days) to adoption metrics
 * @param overallAdoptionScore Overall adoption score (0-100) based on weighted metrics
 * @param totalUniqueUsers Total unique users who adopted the feature since release
 * @param adoptionGrowthRate Growth rate of adoption (percentage change)
 */
public record AdoptionRateDto(
        String featureCode,
        Instant releaseDate,
        Map<Integer, AdoptionWindowMetrics> adoptionWindows,
        double overallAdoptionScore,
        long totalUniqueUsers,
        double adoptionGrowthRate) {

    /**
     * Metrics for a specific time window after release.
     *
     * @param windowDays Number of days in the window (e.g., 7, 30, 90)
     * @param uniqueUsers Number of unique users who adopted in this window
     * @param totalUsage Total usage count in this window
     * @param adoptionRate Percentage of target users who adopted (0-100)
     * @param growthRate Growth rate compared to previous window
     */
    public record AdoptionWindowMetrics(
            int windowDays, long uniqueUsers, long totalUsage, double adoptionRate, double growthRate) {}
}

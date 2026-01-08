package com.sivalabs.ft.features.domain.dtos;

import java.time.Instant;

/**
 * DTO for comparing adoption metrics between multiple features.
 * Simplified version of AdoptionRateDto for comparison purposes.
 *
 * @param featureCode Unique feature code
 * @param releaseDate Date when the feature was released
 * @param windowDays Time window for metrics (7, 30, or 90 days)
 * @param uniqueUsers Number of unique users in the window
 * @param totalUsage Total usage count in the window
 * @param adoptionRate Percentage of adoption (0-100)
 * @param adoptionScore Overall adoption score (0-100)
 * @param growthRate Growth rate percentage
 * @param rank Ranking among compared features (1 = best)
 */
public record AdoptionMetricsDto(
        String featureCode,
        Instant releaseDate,
        int windowDays,
        long uniqueUsers,
        long totalUsage,
        double adoptionRate,
        double adoptionScore,
        double growthRate,
        Integer rank) {}

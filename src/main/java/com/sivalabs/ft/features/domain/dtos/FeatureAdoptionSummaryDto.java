package com.sivalabs.ft.features.domain.dtos;

/**
 * DTO representing a brief adoption summary for a feature within a release.
 * Used in release statistics to show adoption status of all features.
 *
 * @param featureCode Unique feature code
 * @param featureName Feature name/title
 * @param uniqueUsers Number of unique users who adopted this feature
 * @param totalUsage Total usage count for this feature
 * @param adoptionScore Adoption score (0-100)
 * @param adoptionRate Adoption rate percentage (0-100)
 */
public record FeatureAdoptionSummaryDto(
        String featureCode,
        String featureName,
        long uniqueUsers,
        long totalUsage,
        double adoptionScore,
        double adoptionRate) {}

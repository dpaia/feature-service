package com.sivalabs.ft.features.domain.dtos;

import java.time.Instant;
import java.util.List;

/**
 * DTO representing comprehensive statistics for a release.
 * Includes aggregated metrics and individual feature adoption summaries.
 *
 * @param releaseCode Unique release code
 * @param releaseDate Date when the release was deployed
 * @param totalFeatures Total number of features in this release
 * @param featuresWithUsage Number of features that have been used at least once
 * @param totalUniqueUsers Total unique users across all features in release
 * @param totalUsage Total usage count across all features
 * @param averageAdoptionScore Average adoption score across all features
 * @param topAdoptedFeatures List of feature adoption summaries, sorted by adoption score
 * @param overallReleaseScore Overall release success score (0-100)
 */
public record ReleaseStatsDto(
        String releaseCode,
        Instant releaseDate,
        int totalFeatures,
        int featuresWithUsage,
        long totalUniqueUsers,
        long totalUsage,
        double averageAdoptionScore,
        List<FeatureAdoptionSummaryDto> topAdoptedFeatures,
        double overallReleaseScore) {}

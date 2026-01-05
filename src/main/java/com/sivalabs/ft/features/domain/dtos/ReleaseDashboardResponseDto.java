package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import java.util.Map;

public record ReleaseDashboardResponseDto(
        String releaseCode,
        String releaseName,
        ReleaseStatus status,
        OverviewDto overview,
        HealthIndicatorsDto healthIndicators,
        TimelineDto timeline,
        FeatureBreakdownDto featureBreakdown) {
    public static record OverviewDto(
            int totalFeatures,
            int completedFeatures,
            int inProgressFeatures,
            int blockedFeatures,
            int pendingFeatures,
            double completionPercentage) {}

    public static record TimelineDto(
            Instant startDate, Instant plannedEndDate, Instant estimatedEndDate, Instant actualEndDate) {}

    public static record FeatureBreakdownDto(Map<String, Integer> byStatus, Map<String, Integer> byOwner) {}
}

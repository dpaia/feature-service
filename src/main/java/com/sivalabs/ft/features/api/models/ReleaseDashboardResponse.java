package com.sivalabs.ft.features.api.models;

import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import java.util.Map;

public record ReleaseDashboardResponse(
        String releaseCode,
        String releaseName,
        ReleaseStatus status,
        Overview overview,
        HealthIndicators healthIndicators,
        Timeline timeline,
        FeatureBreakdown featureBreakdown) {
    public record Overview(
            int totalFeatures,
            int completedFeatures,
            int inProgressFeatures,
            int blockedFeatures,
            int pendingFeatures,
            double completionPercentage,
            int estimatedDaysRemaining) {}

    public record HealthIndicators(String timelineAdherence, String riskLevel, int blockedFeatures) {}

    public record Timeline(
            Instant startDate, Instant plannedEndDate, Instant estimatedEndDate, Instant actualEndDate) {}

    public record FeatureBreakdown(
            Map<String, Integer> byStatus, Map<String, Integer> byOwner, Map<String, Integer> byPriority) {}
}

package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import com.sivalabs.ft.features.domain.models.RiskLevel;
import com.sivalabs.ft.features.domain.models.TimelineAdherence;
import java.time.Instant;
import java.util.Map;

public record ReleaseDashboardDto(
        String releaseCode,
        String releaseName,
        ReleaseStatus status,
        DashboardOverview overview,
        HealthIndicators healthIndicators,
        Timeline timeline,
        FeatureBreakdown featureBreakdown) {

    public record DashboardOverview(
            int totalFeatures,
            int completedFeatures,
            int inProgressFeatures,
            int blockedFeatures,
            int pendingFeatures,
            double completionPercentage,
            Integer estimatedDaysRemaining) {}

    public record HealthIndicators(TimelineAdherence timelineAdherence, RiskLevel riskLevel, int blockedFeatures) {}

    public record Timeline(
            Instant startDate, Instant plannedEndDate, Instant estimatedEndDate, Instant actualEndDate) {}

    public record FeatureBreakdown(
            Map<String, Integer> byStatus, Map<String, Integer> byOwner, Map<String, Integer> byPriority) {}
}

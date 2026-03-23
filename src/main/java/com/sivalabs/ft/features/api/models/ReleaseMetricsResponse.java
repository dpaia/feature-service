package com.sivalabs.ft.features.api.models;

import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.util.List;
import java.util.Map;

public record ReleaseMetricsResponse(
        String releaseCode,
        ReleaseStatus releaseStatus,
        double completionRate,
        Velocity velocity,
        BlockedTime blockedTime,
        WorkloadDistribution workloadDistribution) {
    public record Velocity(double featuresPerWeek, double averageCycleTime) {}

    public record BlockedTime(
            int totalBlockedDays,
            double averageBlockedDuration,
            double percentageOfTime,
            Map<String, Integer> blockageReasons) {}

    public record WorkloadDistribution(List<OwnerWorkload> byOwner) {}

    public record OwnerWorkload(
            String owner,
            int assignedFeatures,
            int completedFeatures,
            int inProgressFeatures,
            int blockedFeatures,
            double utilizationRate) {}
}

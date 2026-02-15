package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.util.List;
import java.util.Map;

public record ReleaseMetricsDto(
        String releaseCode,
        ReleaseStatus releaseStatus,
        double completionRate,
        Velocity velocity,
        BlockedTime blockedTime,
        WorkloadDistribution workloadDistribution) {

    public record Velocity(double featuresPerWeek, double averageCycleTime) {}

    public record BlockedTime(
            long totalBlockedDays,
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

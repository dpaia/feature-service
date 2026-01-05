package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.util.List;

public record ReleaseMetricsResponseDto(
        String releaseCode,
        ReleaseStatus releaseStatus,
        double completionRate,
        VelocityDto velocity,
        BlockedTimeDto blockedTime,
        WorkloadDistributionDto workloadDistribution) {
    public static record VelocityDto(double featuresPerWeek, double averageCycleTime) {}

    public static record BlockedTimeDto(int totalBlockedDays, double averageBlockedDuration) {}

    public static record WorkloadDistributionDto(List<OwnerWorkloadDto> byOwner) {}

    public static record OwnerWorkloadDto(
            String owner,
            int assignedFeatures,
            int completedFeatures,
            int inProgressFeatures,
            int blockedFeatures,
            double utilizationRate) {}
}

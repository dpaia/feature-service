package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.OverallocationRisk;
import java.util.List;

public record CapacityPlanningResponseDto(
        OverallCapacityDto overallCapacity,
        List<WorkloadByOwnerDto> workloadByOwner,
        CommitmentsDto commitments,
        List<OverallocationWarningDto> overallocationWarnings) {
    public static record OverallCapacityDto(
            int totalResources, double utilizationRate, double availableCapacity, int overallocatedResources) {}

    public static record WorkloadByOwnerDto(
            String owner,
            int currentWorkload,
            int capacity,
            double utilizationRate,
            OverallocationRisk overallocationRisk) {}

    public static record CommitmentsDto(
            int activeReleases, int plannedReleases, int totalFeatures, double estimatedEffort) {}

    public static record OverallocationWarningDto(String owner, String severity, double overallocationPercentage) {}
}

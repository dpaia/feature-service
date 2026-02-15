package com.sivalabs.ft.features.domain.dtos;

import java.util.List;

public record CapacityPlanningDto(
        OverallCapacity overallCapacity,
        List<OwnerWorkload> workloadByOwner,
        Commitments commitments,
        List<OverallocationWarning> overallocationWarnings) {

    public record OverallCapacity(
            int totalResources, double utilizationRate, double availableCapacity, int overallocatedResources) {}

    public record OwnerWorkload(
            String owner,
            int currentWorkload,
            int capacity,
            double utilizationRate,
            int futureCommitments,
            String overallocationRisk) {}

    public record Commitments(int activeReleases, int plannedReleases, int totalFeatures, double estimatedEffort) {}

    public record OverallocationWarning(String owner, String severity, double overallocationPercentage) {}
}

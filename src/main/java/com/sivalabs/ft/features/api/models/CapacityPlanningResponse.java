package com.sivalabs.ft.features.api.models;

import java.util.List;

public record CapacityPlanningResponse(
        OverallCapacity overallCapacity,
        List<WorkloadByOwner> workloadByOwner,
        Commitments commitments,
        List<OverallocationWarning> overallocationWarnings) {
    public record OverallCapacity(
            int totalResources, double utilizationRate, double availableCapacity, int overallocatedResources) {}

    public record WorkloadByOwner(
            String owner,
            int currentWorkload,
            int capacity,
            double utilizationRate,
            int futureCommitments,
            String overallocationRisk) {}

    public record Commitments(int activeReleases, int plannedReleases, int totalFeatures, double estimatedEffort) {}

    public record OverallocationWarning(String owner, String severity, double overallocationPercentage) {}
}

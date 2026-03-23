package com.sivalabs.ft.features.api.models;

import java.util.Map;

public record PlanningHealthResponse(
        Map<String, Integer> releasesByStatus, AtRiskReleases atRiskReleases, PlanningAccuracy planningAccuracy) {
    public record AtRiskReleases(int overdue, int criticallyDelayed, int total) {}

    public record PlanningAccuracy(double onTimeDelivery, double averageDelay, double estimationAccuracy) {}
}

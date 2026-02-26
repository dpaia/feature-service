package com.sivalabs.ft.features.domain.dtos;

import java.util.Map;

public record PlanningHealthDto(
        Map<String, Integer> releasesByStatus, AtRiskReleases atRiskReleases, PlanningAccuracy planningAccuracy) {

    public record AtRiskReleases(int overdue, int criticallyDelayed, int total) {}

    public record PlanningAccuracy(double onTimeDelivery, double averageDelay, double estimationAccuracy) {}
}

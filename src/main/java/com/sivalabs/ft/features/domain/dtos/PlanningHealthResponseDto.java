package com.sivalabs.ft.features.domain.dtos;

import java.util.Map;

public record PlanningHealthResponseDto(
        Map<String, Integer> releasesByStatus, AtRiskReleasesDto atRiskReleases, PlanningAccuracyDto planningAccuracy) {
    public static record AtRiskReleasesDto(int overdue, int criticallyDelayed, int total) {}

    public static record PlanningAccuracyDto(double onTimeDelivery, double averageDelay, double estimationAccuracy) {}
}

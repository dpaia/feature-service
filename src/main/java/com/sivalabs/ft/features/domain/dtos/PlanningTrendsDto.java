package com.sivalabs.ft.features.domain.dtos;

import java.util.List;

public record PlanningTrendsDto(
        ReleasesCompleted releasesCompleted,
        AverageReleaseDuration averageReleaseDuration,
        PlanningAccuracyTrend planningAccuracyTrend) {

    public record ReleasesCompleted(List<TrendData> trend, int total) {}

    public record AverageReleaseDuration(List<TrendData> trend, double current) {}

    public record PlanningAccuracyTrend(List<TrendData> onTimeDelivery) {}

    public record TrendData(String period, double value) {}
}

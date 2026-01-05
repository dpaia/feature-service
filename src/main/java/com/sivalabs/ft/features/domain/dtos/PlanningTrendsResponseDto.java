package com.sivalabs.ft.features.domain.dtos;

import java.util.List;

public record PlanningTrendsResponseDto(
        ReleasesCompletedDto releasesCompleted,
        AverageReleaseDurationDto averageReleaseDuration,
        PlanningAccuracyTrendDto planningAccuracyTrend) {
    public static record ReleasesCompletedDto(List<TrendDataPointDto> trend, int total) {}

    public static record AverageReleaseDurationDto(List<TrendDataPointDto> trend, double current) {}

    public static record PlanningAccuracyTrendDto(List<TrendDataPointDto> onTimeDelivery) {}

    public static record TrendDataPointDto(String period, double value) {}
}

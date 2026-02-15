package com.sivalabs.ft.features.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ft.dashboard")
public record DashboardProperties(
        int decimalPlaces,
        int defaultFeatureDurationDays,
        int defaultTimelineEstimateDays,
        HealthThresholds healthThresholds,
        VelocityConfig velocity) {
    public record HealthThresholds(
            int highRiskBlockedFeatures,
            int mediumRiskBlockedFeatures,
            double highRiskCompletionPercentage,
            double mediumRiskCompletionPercentage,
            double delayedCompletionPercentage,
            int delayedEstimatedDays) {}

    public record VelocityConfig(int daysPerWeek, double minWeeksForCalculation) {}
}

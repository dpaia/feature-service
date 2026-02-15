package com.sivalabs.ft.features.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ft.planning")
public record PlanningProperties(
        int defaultCapacity,
        int draftOverdueDays,
        int draftCriticallyDelayedDays,
        int releaseOverdueDays,
        int onTimeDeliveryDays,
        double estimationAccuracyDefaultEffort,
        OverallocationThresholds overallocationThresholds) {
    public record OverallocationThresholds(double highThreshold, double mediumThreshold) {}
}

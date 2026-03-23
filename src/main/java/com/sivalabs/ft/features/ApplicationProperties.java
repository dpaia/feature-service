package com.sivalabs.ft.features;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ft")
public record ApplicationProperties(EventsProperties events, PlanningProperties planning) {

    public record EventsProperties(String newFeatures, String updatedFeatures, String deletedFeatures) {}

    public record PlanningProperties(
            int draftOverdueDays, int draftCriticallyDelayedDays, OverallocationThresholds overallocationThresholds) {}

    public record OverallocationThresholds(double mediumThreshold, double highThreshold) {}
}

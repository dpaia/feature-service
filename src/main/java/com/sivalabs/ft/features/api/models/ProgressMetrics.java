package com.sivalabs.ft.features.api.models;

public record ProgressMetrics(
        Integer totalFeatures,
        Integer completedFeatures,
        Integer inProgressFeatures,
        Integer newFeatures,
        Integer onHoldFeatures,
        Double completionPercentage) {}

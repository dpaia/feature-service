package com.sivalabs.ft.features.domain.dtos;

public record ProgressMetrics(
        int totalFeatures,
        int completedFeatures,
        int inProgressFeatures,
        int newFeatures,
        int onHoldFeatures,
        double completionPercentage) {}

package com.sivalabs.ft.features.domain.dtos;

import java.io.Serializable;

public record ProgressMetricsDto(
        int totalFeatures,
        int completedFeatures,
        int inProgressFeatures,
        int newFeatures,
        int onHoldFeatures,
        double completionPercentage)
        implements Serializable {}

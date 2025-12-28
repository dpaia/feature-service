package com.sivalabs.ft.features.domain.dtos;

import java.io.Serializable;

public record RoadmapSummaryDto(
        int totalReleases,
        int completedReleases,
        int draftReleases,
        int totalFeatures,
        double overallCompletionPercentage)
        implements Serializable {}

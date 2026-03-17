package com.sivalabs.ft.features.domain.dtos;

public record RoadmapSummary(
        int totalReleases,
        int completedReleases,
        int draftReleases,
        int totalFeatures,
        double overallCompletionPercentage) {}

package com.sivalabs.ft.features.api.models;

public record RoadmapSummary(
        Integer totalReleases,
        Integer completedReleases,
        Integer draftReleases,
        Integer totalFeatures,
        Double overallCompletionPercentage) {}

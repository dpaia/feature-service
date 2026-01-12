package com.sivalabs.ft.features.api.models;

import java.util.List;

public record RoadmapItem(
        RoadmapRelease release,
        ProgressMetrics progressMetrics,
        HealthIndicators healthIndicators,
        List<RoadmapFeature> features) {}

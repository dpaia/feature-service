package com.sivalabs.ft.features.api.models;

import java.util.List;

public record RoadmapItem(
        ProductInfo product,
        RoadmapRelease release,
        ProgressMetrics progressMetrics,
        HealthIndicators healthIndicators,
        List<RoadmapFeature> features) {}

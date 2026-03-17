package com.sivalabs.ft.features.domain.dtos;

import java.util.List;

public record RoadmapItem(
        ProductRef product,
        ReleaseDto release,
        ProgressMetrics progressMetrics,
        HealthIndicators healthIndicators,
        List<RoadmapFeatureDto> features) {}

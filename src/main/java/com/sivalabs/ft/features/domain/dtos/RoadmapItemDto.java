package com.sivalabs.ft.features.domain.dtos;

import java.io.Serializable;
import java.util.List;

public record RoadmapItemDto(
        ReleaseDto release,
        ProgressMetricsDto progressMetrics,
        HealthIndicatorsDto healthIndicators,
        List<FeatureDto> features)
        implements Serializable {}

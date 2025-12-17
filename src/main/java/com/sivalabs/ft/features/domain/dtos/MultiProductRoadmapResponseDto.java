package com.sivalabs.ft.features.domain.dtos;

import java.io.Serializable;
import java.util.List;

public record MultiProductRoadmapResponseDto(
        List<ProductRoadmapDto> products, RoadmapSummaryDto summary, RoadmapFilterDto appliedFilters)
        implements Serializable {}

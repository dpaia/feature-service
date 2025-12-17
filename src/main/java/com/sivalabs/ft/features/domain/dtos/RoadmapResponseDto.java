package com.sivalabs.ft.features.domain.dtos;

import java.io.Serializable;
import java.util.List;

public record RoadmapResponseDto(
        List<RoadmapItemDto> roadmapItems, RoadmapSummaryDto summary, RoadmapFilterDto appliedFilters)
        implements Serializable {}

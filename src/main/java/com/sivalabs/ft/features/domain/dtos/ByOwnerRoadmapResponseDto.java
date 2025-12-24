package com.sivalabs.ft.features.domain.dtos;

import java.io.Serializable;
import java.util.List;

public record ByOwnerRoadmapResponseDto(
        String owner, List<RoadmapItemDto> roadmapItems, RoadmapSummaryDto summary, RoadmapFilterDto appliedFilters)
        implements Serializable {}

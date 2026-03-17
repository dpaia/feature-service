package com.sivalabs.ft.features.domain.dtos;

import java.util.List;

public record RoadmapResponse(List<RoadmapItem> roadmapItems, RoadmapSummary summary, AppliedFilters appliedFilters) {}

package com.sivalabs.ft.features.api.models;

import java.util.List;

public record RoadmapResponse(List<RoadmapItem> roadmapItems, RoadmapSummary summary, AppliedFilters appliedFilters) {}

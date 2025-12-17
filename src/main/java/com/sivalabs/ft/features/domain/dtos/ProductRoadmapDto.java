package com.sivalabs.ft.features.domain.dtos;

import java.io.Serializable;
import java.util.List;

public record ProductRoadmapDto(
        Long productId, String productName, String productCode, List<RoadmapItemDto> roadmapItems)
        implements Serializable {}

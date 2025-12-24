package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.GroupByOption;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

public record RoadmapFilterDto(
        String productCode,
        List<String> productCodes,
        LocalDate startDate,
        LocalDate endDate,
        boolean includeCompleted,
        GroupByOption groupBy,
        String owner)
        implements Serializable {}

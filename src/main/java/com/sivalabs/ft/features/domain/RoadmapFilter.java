package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.LocalDate;
import java.util.List;

public record RoadmapFilter(
        List<String> productCodes,
        List<ReleaseStatus> statuses,
        LocalDate dateFrom,
        LocalDate dateTo,
        String groupBy,
        String owner) {}

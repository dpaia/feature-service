package com.sivalabs.ft.features.domain.dtos;

import java.time.LocalDate;
import java.util.List;

public record AppliedFilters(
        List<String> productCodes,
        List<String> statuses,
        LocalDate dateFrom,
        LocalDate dateTo,
        String groupBy,
        String owner) {}

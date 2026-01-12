package com.sivalabs.ft.features.api.models;

import java.time.LocalDate;

public record AppliedFilters(
        String[] productCodes, String[] statuses, LocalDate dateFrom, LocalDate dateTo, String groupBy, String owner) {}

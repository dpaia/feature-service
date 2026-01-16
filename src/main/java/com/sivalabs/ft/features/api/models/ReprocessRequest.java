package com.sivalabs.ft.features.api.models;

import java.time.Instant;
import java.util.List;

public record ReprocessRequest(List<Long> errorLogIds, DateRange dateRange, Boolean dryRun) {

    public record DateRange(Instant startDate, Instant endDate) {}
}

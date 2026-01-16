package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ErrorType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record HealthMetricsDto(
        Long totalEvents,
        Long failedEvents,
        Double successRate,
        Double errorRate,
        Double avgResponseTimeMs,
        Double p95LatencyMs,
        Instant lastEventTimestamp,
        Map<ErrorType, Long> errorsByType,
        List<DataGapDto> dataGaps) {}

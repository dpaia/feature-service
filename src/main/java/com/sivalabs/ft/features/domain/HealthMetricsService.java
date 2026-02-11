package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.HealthMetricsDto;
import com.sivalabs.ft.features.domain.models.ErrorType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthMetricsService {
    private final FeatureUsageRepository featureUsageRepository;
    private final ErrorLogRepository errorLogRepository;

    public HealthMetricsService(FeatureUsageRepository featureUsageRepository, ErrorLogRepository errorLogRepository) {
        this.featureUsageRepository = featureUsageRepository;
        this.errorLogRepository = errorLogRepository;
    }

    @Transactional(readOnly = true)
    public HealthMetricsDto getSystemHealth(Instant startDate, Instant endDate) {
        // Default to last 7 days if no dates provided
        if (startDate == null || endDate == null) {
            endDate = Instant.now();
            startDate = endDate.minus(7, ChronoUnit.DAYS);
        }

        // Get successful events from feature_usage
        long successfulEvents = featureUsageRepository.countByTimestampBetween(startDate, endDate);

        // Get failed events from error_log
        long failedEvents = errorLogRepository.countByTimestampBetween(startDate, endDate);

        // Total events = successful + failed (error_log contains events that did NOT make it to feature_usage)
        long totalEvents = successfulEvents + failedEvents;

        // Calculate rates
        double successRate = totalEvents > 0 ? (successfulEvents * 100.0 / totalEvents) : 100.0;
        double errorRate = totalEvents > 0 ? (failedEvents * 100.0 / totalEvents) : 0.0;

        // Get last event timestamp (from feature_usage)
        Instant lastEventTimestamp = featureUsageRepository.findLatestTimestamp();

        // Get errors by type
        Map<ErrorType, Long> errorsByType = new HashMap<>();
        for (ErrorType errorType : ErrorType.values()) {
            long count = errorLogRepository.countByErrorTypeAndTimestampBetween(errorType, startDate, endDate);
            if (count > 0) {
                errorsByType.put(errorType, count);
            }
        }

        return new HealthMetricsDto(
                totalEvents, failedEvents, successRate, errorRate, lastEventTimestamp, errorsByType);
    }
}

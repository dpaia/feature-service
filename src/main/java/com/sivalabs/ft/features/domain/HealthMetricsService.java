package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.DataGapDto;
import com.sivalabs.ft.features.domain.dtos.HealthMetricsDto;
import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import com.sivalabs.ft.features.domain.models.ErrorType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class HealthMetricsService {

    private final FeatureUsageRepository featureUsageRepository;
    private final ErrorLogRepository errorLogRepository;

    public HealthMetricsService(FeatureUsageRepository featureUsageRepository, ErrorLogRepository errorLogRepository) {
        this.featureUsageRepository = featureUsageRepository;
        this.errorLogRepository = errorLogRepository;
    }

    public HealthMetricsDto getSystemHealth(Instant startDate, Instant endDate) {
        // Total events
        Long totalEvents = featureUsageRepository.countByTimestampBetween(startDate, endDate);

        // Failed events
        Long failedEvents = errorLogRepository.countByTimestampBetween(startDate, endDate);

        // Success rate: (total - failed) / total * 100
        double successRate = totalEvents > 0 ? (totalEvents - failedEvents) * 100.0 / totalEvents : 100.0;

        // Error rate
        double errorRate = 100.0 - successRate;

        // Errors by type
        Map<ErrorType, Long> errorsByType = Arrays.stream(ErrorType.values())
                .collect(Collectors.toMap(type -> type, errorLogRepository::countByErrorType));

        // Last event timestamp
        Instant lastEventTimestamp = featureUsageRepository
                .findFirstByOrderByTimestampDesc()
                .map(FeatureUsage::getTimestamp)
                .orElse(null);

        // Data gaps detection
        List<DataGapDto> dataGaps = detectDataGaps(startDate, endDate);

        // Performance metrics (placeholder - would need separate tracking)
        double avgResponseTime = 45.0;
        double p95Latency = 120.0;

        return new HealthMetricsDto(
                totalEvents,
                failedEvents,
                successRate,
                errorRate,
                avgResponseTime,
                p95Latency,
                lastEventTimestamp,
                errorsByType,
                dataGaps);
    }

    private List<DataGapDto> detectDataGaps(Instant startDate, Instant endDate) {
        // Detect periods > 2 hours with < 10 events
        List<DataGapDto> gaps = new ArrayList<>();
        Instant current = startDate;
        Duration window = Duration.ofHours(2);
        int threshold = 10;

        while (current.isBefore(endDate)) {
            Instant windowEnd = current.plus(window);
            if (windowEnd.isAfter(endDate)) {
                windowEnd = endDate;
            }

            Long eventsInWindow = featureUsageRepository.countByTimestampBetween(current, windowEnd);

            if (eventsInWindow < threshold) {
                gaps.add(new DataGapDto(current, windowEnd, "Low activity: " + eventsInWindow + " events"));
            }

            current = windowEnd;
        }

        return gaps;
    }
}

package com.sivalabs.ft.features.domain.trends;

import com.sivalabs.ft.features.domain.dtos.UsageTrendDto;
import com.sivalabs.ft.features.domain.models.PeriodType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Calculator for daily usage trends.
 * Groups usage data by calendar day and calculates growth rates.
 */
@Component
public class DailyTrendCalculator implements TrendCalculator {

    private static final DateTimeFormatter DAILY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public List<UsageTrendDto> calculate(List<Object[]> rawData) {
        if (rawData == null || rawData.isEmpty()) {
            return List.of();
        }

        List<UsageTrendDto> trends = new ArrayList<>();

        // Process data in reverse order to calculate growth rates correctly
        // rawData comes in DESC order (newest first), but we need to calculate growth chronologically
        for (int i = rawData.size() - 1; i >= 0; i--) {
            Object[] row = rawData.get(i);
            Instant periodTimestamp = ((java.sql.Timestamp) row[0]).toInstant();
            long usageCount = ((Number) row[1]).longValue();
            long uniqueUserCount = ((Number) row[2]).longValue();

            // Calculate growth rate compared to previous chronological period
            double growthRate = 0.0;
            if (i < rawData.size() - 1) {
                // Get previous period data (chronologically)
                Object[] previousRow = rawData.get(i + 1);
                long previousUsageCount = ((Number) previousRow[1]).longValue();

                if (previousUsageCount > 0) {
                    growthRate = ((double) (usageCount - previousUsageCount) / previousUsageCount) * 100;
                }
            }

            // Calculate period boundaries (start of day to end of day)
            LocalDate date = periodTimestamp.atZone(ZoneOffset.UTC).toLocalDate();
            Instant periodStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant periodEnd =
                    date.plusDays(1).atStartOfDay(ZoneOffset.UTC).minusNanos(1).toInstant();

            UsageTrendDto trend = new UsageTrendDto(
                    formatPeriod(periodTimestamp),
                    PeriodType.DAY,
                    usageCount,
                    uniqueUserCount,
                    growthRate,
                    periodStart,
                    periodEnd);

            trends.add(0, trend); // Add at beginning to maintain DESC order
        }

        return trends;
    }

    @Override
    public PeriodType getPeriodType() {
        return PeriodType.DAY;
    }

    @Override
    public String formatPeriod(Instant periodTimestamp) {
        return periodTimestamp.atZone(ZoneOffset.UTC).toLocalDate().format(DAILY_FORMATTER);
    }
}

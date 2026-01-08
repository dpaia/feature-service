package com.sivalabs.ft.features.domain.trends;

import com.sivalabs.ft.features.domain.dtos.UsageTrendDto;
import com.sivalabs.ft.features.domain.models.PeriodType;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Calculator for monthly usage trends.
 * Groups usage data by calendar month and calculates growth rates.
 */
@Component
public class MonthlyTrendCalculator implements TrendCalculator {

    private static final DateTimeFormatter MONTHLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public List<UsageTrendDto> calculate(List<Object[]> rawData) {
        if (rawData == null || rawData.isEmpty()) {
            return List.of();
        }

        List<UsageTrendDto> trends = new ArrayList<>();
        UsageTrendDto previousTrend = null;

        for (Object[] row : rawData) {
            Instant periodTimestamp = ((java.sql.Timestamp) row[0]).toInstant();
            long usageCount = ((Number) row[1]).longValue();
            long uniqueUserCount = ((Number) row[2]).longValue();

            // Calculate growth rate compared to previous period
            double growthRate = 0.0;
            if (previousTrend != null && previousTrend.usageCount() > 0) {
                growthRate = ((double) (usageCount - previousTrend.usageCount()) / previousTrend.usageCount()) * 100;
            }

            // Calculate month boundaries (first day to last day of month)
            var zonedDateTime = periodTimestamp.atZone(ZoneOffset.UTC);
            YearMonth yearMonth = YearMonth.from(zonedDateTime);
            Instant periodStart =
                    yearMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant periodEnd =
                    yearMonth.atEndOfMonth().atTime(23, 59, 59, 999999999).toInstant(ZoneOffset.UTC);

            UsageTrendDto trend = new UsageTrendDto(
                    formatPeriod(periodTimestamp),
                    PeriodType.MONTH,
                    usageCount,
                    uniqueUserCount,
                    growthRate,
                    periodStart,
                    periodEnd);

            trends.add(trend);
            previousTrend = trend;
        }

        return trends;
    }

    @Override
    public PeriodType getPeriodType() {
        return PeriodType.MONTH;
    }

    @Override
    public String formatPeriod(Instant periodTimestamp) {
        return periodTimestamp.atZone(ZoneOffset.UTC).format(MONTHLY_FORMATTER);
    }
}

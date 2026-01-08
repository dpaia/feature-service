package com.sivalabs.ft.features.domain.trends;

import com.sivalabs.ft.features.domain.dtos.UsageTrendDto;
import com.sivalabs.ft.features.domain.models.PeriodType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Calculator for weekly usage trends.
 * Groups usage data by ISO-8601 week (Monday to Sunday) and calculates growth rates.
 */
@Component
public class WeeklyTrendCalculator implements TrendCalculator {

    private static final DateTimeFormatter WEEKLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-'W'ww");

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

            // Calculate week boundaries (Monday to Sunday)
            var zonedDateTime = periodTimestamp.atZone(ZoneOffset.UTC);
            var weekStart = zonedDateTime.with(TemporalAdjusters.previousOrSame(
                    WeekFields.of(Locale.getDefault()).getFirstDayOfWeek()));
            var weekEnd = weekStart
                    .plusDays(6)
                    .withHour(23)
                    .withMinute(59)
                    .withSecond(59)
                    .withNano(999999999);

            UsageTrendDto trend = new UsageTrendDto(
                    formatPeriod(periodTimestamp),
                    PeriodType.WEEK,
                    usageCount,
                    uniqueUserCount,
                    growthRate,
                    weekStart.toInstant(),
                    weekEnd.toInstant());

            trends.add(trend);
            previousTrend = trend;
        }

        return trends;
    }

    @Override
    public PeriodType getPeriodType() {
        return PeriodType.WEEK;
    }

    @Override
    public String formatPeriod(Instant periodTimestamp) {
        var zonedDateTime = periodTimestamp.atZone(ZoneOffset.UTC);
        int year = zonedDateTime.getYear();
        int week = zonedDateTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
    }
}

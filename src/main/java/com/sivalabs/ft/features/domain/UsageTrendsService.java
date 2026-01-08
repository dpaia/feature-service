package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.TrendDataDto;
import com.sivalabs.ft.features.domain.dtos.TrendSummaryDto;
import com.sivalabs.ft.features.domain.dtos.UsageTrendDto;
import com.sivalabs.ft.features.domain.models.ActionType;
import com.sivalabs.ft.features.domain.models.PeriodType;
import com.sivalabs.ft.features.domain.models.TrendDirection;
import com.sivalabs.ft.features.domain.trends.TrendCalculator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for calculating usage trends over time.
 * Supports daily, weekly, and monthly trend analysis with growth rate calculations.
 */
@Service
public class UsageTrendsService {
    private static final Logger log = LoggerFactory.getLogger(UsageTrendsService.class);

    private final FeatureUsageRepository featureUsageRepository;
    private final Map<PeriodType, TrendCalculator> calculators;

    @Autowired
    public UsageTrendsService(FeatureUsageRepository featureUsageRepository, List<TrendCalculator> calculatorList) {
        this.featureUsageRepository = featureUsageRepository;
        this.calculators =
                calculatorList.stream().collect(Collectors.toMap(TrendCalculator::getPeriodType, Function.identity()));

        log.info("Initialized UsageTrendsService with {} calculators: {}", calculators.size(), calculators.keySet());
    }

    /**
     * Calculate usage trends for the specified period type and filters.
     *
     * @param periodType Type of period grouping (DAILY, WEEKLY, MONTHLY)
     * @param featureCode Optional feature code filter
     * @param productCode Optional product code filter
     * @param actionType Optional action type filter
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @return TrendDataDto containing trends and summary
     */
    public TrendDataDto calculateTrends(
            PeriodType periodType,
            String featureCode,
            String productCode,
            ActionType actionType,
            Instant startDate,
            Instant endDate) {

        log.debug(
                "Calculating trends: periodType={}, featureCode={}, productCode={}, actionType={}, startDate={}, endDate={}",
                periodType,
                featureCode,
                productCode,
                actionType,
                startDate,
                endDate);

        // Get calculator for the specified period type
        TrendCalculator calculator = calculators.get(periodType);
        if (calculator == null) {
            throw new IllegalArgumentException("No calculator found for period type: " + periodType);
        }

        // Get raw trend data from repository
        List<Object[]> rawTrends;
        String sqlPeriodType = periodType.name().toLowerCase();
        log.info(
                "SQL parameters: periodType={}, featureCode={}, productCode={}, actionType={}, startDate={}, endDate={}",
                sqlPeriodType,
                featureCode,
                productCode,
                actionType,
                startDate,
                endDate);

        String actionTypeString = actionType != null ? actionType.name() : null;
        log.info(
                "Before repository call: featureCode={}, productCode={}, actionTypeString={}",
                featureCode,
                productCode,
                actionTypeString);

        if (featureCode == null && productCode == null) {
            // Use optimized overall trends query
            log.info("Using findOverallUsageTrends");
            rawTrends =
                    featureUsageRepository.findOverallUsageTrends(sqlPeriodType, actionTypeString, startDate, endDate);
        } else {
            // Use filtered trends query
            log.info("Using findUsageTrends with featureCode={}, productCode={}", featureCode, productCode);
            rawTrends = featureUsageRepository.findUsageTrends(
                    sqlPeriodType, featureCode, productCode, actionTypeString, startDate, endDate);
        }

        log.info(
                "Retrieved {} raw trend data points for periodType={}, featureCode={}, productCode={}, actionType={}",
                rawTrends.size(),
                periodType,
                featureCode,
                productCode,
                actionType);

        // Log raw data for debugging
        for (int i = 0; i < rawTrends.size(); i++) {
            Object[] row = rawTrends.get(i);
            log.info("Raw trend data [{}]: period={}, usageCount={}, uniqueUserCount={}", i, row[0], row[1], row[2]);
        }

        // Calculate trends using appropriate calculator
        List<UsageTrendDto> trends = calculator.calculate(rawTrends);

        // Calculate summary statistics
        TrendSummaryDto summary = calculateSummary(trends);

        // Determine entity information
        String entityCode = determineEntityCode(featureCode, productCode);
        String entityType = determineEntityType(featureCode, productCode);

        return new TrendDataDto(entityCode, entityType, trends, summary);
    }

    /**
     * Calculate summary statistics for a list of trends.
     */
    private TrendSummaryDto calculateSummary(List<UsageTrendDto> trends) {
        if (trends.isEmpty()) {
            return new TrendSummaryDto(0L, 0.0, 0.0, TrendDirection.STABLE);
        }

        // Calculate total usage
        long totalUsage = trends.stream().mapToLong(UsageTrendDto::usageCount).sum();

        // Calculate average usage per period
        double averageUsagePerPeriod = (double) totalUsage / trends.size();

        // Calculate overall growth rate (first to last period)
        double overallGrowthRate = 0.0;
        if (trends.size() >= 2) {
            UsageTrendDto firstPeriod = trends.get(trends.size() - 1); // Last in DESC order
            UsageTrendDto lastPeriod = trends.get(0); // First in DESC order

            if (firstPeriod.usageCount() > 0) {
                overallGrowthRate =
                        ((double) (lastPeriod.usageCount() - firstPeriod.usageCount()) / firstPeriod.usageCount())
                                * 100;
            }
        }

        // Determine trend direction
        TrendDirection trendDirection = determineTrendDirection(trends);

        return new TrendSummaryDto(totalUsage, averageUsagePerPeriod, overallGrowthRate, trendDirection);
    }

    /**
     * Determine the overall trend direction based on growth rates.
     */
    private TrendDirection determineTrendDirection(List<UsageTrendDto> trends) {
        if (trends.size() < 2) {
            return TrendDirection.STABLE;
        }

        // Count positive and negative growth rates
        long positiveGrowthCount = trends.stream()
                .mapToDouble(UsageTrendDto::growthRate)
                .filter(rate -> rate > 5.0) // Consider > 5% as significant growth
                .count();

        long negativeGrowthCount = trends.stream()
                .mapToDouble(UsageTrendDto::growthRate)
                .filter(rate -> rate < -5.0) // Consider < -5% as significant decline
                .count();

        // Determine direction based on majority
        if (positiveGrowthCount > negativeGrowthCount) {
            return TrendDirection.INCREASING;
        } else if (negativeGrowthCount > positiveGrowthCount) {
            return TrendDirection.DECREASING;
        } else {
            return TrendDirection.STABLE;
        }
    }

    /**
     * Calculate growth rate between two values.
     */
    public double calculateGrowthRate(long currentValue, long previousValue) {
        if (previousValue == 0) {
            return currentValue > 0 ? 100.0 : 0.0; // 100% growth from zero, or 0% if both zero
        }
        return ((double) (currentValue - previousValue) / previousValue) * 100;
    }

    /**
     * Determine entity code for the trend data.
     */
    private String determineEntityCode(String featureCode, String productCode) {
        if (featureCode != null) {
            return featureCode;
        } else if (productCode != null) {
            return productCode;
        } else {
            return "overall";
        }
    }

    /**
     * Determine entity type for the trend data.
     */
    private String determineEntityType(String featureCode, String productCode) {
        if (featureCode != null) {
            return "FEATURE";
        } else if (productCode != null) {
            return "PRODUCT";
        } else {
            return "OVERALL";
        }
    }
}

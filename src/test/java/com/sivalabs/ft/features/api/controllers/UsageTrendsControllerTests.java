package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for UsageTrendsController.
 * Tests all trend calculation scenarios with proper data setup and validation.
 */
class UsageTrendsControllerTests extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM feature_usage");
    }

    /**
     * Tests for unauthenticated requests - should return 401 Unauthorized
     */
    @Nested
    class UnauthenticatedTests {

        @Test
        void shouldReturn401ForUnauthenticatedTrendsRequest() {
            var result = mvc.get().uri("/api/usage/trends?periodType=DAY").exchange();

            assertThat(result).hasStatus(401);
        }
    }

    /**
     * Tests for authenticated users
     */
    @Nested
    @WithMockOAuth2User(roles = {"USER"})
    class AuthenticatedUserTests {

        @Test
        void shouldGetDailyTrendsWithCorrectStructure() {
            // Create test data across multiple days
            createUsageEventForDate(
                    "FEATURE_VIEWED",
                    "TREND-FEATURE",
                    "TREND-PRODUCT",
                    Instant.now().minus(2, ChronoUnit.DAYS));
            createUsageEventForDate(
                    "FEATURE_VIEWED",
                    "TREND-FEATURE",
                    "TREND-PRODUCT",
                    Instant.now().minus(1, ChronoUnit.DAYS));
            createUsageEventForDate("FEATURE_VIEWED", "TREND-FEATURE", "TREND-PRODUCT", Instant.now());

            var result = mvc.get()
                    .uri("/api/usage/trends?periodType=DAY&featureCode=TREND-FEATURE")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify response structure
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.entityCode")
                    .asString()
                    .isEqualTo("TREND-FEATURE");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.entityType")
                    .asString()
                    .isEqualTo("FEATURE");
            assertThat(result).bodyJson().extractingPath("$.trends").asList().isNotEmpty();
            assertThat(result).bodyJson().extractingPath("$.summary").isNotNull();

            // Verify trend data structure
            assertThat(result).bodyJson().extractingPath("$.trends[0].period").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.trends[0].periodType")
                    .asString()
                    .isEqualTo("DAY");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.trends[0].usageCount")
                    .asNumber()
                    .satisfies(count -> assertThat(count.intValue()).isGreaterThan(0));
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.trends[0].uniqueUserCount")
                    .asNumber()
                    .satisfies(count -> assertThat(count.intValue()).isGreaterThan(0));
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.trends[0].growthRate")
                    .isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.trends[0].periodStart")
                    .isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.trends[0].periodEnd")
                    .isNotNull();

            // Verify summary structure
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.summary.totalUsage")
                    .asNumber()
                    .isEqualTo(3);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.summary.averageUsagePerPeriod")
                    .isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.summary.overallGrowthRate")
                    .isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.summary.trendDirection")
                    .isNotNull();
        }

        @Test
        void shouldGetWeeklyTrendsWithCorrectPeriodFormat() {
            // Create test data
            createUsageEventForDate(
                    "FEATURE_VIEWED",
                    "WEEKLY-FEATURE",
                    "TEST-PRODUCT",
                    Instant.now().minus(14, ChronoUnit.DAYS));
            createUsageEventForDate(
                    "FEATURE_VIEWED",
                    "WEEKLY-FEATURE",
                    "TEST-PRODUCT",
                    Instant.now().minus(7, ChronoUnit.DAYS));

            var result = mvc.get()
                    .uri("/api/usage/trends?periodType=WEEK&featureCode=WEEKLY-FEATURE")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify weekly period format (e.g., "2024-W01")
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.trends[0].period")
                    .asString()
                    .matches("\\d{4}-W\\d{2}");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.trends[0].periodType")
                    .asString()
                    .isEqualTo("WEEK");
        }

        @Test
        void shouldGetMonthlyTrendsWithCorrectPeriodFormat() {
            // Create test data
            createUsageEventForDate(
                    "FEATURE_VIEWED",
                    "MONTHLY-FEATURE",
                    "TEST-PRODUCT",
                    Instant.now().minus(60, ChronoUnit.DAYS));
            createUsageEventForDate(
                    "FEATURE_VIEWED",
                    "MONTHLY-FEATURE",
                    "TEST-PRODUCT",
                    Instant.now().minus(30, ChronoUnit.DAYS));

            var result = mvc.get()
                    .uri("/api/usage/trends?periodType=MONTH&featureCode=MONTHLY-FEATURE")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify monthly period format (e.g., "2024-01")
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.trends[0].period")
                    .asString()
                    .matches("\\d{4}-\\d{2}");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.trends[0].periodType")
                    .asString()
                    .isEqualTo("MONTH");
        }

        @Test
        void shouldGetOverallTrendsWhenNoFiltersProvided() {
            // Create test data for overall trends
            createUsageEventForDate(
                    "FEATURE_VIEWED",
                    "OVERALL-FEATURE-1",
                    "PRODUCT-1",
                    Instant.now().minus(1, ChronoUnit.DAYS));
            createUsageEventForDate("FEATURE_VIEWED", "OVERALL-FEATURE-2", "PRODUCT-2", Instant.now());

            var result = mvc.get().uri("/api/usage/trends?periodType=DAY").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify overall entity type
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.entityCode")
                    .asString()
                    .isEqualTo("overall");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.entityType")
                    .asString()
                    .isEqualTo("OVERALL");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.summary.totalUsage")
                    .asNumber()
                    .isEqualTo(2);
        }

        @Test
        void shouldGetProductTrendsWhenProductCodeProvided() {
            // Create test data for product trends
            createUsageEventForDate(
                    "FEATURE_VIEWED", "FEAT-1", "PRODUCT-TRENDS", Instant.now().minus(1, ChronoUnit.DAYS));
            createUsageEventForDate("FEATURE_VIEWED", "FEAT-2", "PRODUCT-TRENDS", Instant.now());

            var result = mvc.get()
                    .uri("/api/usage/trends?periodType=DAY&productCode=PRODUCT-TRENDS")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify product entity type
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.entityCode")
                    .asString()
                    .isEqualTo("PRODUCT-TRENDS");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.entityType")
                    .asString()
                    .isEqualTo("PRODUCT");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.summary.totalUsage")
                    .asNumber()
                    .isEqualTo(2);
        }

        @Test
        void shouldCalculateGrowthRateCorrectly() {
            // Create test data with known growth pattern
            // Day 1: 1 event, Day 2: 2 events (100% growth), Day 3: 3 events (50% growth)
            Instant day1 = Instant.now().minus(2, ChronoUnit.DAYS);
            Instant day2 = Instant.now().minus(1, ChronoUnit.DAYS);
            Instant day3 = Instant.now();

            // Day 1: 1 event
            createUsageEventForDate("FEATURE_VIEWED", "GROWTH-FEATURE", "TEST-PRODUCT", day1);

            // Day 2: 2 events
            createUsageEventForDate("FEATURE_VIEWED", "GROWTH-FEATURE", "TEST-PRODUCT", day2);
            createUsageEventForDate("FEATURE_VIEWED", "GROWTH-FEATURE", "TEST-PRODUCT", day2);

            // Day 3: 3 events
            createUsageEventForDate("FEATURE_VIEWED", "GROWTH-FEATURE", "TEST-PRODUCT", day3);
            createUsageEventForDate("FEATURE_VIEWED", "GROWTH-FEATURE", "TEST-PRODUCT", day3);
            createUsageEventForDate("FEATURE_VIEWED", "GROWTH-FEATURE", "TEST-PRODUCT", day3);

            var result = mvc.get()
                    .uri("/api/usage/trends?periodType=DAY&featureCode=GROWTH-FEATURE")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify we have 3 trend points
            assertThat(result).bodyJson().extractingPath("$.trends").asList().hasSize(3);

            // Verify growth rates (trends are ordered DESC by date)
            // Most recent (day 3): should have positive growth rate
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.trends[0].usageCount")
                    .asNumber()
                    .isEqualTo(3);

            // Middle (day 2): should have positive growth rate
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.trends[1].usageCount")
                    .asNumber()
                    .isEqualTo(2);

            // Oldest (day 1): should have 0 growth rate (first period)
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.trends[2].usageCount")
                    .asNumber()
                    .isEqualTo(1);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.trends[2].growthRate")
                    .asNumber()
                    .isEqualTo(0.0);
        }

        @Test
        void shouldHandleDateRangeFiltering() {
            // Create events outside and inside date range
            Instant outsideRange = Instant.now().minus(10, ChronoUnit.DAYS);
            Instant insideRange = Instant.now().minus(1, ChronoUnit.DAYS);

            createUsageEventForDate("FEATURE_VIEWED", "DATE-FILTER-FEATURE", "TEST-PRODUCT", outsideRange);
            createUsageEventForDate("FEATURE_VIEWED", "DATE-FILTER-FEATURE", "TEST-PRODUCT", insideRange);

            // Query with date range that excludes the older event
            Instant startDate = Instant.now().minus(5, ChronoUnit.DAYS);
            Instant endDate = Instant.now();

            var result = mvc.get()
                    .uri("/api/usage/trends?periodType=DAY&featureCode=DATE-FILTER-FEATURE" + "&startDate="
                            + startDate.toString() + "&endDate=" + endDate.toString())
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should only include events within date range
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.summary.totalUsage")
                    .asNumber()
                    .isEqualTo(1);
        }

        @Test
        void shouldHandleActionTypeFiltering() {
            // Create events with different action types
            createUsageEventForDate(
                    "FEATURE_VIEWED",
                    "ACTION-FILTER-FEATURE",
                    "TEST-PRODUCT",
                    Instant.now().minus(1, ChronoUnit.DAYS));
            createUsageEventForDate(
                    "FEATURE_UPDATED",
                    "ACTION-FILTER-FEATURE",
                    "TEST-PRODUCT",
                    Instant.now().minus(1, ChronoUnit.DAYS));
            createUsageEventForDate("FEATURE_VIEWED", "ACTION-FILTER-FEATURE", "TEST-PRODUCT", Instant.now());

            // Verify data was created
            int totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feature_usage", Integer.class);
            System.out.println("Total events in database: " + totalCount);

            // Show all data in database
            jdbcTemplate.query(
                    "SELECT id, user_id, feature_code, product_code, action_type, timestamp FROM feature_usage",
                    (rs, rowNum) -> {
                        System.out.println(String.format(
                                "Row %d: id=%d, user_id=%s, feature_code=%s, product_code=%s, action_type=%s, timestamp=%s",
                                rowNum,
                                rs.getLong("id"),
                                rs.getString("user_id"),
                                rs.getString("feature_code"),
                                rs.getString("product_code"),
                                rs.getString("action_type"),
                                rs.getTimestamp("timestamp")));
                        return null;
                    });

            int count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM feature_usage WHERE feature_code = ? AND action_type = ?",
                    Integer.class,
                    "ACTION-FILTER-FEATURE",
                    "FEATURE_VIEWED");
            System.out.println("Created FEATURE_VIEWED events: " + count);

            var result = mvc.get()
                    .uri("/api/usage/trends?periodType=DAY&featureCode=ACTION-FILTER-FEATURE&actionType=FEATURE_VIEWED")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should only include FEATURE_VIEWED events (2 total)
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.summary.totalUsage")
                    .asNumber()
                    .isEqualTo(2);
        }

        @Test
        void shouldReturnEmptyTrendsForNonExistentFeature() {
            var result = mvc.get()
                    .uri("/api/usage/trends?periodType=DAY&featureCode=NON-EXISTENT")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify empty trends structure
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.entityCode")
                    .asString()
                    .isEqualTo("NON-EXISTENT");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.entityType")
                    .asString()
                    .isEqualTo("FEATURE");
            assertThat(result).bodyJson().extractingPath("$.trends").asList().isEmpty();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.summary.totalUsage")
                    .asNumber()
                    .isEqualTo(0);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.summary.averageUsagePerPeriod")
                    .asNumber()
                    .isEqualTo(0.0);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.summary.overallGrowthRate")
                    .asNumber()
                    .isEqualTo(0.0);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.summary.trendDirection")
                    .asString()
                    .isEqualTo("STABLE");
        }

        @Test
        void shouldRejectRequestWithoutPeriodType() {
            var result = mvc.get().uri("/api/usage/trends").exchange();

            assertThat(result).hasStatus4xxClientError();
        }

        @Test
        void shouldRejectRequestWithInvalidPeriodType() {
            var result = mvc.get().uri("/api/usage/trends?periodType=INVALID").exchange();

            assertThat(result).hasStatus4xxClientError();
        }

        @Test
        void shouldRejectRequestWithInvalidDateFormat() {
            var result = mvc.get()
                    .uri("/api/usage/trends?periodType=DAY&startDate=invalid-date")
                    .exchange();

            assertThat(result).hasStatus4xxClientError();
        }

        @Test
        void shouldRejectRequestWithInvalidDateRange() {
            Instant startDate = Instant.now();
            Instant endDate = Instant.now().minus(1, ChronoUnit.DAYS); // End before start

            var result = mvc.get()
                    .uri("/api/usage/trends?periodType=DAY&startDate=" + startDate.toString() + "&endDate="
                            + endDate.toString())
                    .exchange();

            assertThat(result).hasStatus4xxClientError();
        }

        @Test
        void shouldCalculateTrendDirectionCorrectly() {
            // Create increasing trend: 1, 2, 4 events per day
            Instant day1 = Instant.now().minus(2, ChronoUnit.DAYS);
            Instant day2 = Instant.now().minus(1, ChronoUnit.DAYS);
            Instant day3 = Instant.now();

            // Day 1: 1 event
            createUsageEventForDate("FEATURE_VIEWED", "DIRECTION-FEATURE", "TEST-PRODUCT", day1);

            // Day 2: 2 events
            createUsageEventForDate("FEATURE_VIEWED", "DIRECTION-FEATURE", "TEST-PRODUCT", day2);
            createUsageEventForDate("FEATURE_VIEWED", "DIRECTION-FEATURE", "TEST-PRODUCT", day2);

            // Day 3: 4 events
            for (int i = 0; i < 4; i++) {
                createUsageEventForDate("FEATURE_VIEWED", "DIRECTION-FEATURE", "TEST-PRODUCT", day3);
            }

            var result = mvc.get()
                    .uri("/api/usage/trends?periodType=DAY&featureCode=DIRECTION-FEATURE")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should detect increasing trend
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.summary.trendDirection")
                    .asString()
                    .isEqualTo("INCREASING");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.summary.totalUsage")
                    .asNumber()
                    .isEqualTo(7);
        }

        @Test
        void shouldHandleMultipleFiltersSimultaneously() {
            // Create test data with multiple dimensions
            Instant now = Instant.now();
            createUsageEventForDate("FEATURE_VIEWED", "MULTI-FEATURE", "MULTI-PRODUCT", now.minus(1, ChronoUnit.DAYS));
            createUsageEventForDate("FEATURE_UPDATED", "MULTI-FEATURE", "MULTI-PRODUCT", now.minus(1, ChronoUnit.DAYS));
            createUsageEventForDate("FEATURE_VIEWED", "MULTI-FEATURE", "MULTI-PRODUCT", now);

            Instant startDate = now.minus(2, ChronoUnit.DAYS);
            Instant endDate = now.plus(1, ChronoUnit.DAYS); // Add buffer to include all events

            var result = mvc.get()
                    .uri("/api/usage/trends?periodType=DAY&featureCode=MULTI-FEATURE"
                            + "&productCode=MULTI-PRODUCT&actionType=FEATURE_VIEWED"
                            + "&startDate="
                            + startDate.toString() + "&endDate=" + endDate.toString())
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should only include FEATURE_VIEWED events (2 total)
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.summary.totalUsage")
                    .asNumber()
                    .isEqualTo(2);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.entityCode")
                    .asString()
                    .isEqualTo("MULTI-FEATURE");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.entityType")
                    .asString()
                    .isEqualTo("FEATURE");
        }

        @Test
        void shouldPrioritizeFeatureCodeOverProductCode() {
            // When both featureCode and productCode are provided, should use feature
            createUsageEventForDate("FEATURE_VIEWED", "PRIORITY-FEATURE", "PRIORITY-PRODUCT", Instant.now());

            var result = mvc.get()
                    .uri("/api/usage/trends?periodType=DAY&featureCode=PRIORITY-FEATURE&productCode=PRIORITY-PRODUCT")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should use feature as entity
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.entityCode")
                    .asString()
                    .isEqualTo("PRIORITY-FEATURE");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.entityType")
                    .asString()
                    .isEqualTo("FEATURE");
        }

        /**
         * Helper method to create usage events with specific timestamps.
         */
        private void createUsageEventForDate(
                String actionType, String featureCode, String productCode, Instant timestamp) {
            // Insert directly into database with specific timestamp
            jdbcTemplate.update(
                    "INSERT INTO feature_usage (user_id, feature_code, product_code, action_type, timestamp) VALUES (?, ?, ?, ?, ?)",
                    "user",
                    featureCode,
                    productCode,
                    actionType,
                    java.sql.Timestamp.from(timestamp));
        }
    }
}

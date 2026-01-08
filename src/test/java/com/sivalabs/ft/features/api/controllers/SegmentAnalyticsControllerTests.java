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
 * Integration tests for SegmentAnalyticsController.
 * Tests segment analytics, custom tag filtering, and authorization.
 */
class SegmentAnalyticsControllerTests extends AbstractIT {

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
        void shouldReturn401ForUnauthenticatedSegmentsRequest() {
            var result = mvc.get().uri("/api/usage/segments").exchange();
            assertThat(result).hasStatus(401);
        }

        @Test
        void shouldReturn401ForUnauthenticatedPredefinedSegmentsRequest() {
            var result = mvc.get().uri("/api/usage/segments/predefined").exchange();
            assertThat(result).hasStatus(401);
        }
    }

    /**
     * Tests for regular USER role - should return 403 Forbidden
     */
    @Nested
    @WithMockOAuth2User(roles = {"USER"})
    class UnauthorizedUserTests {

        @Test
        void shouldReturn403ForRegularUserSegmentsRequest() {
            var result = mvc.get().uri("/api/usage/segments").exchange();
            assertThat(result).hasStatus(403);
        }

        @Test
        void shouldReturn403ForRegularUserPredefinedSegments() {
            var result = mvc.get().uri("/api/usage/segments/predefined").exchange();
            assertThat(result).hasStatus(403);
        }
    }

    /**
     * Tests for ADMIN role - should have full access
     */
    @Nested
    @WithMockOAuth2User(roles = {"ADMIN"})
    class AdminUserTests {

        @Test
        void shouldGetAllPredefinedSegments() {
            var result = mvc.get().uri("/api/usage/segments/predefined").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should return exactly 4 predefined segments
            assertThat(result).bodyJson().extractingPath("$").asList().hasSize(4);

            // Verify segment structure
            assertThat(result).bodyJson().extractingPath("$[0].segmentId").isNotNull();
            assertThat(result).bodyJson().extractingPath("$[0].segmentName").isNotNull();
            assertThat(result).bodyJson().extractingPath("$[0].description").isNotNull();
            assertThat(result).bodyJson().extractingPath("$[0].criteria").isNotNull();
        }

        @Test
        void shouldGetSegmentAnalyticsForAllSegments() {
            // Create usage events with context tags
            createUsageEventWithContext("SEGMENT-FEAT-1", "{\"device\":\"mobile\"}");
            createUsageEventWithContext("SEGMENT-FEAT-2", "{\"device\":\"desktop\"}");
            createUsageEventWithContext("SEGMENT-FEAT-3", "{\"device\":\"mobile\"}");

            var result = mvc.get().uri("/api/usage/segments").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should return analytics for all 4 predefined segments
            assertThat(result).bodyJson().extractingPath("$").asList().hasSize(4);

            // Verify analytics structure
            assertThat(result).bodyJson().extractingPath("$[0].segmentName").isNotNull();
            assertThat(result).bodyJson().extractingPath("$[0].segmentCriteria").isNotNull();
            assertThat(result).bodyJson().extractingPath("$[0].totalUsage").isNotNull();
            assertThat(result).bodyJson().extractingPath("$[0].uniqueUsers").isNotNull();
        }

        @Test
        void shouldGetSegmentAnalyticsForSpecificSegment() {
            // Create mobile usage events
            createUsageEventWithContext("MOBILE-FEAT-1", "{\"device\":\"mobile\"}");
            createUsageEventWithContext("MOBILE-FEAT-2", "{\"device\":\"mobile\"}");

            // Create desktop usage events
            createUsageEventWithContext("DESKTOP-FEAT-1", "{\"device\":\"desktop\"}");

            var result = mvc.get().uri("/api/usage/segments?segments=mobile").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should return analytics only for mobile segment
            assertThat(result).bodyJson().extractingPath("$").asList().hasSize(1);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].segmentName")
                    .asString()
                    .isEqualTo("Mobile Users");

            // Should have 2 mobile events
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].totalUsage")
                    .asNumber()
                    .isEqualTo(2);
        }

        @Test
        void shouldGetSegmentAnalyticsWithDateFilter() {
            Instant now = Instant.now();

            // Create events within range
            createUsageEventWithContextAndTime(
                    "IN-RANGE-FEAT", "{\"device\":\"mobile\"}", now.minus(5, ChronoUnit.DAYS));

            // Create events outside range
            createUsageEventWithContextAndTime(
                    "OUT-RANGE-FEAT", "{\"device\":\"mobile\"}", now.minus(30, ChronoUnit.DAYS));

            Instant startDate = now.minus(10, ChronoUnit.DAYS);
            Instant endDate = now;

            var result = mvc.get()
                    .uri("/api/usage/segments?segments=mobile&startDate=" + startDate.toString() + "&endDate="
                            + endDate.toString())
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should only include events within date range (1 event)
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].totalUsage")
                    .asNumber()
                    .isEqualTo(1);
        }

        @Test
        void shouldGetMultipleSpecificSegments() {
            // Create exactly 2 events for each segment
            createUsageEventWithContext("MOBILE-FEAT-1", "{\"device\":\"mobile\"}");
            createUsageEventWithContext("MOBILE-FEAT-2", "{\"device\":\"mobile\"}");
            createUsageEventWithContext("DESKTOP-FEAT-1", "{\"device\":\"desktop\"}");
            createUsageEventWithContext("DESKTOP-FEAT-2", "{\"device\":\"desktop\"}");

            // Request both mobile and desktop segments
            var result =
                    mvc.get().uri("/api/usage/segments?segments=mobile,desktop").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should return exactly 2 segments
            assertThat(result).bodyJson().extractingPath("$").asList().hasSize(2);

            // Each segment should have exactly 2 events
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].totalUsage")
                    .asNumber()
                    .isEqualTo(2);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[1].totalUsage")
                    .asNumber()
                    .isEqualTo(2);
        }

        @Test
        void shouldReturn400ForInvalidDateFormat() {
            var result = mvc.get()
                    .uri("/api/usage/segments?segments=mobile&startDate=invalid-date")
                    .exchange();

            assertThat(result).hasStatus4xxClientError();
        }

        @Test
        void shouldReturn400ForInvalidDateRange() {
            Instant startDate = Instant.now();
            Instant endDate = Instant.now().minus(1, ChronoUnit.DAYS);

            var result = mvc.get()
                    .uri("/api/usage/segments?segments=mobile&startDate=" + startDate.toString() + "&endDate="
                            + endDate.toString())
                    .exchange();

            assertThat(result).hasStatus4xxClientError();
        }

        @Test
        void shouldReturn400ForMalformedTagsJson() {
            // Use simple invalid string instead of JSON with braces to avoid URL issues
            var result =
                    mvc.get().uri("/api/usage/segments?tags=not-a-json-string").exchange();

            assertThat(result).hasStatus4xxClientError();
        }

        @Test
        void shouldReturnEmptyAnalyticsForUnmatchedSegment() {
            // Create only desktop events
            createUsageEventWithContext("DESKTOP-ONLY", "{\"device\":\"desktop\"}");

            // Request mobile segment analytics
            var result = mvc.get().uri("/api/usage/segments?segments=mobile").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should return analytics with zero counts
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].totalUsage")
                    .asNumber()
                    .isEqualTo(0);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].uniqueUsers")
                    .asNumber()
                    .isEqualTo(0);
        }

        @Test
        void shouldCalculateTopFeaturesPerSegment() {
            // Create mobile events for different features
            createUsageEventWithContext("MOBILE-FEAT-1", "{\"device\":\"mobile\"}");
            createUsageEventWithContext("MOBILE-FEAT-1", "{\"device\":\"mobile\"}");
            createUsageEventWithContext("MOBILE-FEAT-1", "{\"device\":\"mobile\"}"); // 3 times
            createUsageEventWithContext("MOBILE-FEAT-2", "{\"device\":\"mobile\"}");
            createUsageEventWithContext("MOBILE-FEAT-2", "{\"device\":\"mobile\"}"); // 2 times

            var result = mvc.get().uri("/api/usage/segments?segments=mobile").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should have top features list
            assertThat(result).bodyJson().extractingPath("$[0].topFeatures").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].topFeatures")
                    .asList()
                    .hasSize(2);

            // Top feature should be MOBILE-FEAT-1 (3 usages)
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].topFeatures[0].name")
                    .asString()
                    .isEqualTo("MOBILE-FEAT-1");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].topFeatures[0].count")
                    .asNumber()
                    .isEqualTo(3);
        }

        @Test
        void shouldCalculateUsageByActionType() {
            // Create events with different action types
            createUsageEventWithContextAndAction("ACTION-FEAT-1", "{\"device\":\"mobile\"}", "FEATURE_VIEWED");
            createUsageEventWithContextAndAction("ACTION-FEAT-2", "{\"device\":\"mobile\"}", "FEATURE_VIEWED");
            createUsageEventWithContextAndAction("ACTION-FEAT-3", "{\"device\":\"mobile\"}", "FEATURE_UPDATED");

            var result = mvc.get().uri("/api/usage/segments?segments=mobile").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should have usage by action type map
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].usageByActionType")
                    .isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].usageByActionType.FEATURE_VIEWED")
                    .asNumber()
                    .isEqualTo(2);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].usageByActionType.FEATURE_UPDATED")
                    .asNumber()
                    .isEqualTo(1);
        }

        /**
         * Helper: Create usage event with context tags.
         */
        private void createUsageEventWithContext(String featureCode, String context) {
            jdbcTemplate.update(
                    "INSERT INTO feature_usage (user_id, feature_code, action_type, context, timestamp) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    "test-user-" + System.nanoTime(),
                    featureCode,
                    "FEATURE_VIEWED",
                    context,
                    java.sql.Timestamp.from(Instant.now()));
        }

        /**
         * Helper: Create usage event with context and custom timestamp.
         */
        private void createUsageEventWithContextAndTime(String featureCode, String context, Instant timestamp) {
            jdbcTemplate.update(
                    "INSERT INTO feature_usage (user_id, feature_code, action_type, context, timestamp) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    "test-user-" + System.nanoTime(),
                    featureCode,
                    "FEATURE_VIEWED",
                    context,
                    java.sql.Timestamp.from(timestamp));
        }

        /**
         * Helper: Create usage event with context and action type.
         */
        private void createUsageEventWithContextAndAction(String featureCode, String context, String actionType) {
            jdbcTemplate.update(
                    "INSERT INTO feature_usage (user_id, feature_code, action_type, context, timestamp) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    "test-user-" + System.nanoTime(),
                    featureCode,
                    actionType,
                    context,
                    java.sql.Timestamp.from(Instant.now()));
        }
    }

    /**
     * Tests for PRODUCT_MANAGER role - should have access
     */
    @Nested
    @WithMockOAuth2User(roles = {"PRODUCT_MANAGER"})
    class ProductManagerTests {

        @Test
        void shouldAllowProductManagerToAccessSegments() {
            var result = mvc.get().uri("/api/usage/segments").exchange();
            assertThat(result).hasStatus2xxSuccessful();
        }

        @Test
        void shouldAllowProductManagerToAccessPredefinedSegments() {
            var result = mvc.get().uri("/api/usage/segments/predefined").exchange();
            assertThat(result).hasStatus2xxSuccessful();
        }
    }
}

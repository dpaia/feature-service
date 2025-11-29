package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Comprehensive tests for FeatureUsageController covering all requirements:
 * - Authentication and authorization
 * - API functionality with precise assertions
 * - Proper test isolation with database cleanup
 * - API-based test data creation instead of direct repository access
 */
class FeatureUsageControllerTests extends AbstractIT {

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
        void shouldReturn401ForUnauthenticatedPostUsageEvent() {
            var requestBody =
                    """
                    {
                        "actionType": "FEATURE_VIEWED",
                        "featureCode": "FEAT-001"
                    }
                    """;

            var result = mvc.post()
                    .uri("/api/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();

            assertThat(result).hasStatus(401);
        }

        @Test
        void shouldReturn401ForUnauthenticatedGetAllUsageEvents() {
            var result = mvc.get().uri("/api/usage/events").exchange();
            assertThat(result).hasStatus(401);
        }

        @Test
        void shouldReturn401ForUnauthenticatedGetUsageStats() {
            var result = mvc.get().uri("/api/usage/stats").exchange();
            assertThat(result).hasStatus(401);
        }

        @Test
        void shouldReturn401ForUnauthenticatedGetFeatureStats() {
            var result = mvc.get().uri("/api/usage/feature/FEAT-001/stats").exchange();
            assertThat(result).hasStatus(401);
        }

        @Test
        void shouldReturn401ForUnauthenticatedGetProductStats() {
            var result = mvc.get().uri("/api/usage/product/PROD-001/stats").exchange();
            assertThat(result).hasStatus(401);
        }

        @Test
        void shouldReturn401ForUnauthenticatedGetFeatureEvents() {
            var result = mvc.get().uri("/api/usage/feature/FEAT-001/events").exchange();
            assertThat(result).hasStatus(401);
        }

        @Test
        void shouldReturn401ForUnauthenticatedGetProductEvents() {
            var result = mvc.get().uri("/api/usage/product/PROD-001/events").exchange();
            assertThat(result).hasStatus(401);
        }

        @Test
        void shouldReturn401ForUnauthenticatedGetTopFeatures() {
            var result = mvc.get().uri("/api/usage/top-features").exchange();
            assertThat(result).hasStatus(401);
        }

        @Test
        void shouldReturn401ForUnauthenticatedGetTopUsers() {
            var result = mvc.get().uri("/api/usage/top-users").exchange();
            assertThat(result).hasStatus(401);
        }

        @Test
        void shouldReturn401ForUnauthenticatedGetUserUsage() {
            var result = mvc.get().uri("/api/usage/user/user1").exchange();
            assertThat(result).hasStatus(401);
        }

        @Test
        void shouldReturn401ForUnauthenticatedGetFeatureUsagePaginated() {
            var result = mvc.get().uri("/api/usage/feature/FEAT-001").exchange();
            assertThat(result).hasStatus(401);
        }

        @Test
        void shouldReturn401ForUnauthenticatedGetProductUsagePaginated() {
            var result = mvc.get().uri("/api/usage/product/PROD-001").exchange();
            assertThat(result).hasStatus(401);
        }
    }

    /**
     * Tests for authenticated regular users (USER role)
     * Tests only endpoints accessible to USER role
     */
    @Nested
    @WithMockOAuth2User(roles = {"USER"})
    class AuthenticatedUserTests {

        @Test
        void shouldCreateUsageEventWithCompleteDataAndReturn201() {
            var requestBody =
                    """
                    {
                        "actionType": "FEATURE_VIEWED",
                        "featureCode": "FEAT-001",
                        "productCode": "PROD-001",
                        "context": {
                            "source": "web",
                            "device": "desktop"
                        }
                    }
                    """;

            var result = mvc.post()
                    .uri("/api/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();

            // Verify successful creation (flexible 2xx pattern)
            assertThat(result).hasStatus2xxSuccessful();

            // Verify complete response structure
            assertThat(result).bodyJson().extractingPath("$.id").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.actionType")
                    .asString()
                    .isEqualTo("FEATURE_VIEWED");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.featureCode")
                    .asString()
                    .isEqualTo("FEAT-001");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.productCode")
                    .asString()
                    .isEqualTo("PROD-001");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.userId")
                    .asString()
                    .isNotEmpty(); // UserId is anonymized due to GDPR settings
            assertThat(result).bodyJson().extractingPath("$.timestamp").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.context")
                    .asString()
                    .contains("web")
                    .contains("desktop");
        }

        @Test
        void shouldCreateUsageEventWithMinimalData() {
            var requestBody =
                    """
                    {
                        "actionType": "FEATURE_CREATED"
                    }
                    """;

            var result = mvc.post()
                    .uri("/api/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify all required fields are present even with minimal input
            assertThat(result).bodyJson().extractingPath("$.id").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.actionType")
                    .asString()
                    .isEqualTo("FEATURE_CREATED");
            assertThat(result).bodyJson().extractingPath("$.userId").asString().isNotEmpty();
            assertThat(result).bodyJson().extractingPath("$.timestamp").isNotNull();
            // Optional fields should be null
            assertThat(result).bodyJson().extractingPath("$.featureCode").isNull();
            assertThat(result).bodyJson().extractingPath("$.productCode").isNull();
        }

        @Test
        void shouldRejectPostUsageEventWithoutActionType() {
            var requestBody =
                    """
                    {
                        "featureCode": "FEAT-001",
                        "productCode": "PROD-001"
                    }
                    """;

            var result = mvc.post()
                    .uri("/api/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();

            assertThat(result).hasStatus4xxClientError();
        }

        @Test
        void shouldRejectPostUsageEventWithInvalidActionType() {
            var requestBody =
                    """
                    {
                        "actionType": "INVALID_ACTION",
                        "featureCode": "FEAT-001"
                    }
                    """;

            var result = mvc.post()
                    .uri("/api/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();

            assertThat(result).hasStatus4xxClientError();
        }

        @Test
        void shouldRejectPostUsageEventWithMalformedJson() {
            var requestBody =
                    """
                    {
                        "actionType": "FEATURE_VIEWED",
                        "featureCode": "FEAT-001"
                    """;

            var result = mvc.post()
                    .uri("/api/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();

            assertThat(result).hasStatus4xxClientError();
        }

        @Test
        void shouldGetFeatureStatsWithPreciseValidation() {
            // Create exactly 3 events with different action types
            createUsageEventViaAPI("FEATURE_VIEWED", "TEST-FEATURE", "TEST-PRODUCT");
            createUsageEventViaAPI("FEATURE_UPDATED", "TEST-FEATURE", "TEST-PRODUCT");
            createUsageEventViaAPI("FEATURE_VIEWED", "TEST-FEATURE", "TEST-PRODUCT");

            var result = mvc.get().uri("/api/usage/feature/TEST-FEATURE/stats").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify exact counts
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.featureCode")
                    .asString()
                    .isEqualTo("TEST-FEATURE");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalUsageCount")
                    .asNumber()
                    .isEqualTo(3);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.uniqueUserCount")
                    .asNumber()
                    .isEqualTo(1);

            // Verify exact action type distribution
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.usageByActionType.FEATURE_VIEWED")
                    .asNumber()
                    .isEqualTo(2);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.usageByActionType.FEATURE_UPDATED")
                    .asNumber()
                    .isEqualTo(1);

            // Verify structure completeness
            assertThat(result).bodyJson().extractingPath("$.topUsers").asList().isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.usageByProduct")
                    .asList()
                    .isNotNull();
        }

        @Test
        void shouldGetProductStatsWithPreciseValidation() {
            // Create exactly 2 events for 2 different features
            createUsageEventViaAPI("FEATURE_VIEWED", "FEAT-A", "TEST-PRODUCT");
            createUsageEventViaAPI("FEATURE_VIEWED", "FEAT-B", "TEST-PRODUCT");

            var result = mvc.get().uri("/api/usage/product/TEST-PRODUCT/stats").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify exact counts
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.productCode")
                    .asString()
                    .isEqualTo("TEST-PRODUCT");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalUsageCount")
                    .asNumber()
                    .isEqualTo(2);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.uniqueUserCount")
                    .asNumber()
                    .isEqualTo(1);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.uniqueFeatureCount")
                    .asNumber()
                    .isEqualTo(2);

            // Verify structure completeness
            assertThat(result).bodyJson().extractingPath("$.usageByActionType").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.topFeatures")
                    .asList()
                    .isNotNull();
            assertThat(result).bodyJson().extractingPath("$.topUsers").asList().isNotNull();
        }

        @Test
        void shouldGetOverallStatsWithStructureValidation() {
            // Create test data
            createUsageEventViaAPI("FEATURE_VIEWED", "TEST-FEATURE", "TEST-PRODUCT");

            var result = mvc.get().uri("/api/usage/stats").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify structure is correct (overall stats include all events)
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalUsageCount")
                    .asNumber()
                    .satisfies(count -> assertThat(count.intValue()).isGreaterThan(0));
            assertThat(result).bodyJson().extractingPath("$.uniqueUserCount").isNotNull();
            assertThat(result).bodyJson().extractingPath("$.uniqueFeatureCount").isNotNull();
            assertThat(result).bodyJson().extractingPath("$.uniqueProductCount").isNotNull();
            assertThat(result).bodyJson().extractingPath("$.usageByActionType").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.topFeatures")
                    .asList()
                    .isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.topProducts")
                    .asList()
                    .isNotNull();
            assertThat(result).bodyJson().extractingPath("$.topUsers").asList().isNotNull();
        }

        @Test
        void shouldHandleDateRangeFiltersCorrectly() {
            // Create an event
            createUsageEventViaAPI("FEATURE_VIEWED", "DATE-FILTER-FEATURE", "DATE-FILTER-PRODUCT");

            // Use a date range that includes the event
            Instant startDate = Instant.now().minusSeconds(3600); // 1 hour ago
            Instant endDate = Instant.now().plusSeconds(3600); // 1 hour from now

            var result = mvc.get()
                    .uri("/api/usage/feature/DATE-FILTER-FEATURE/stats?startDate=" + startDate.toString() + "&endDate="
                            + endDate.toString())
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should include our event
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalUsageCount")
                    .asNumber()
                    .isEqualTo(1);

            // Test with date range that excludes the event
            Instant pastStart = Instant.now().minusSeconds(7200); // 2 hours ago
            Instant pastEnd = Instant.now().minusSeconds(3600); // 1 hour ago

            var pastResult = mvc.get()
                    .uri("/api/usage/feature/DATE-FILTER-FEATURE/stats?startDate=" + pastStart.toString() + "&endDate="
                            + pastEnd.toString())
                    .exchange();

            assertThat(pastResult).hasStatus2xxSuccessful();

            // Should exclude our event (exactly 0)
            assertThat(pastResult)
                    .bodyJson()
                    .extractingPath("$.totalUsageCount")
                    .asNumber()
                    .isEqualTo(0);
        }

        @Test
        void shouldReturnEmptyStatsForNonExistentFeature() {
            var result = mvc.get().uri("/api/usage/feature/NON-EXISTENT/stats").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify exact zero values and correct structure
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.featureCode")
                    .asString()
                    .isEqualTo("NON-EXISTENT");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalUsageCount")
                    .asNumber()
                    .isEqualTo(0);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.uniqueUserCount")
                    .asNumber()
                    .isEqualTo(0);

            // Verify empty maps are actually empty
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.usageByActionType")
                    .asMap()
                    .isEmpty();
            assertThat(result).bodyJson().extractingPath("$.topUsers").asList().isEmpty();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.usageByProduct")
                    .asList()
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyStatsForNonExistentProduct() {
            var result = mvc.get().uri("/api/usage/product/NON-EXISTENT/stats").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify exact zero values and correct structure
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.productCode")
                    .asString()
                    .isEqualTo("NON-EXISTENT");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalUsageCount")
                    .asNumber()
                    .isEqualTo(0);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.uniqueUserCount")
                    .asNumber()
                    .isEqualTo(0);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.uniqueFeatureCount")
                    .asNumber()
                    .isEqualTo(0);
        }

        @Test
        void shouldHandleInvalidDateFormats() {
            var result = mvc.get()
                    .uri("/api/usage/feature/FEAT-001/stats?startDate=invalid-date")
                    .exchange();
            assertThat(result).hasStatus4xxClientError();

            var result1 = mvc.get()
                    .uri("/api/usage/product/PROD-001/stats?endDate=invalid-date")
                    .exchange();
            assertThat(result1).hasStatus4xxClientError();

            var result2 = mvc.get().uri("/api/usage/stats?endDate=invalid-date").exchange();
            assertThat(result2).hasStatus4xxClientError();

            // Test top features with invalid dates
            var result3 = mvc.get()
                    .uri("/api/usage/top-features?startDate=invalid-date")
                    .exchange();
            assertThat(result3).hasStatus4xxClientError();

            // Test top users with invalid dates
            var result4 =
                    mvc.get().uri("/api/usage/top-users?endDate=invalid-date").exchange();
            assertThat(result4).hasStatus4xxClientError();
        }

        @Test
        void shouldHandleInvalidDateRanges() {
            Instant startDate = Instant.now();
            Instant endDate = Instant.now().minusSeconds(3600); // End before start

            var result = mvc.get()
                    .uri("/api/usage/stats?startDate=" + startDate.toString() + "&endDate=" + endDate.toString())
                    .exchange();

            assertThat(result).hasStatus4xxClientError();
        }

        @Test
        void shouldGetTopFeatures() {
            // Create test data with different usage counts to verify sorting
            createUsageEventViaAPI("FEATURE_VIEWED", "TOP-FEATURE-1", "TEST-PRODUCT");
            createUsageEventViaAPI("FEATURE_VIEWED", "TOP-FEATURE-2", "TEST-PRODUCT");
            createUsageEventViaAPI("FEATURE_VIEWED", "TOP-FEATURE-1", "TEST-PRODUCT"); // Make FEATURE-1 more popular

            var result = mvc.get().uri("/api/usage/top-features").exchange();

            assertThat(result).hasStatus2xxSuccessful();
            assertThat(result).bodyJson().extractingPath("$").asList().isNotEmpty();

            // Verify sorting: first element should be TOP-FEATURE-1 (2 usages)
            assertThat(result).bodyJson().extractingPath("$[0].name").asString().isEqualTo("TOP-FEATURE-1");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].count")
                    .asNumber()
                    .isEqualTo(2);

            // Second element should be TOP-FEATURE-2 (1 usage)
            assertThat(result).bodyJson().extractingPath("$[1].name").asString().isEqualTo("TOP-FEATURE-2");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[1].count")
                    .asNumber()
                    .isEqualTo(1);
        }

        @Test
        void shouldGetTopUsers() {
            // Create test data (all events from same user due to @WithMockOAuth2User)
            createUsageEventViaAPI("FEATURE_VIEWED", "TEST-FEATURE-1", "TEST-PRODUCT");
            createUsageEventViaAPI("FEATURE_VIEWED", "TEST-FEATURE-2", "TEST-PRODUCT");

            var result = mvc.get().uri("/api/usage/top-users").exchange();

            assertThat(result).hasStatus2xxSuccessful();
            assertThat(result).bodyJson().extractingPath("$").asList().isNotEmpty();

            // Should have exactly one user with count = 2 (first element due to sorting)
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].count")
                    .asNumber()
                    .isEqualTo(2);
            assertThat(result).bodyJson().extractingPath("$[0].name").asString().isNotEmpty();
        }

        @Test
        void shouldGetTopFeaturesWithDateRangeFilter() {
            // Create test data
            createUsageEventViaAPI("FEATURE_VIEWED", "DATE-FEATURE", "TEST-PRODUCT");

            Instant startDate = Instant.now().minusSeconds(3600); // 1 hour ago
            Instant endDate = Instant.now().plusSeconds(3600); // 1 hour from now

            var result = mvc.get()
                    .uri("/api/usage/top-features?startDate=" + startDate.toString() + "&endDate=" + endDate.toString())
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();
            assertThat(result).bodyJson().extractingPath("$").asList().isNotEmpty();
        }

        @Test
        void shouldGetTopUsersWithLimitParameter() {
            // Create test data
            createUsageEventViaAPI("FEATURE_VIEWED", "LIMIT-FEATURE", "TEST-PRODUCT");

            var result = mvc.get().uri("/api/usage/top-users?limit=5").exchange();

            assertThat(result).hasStatus2xxSuccessful();
            assertThat(result).bodyJson().extractingPath("$").asList().isNotEmpty();

            // Should not exceed limit of 5 users (though we only have 1 user in test)
            assertThat(result).bodyJson().extractingPath("$").asList().size().isLessThanOrEqualTo(5);
        }

        @Test
        void shouldGetUserUsageWithPagination() {
            // Create test data for specific user (current authenticated user)
            createUsageEventViaAPI("FEATURE_VIEWED", "USER-FEATURE-1", "TEST-PRODUCT");
            createUsageEventViaAPI("FEATURE_VIEWED", "USER-FEATURE-2", "TEST-PRODUCT");

            // Use the raw userId "user" which will be anonymized by the endpoint
            // The @WithMockOAuth2User annotation uses "user" as the default username
            var result = mvc.get().uri("/api/usage/user/user?page=0&size=10").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify pagination structure
            assertThat(result).bodyJson().extractingPath("$.content").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalElements")
                    .asNumber()
                    .isEqualTo(2);
            assertThat(result).bodyJson().extractingPath("$.size").asNumber().isEqualTo(10);
            assertThat(result).bodyJson().extractingPath("$.number").asNumber().isEqualTo(0);
        }

        @Test
        void shouldGetFeatureUsageWithPagination() {
            // Create test data
            createUsageEventViaAPI("FEATURE_VIEWED", "PAGINATED-FEATURE", "TEST-PRODUCT");
            createUsageEventViaAPI("FEATURE_UPDATED", "PAGINATED-FEATURE", "TEST-PRODUCT");

            var result = mvc.get()
                    .uri("/api/usage/feature/PAGINATED-FEATURE?page=0&size=5")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify pagination structure
            assertThat(result).bodyJson().extractingPath("$.content").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalElements")
                    .asNumber()
                    .isEqualTo(2);
            assertThat(result).bodyJson().extractingPath("$.size").asNumber().isEqualTo(5);
            assertThat(result).bodyJson().extractingPath("$.number").asNumber().isEqualTo(0);

            // Verify content structure
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content[0].featureCode")
                    .asString()
                    .isEqualTo("PAGINATED-FEATURE");
        }

        @Test
        void shouldGetProductUsageWithPagination() {
            // Create test data
            createUsageEventViaAPI("FEATURE_VIEWED", "FEAT-1", "PAGINATED-PRODUCT");
            createUsageEventViaAPI("FEATURE_VIEWED", "FEAT-2", "PAGINATED-PRODUCT");

            var result = mvc.get()
                    .uri("/api/usage/product/PAGINATED-PRODUCT?page=0&size=5")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify pagination structure
            assertThat(result).bodyJson().extractingPath("$.content").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalElements")
                    .asNumber()
                    .isEqualTo(2);
            assertThat(result).bodyJson().extractingPath("$.size").asNumber().isEqualTo(5);
            assertThat(result).bodyJson().extractingPath("$.number").asNumber().isEqualTo(0);

            // Verify content structure
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content[0].productCode")
                    .asString()
                    .isEqualTo("PAGINATED-PRODUCT");
        }

        /**
         * Helper method to create usage events via API instead of direct repository access.
         */
        private void createUsageEventViaAPI(String actionType, String featureCode, String productCode) {
            var requestBody = String.format(
                    """
                    {
                        "actionType": "%s",
                        "featureCode": "%s",
                        "productCode": "%s"
                    }
                    """,
                    actionType, featureCode, productCode);

            var result = mvc.post()
                    .uri("/api/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();

            // Ensure the event was created successfully
            assertThat(result).hasStatus2xxSuccessful();
        }
    }

    /**
     * Tests for admin-only endpoints (ADMIN role)
     * Tests restricted endpoints that require ADMIN or PRODUCT_MANAGER role
     */
    @Nested
    @WithMockOAuth2User(roles = {"ADMIN", "PRODUCT_MANAGER"})
    class AdminTests {

        @Test
        void shouldGetFeatureEventsWithPreciseValidation() {
            // Create exactly 2 events
            createUsageEventViaAPI("FEATURE_VIEWED", "TEST-FEATURE", "TEST-PRODUCT");
            createUsageEventViaAPI("FEATURE_UPDATED", "TEST-FEATURE", "TEST-PRODUCT");

            var result = mvc.get().uri("/api/usage/feature/TEST-FEATURE/events").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify pagination structure and content size
            assertThat(result).bodyJson().extractingPath("$.content").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalElements")
                    .asNumber()
                    .isEqualTo(2);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content.size()")
                    .asNumber()
                    .isEqualTo(2);

            // Verify both events have correct feature code
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content[0].featureCode")
                    .asString()
                    .isEqualTo("TEST-FEATURE");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content[1].featureCode")
                    .asString()
                    .isEqualTo("TEST-FEATURE");

            // Verify event structure
            assertThat(result).bodyJson().extractingPath("$.content[0].id").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content[0].actionType")
                    .isNotNull();
            assertThat(result).bodyJson().extractingPath("$.content[0].userId").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content[0].timestamp")
                    .isNotNull();
        }

        @Test
        void shouldGetProductEventsWithPreciseValidation() {
            // Create exactly 2 events for different features
            createUsageEventViaAPI("FEATURE_VIEWED", "FEAT-1", "TEST-PRODUCT");
            createUsageEventViaAPI("FEATURE_VIEWED", "FEAT-2", "TEST-PRODUCT");

            var result = mvc.get().uri("/api/usage/product/TEST-PRODUCT/events").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify pagination structure and content size
            assertThat(result).bodyJson().extractingPath("$.content").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalElements")
                    .asNumber()
                    .isEqualTo(2);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content.size()")
                    .asNumber()
                    .isEqualTo(2);

            // Verify all events belong to the requested product
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content[0].productCode")
                    .asString()
                    .isEqualTo("TEST-PRODUCT");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content[1].productCode")
                    .asString()
                    .isEqualTo("TEST-PRODUCT");
        }

        @Test
        void shouldGetAllEventsWithFiltersAndPreciseValidation() {
            // Create 3 events: 2 FEATURE_VIEWED, 1 FEATURE_UPDATED
            createUsageEventViaAPI("FEATURE_VIEWED", "TEST-FEATURE", "TEST-PRODUCT");
            createUsageEventViaAPI("FEATURE_UPDATED", "TEST-FEATURE", "TEST-PRODUCT");
            createUsageEventViaAPI("FEATURE_VIEWED", "TEST-FEATURE", "TEST-PRODUCT");

            // Filter by FEATURE_VIEWED only
            var result = mvc.get()
                    .uri("/api/usage/events?actionType=FEATURE_VIEWED&featureCode=TEST-FEATURE")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify pagination structure and content size
            assertThat(result).bodyJson().extractingPath("$.content").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalElements")
                    .asNumber()
                    .isEqualTo(2);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content.size()")
                    .asNumber()
                    .isEqualTo(2);

            // Verify all returned events match the filter
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content[0].actionType")
                    .asString()
                    .isEqualTo("FEATURE_VIEWED");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content[0].featureCode")
                    .asString()
                    .isEqualTo("TEST-FEATURE");
        }

        @Test
        void shouldGetAllUsageEventsWithMultipleFilters() {
            // Create test data
            createUsageEventViaAPI("FEATURE_VIEWED", "MULTI-FEATURE", "MULTI-PRODUCT");
            createUsageEventViaAPI("FEATURE_UPDATED", "MULTI-FEATURE", "MULTI-PRODUCT");

            var result = mvc.get()
                    .uri(
                            "/api/usage/events?actionType=FEATURE_VIEWED&featureCode=MULTI-FEATURE&productCode=MULTI-PRODUCT")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify pagination structure and content size
            assertThat(result).bodyJson().extractingPath("$.content").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalElements")
                    .asNumber()
                    .isEqualTo(1);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content.size()")
                    .asNumber()
                    .isEqualTo(1);
        }

        @Test
        void shouldReturnEmptyListForNonExistentFeatureEvents() {
            var result = mvc.get().uri("/api/usage/feature/NON-EXISTENT/events").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify pagination structure with empty content
            assertThat(result).bodyJson().extractingPath("$.content").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalElements")
                    .asNumber()
                    .isEqualTo(0);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content.size()")
                    .asNumber()
                    .isEqualTo(0);
            assertThat(result).bodyJson().extractingPath("$.content").asList().isEmpty();
        }

        @Test
        void shouldReturnEmptyListForNonExistentProductEvents() {
            var result = mvc.get().uri("/api/usage/product/NON-EXISTENT/events").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify pagination structure with empty content
            assertThat(result).bodyJson().extractingPath("$.content").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalElements")
                    .asNumber()
                    .isEqualTo(0);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content.size()")
                    .asNumber()
                    .isEqualTo(0);
            assertThat(result).bodyJson().extractingPath("$.content").asList().isEmpty();
        }

        @Test
        void shouldGetAllEventsWithUserIdFilter() {
            // Create test data with specific user
            createUsageEventViaAPI("FEATURE_VIEWED", "USER-FILTER-FEATURE", "USER-FILTER-PRODUCT");
            createUsageEventViaAPI("FEATURE_UPDATED", "USER-FILTER-FEATURE", "USER-FILTER-PRODUCT");

            // Filter by userId (current authenticated user is "user")
            var result = mvc.get().uri("/api/usage/events?userId=user").exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify pagination structure and content size
            assertThat(result).bodyJson().extractingPath("$.content").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalElements")
                    .asNumber()
                    .isEqualTo(2);

            // Verify all returned events belong to the filtered user
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content[0].userId")
                    .asString()
                    .isNotEmpty(); // userId is anonymized but should be present
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content[1].userId")
                    .asString()
                    .isNotEmpty();
        }

        /**
         * Helper method to create usage events via API instead of direct repository access.
         */
        private void createUsageEventViaAPI(String actionType, String featureCode, String productCode) {
            var requestBody = String.format(
                    """
                    {
                        "actionType": "%s",
                        "featureCode": "%s",
                        "productCode": "%s"
                    }
                    """,
                    actionType, featureCode, productCode);

            var result = mvc.post()
                    .uri("/api/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();

            // Ensure the event was created successfully
            assertThat(result).hasStatus2xxSuccessful();
        }
    }

    /**
     * Tests for forbidden access - regular users trying to access restricted endpoints
     */
    @Nested
    @WithMockOAuth2User(roles = {"USER"})
    class ForbiddenAccessTests {

        @Test
        void shouldReturn403ForUserAccessingAllEvents() {
            var result = mvc.get().uri("/api/usage/events").exchange();
            assertThat(result).hasStatus(403);
        }

        @Test
        void shouldReturn403ForUserAccessingFeatureEvents() {
            var result = mvc.get().uri("/api/usage/feature/FEAT-001/events").exchange();
            assertThat(result).hasStatus(403);
        }

        @Test
        void shouldReturn403ForUserAccessingProductEvents() {
            var result = mvc.get().uri("/api/usage/product/PROD-001/events").exchange();
            assertThat(result).hasStatus(403);
        }
    }
}

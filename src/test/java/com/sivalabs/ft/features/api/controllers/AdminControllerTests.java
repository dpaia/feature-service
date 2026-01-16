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
 * Integration tests for AdminController covering:
 * - Authorization (ADMIN only access, 403 for USER/PRODUCT_MANAGER)
 * - Error logging integration
 * - Health metrics calculation
 * - Reprocess functionality with resolved tracking
 */
class AdminControllerTests extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM error_log");
        jdbcTemplate.execute("DELETE FROM feature_usage");
    }

    /**
     * Authorization tests - verify only ADMIN role can access admin endpoints
     */
    @Nested
    class AuthorizationTests {

        @Test
        void shouldReturn401ForUnauthenticatedAccessToHealth() {
            var result = mvc.get().uri("/api/admin/health").exchange();
            assertThat(result).hasStatus(401);
        }

        @Test
        void shouldReturn401ForUnauthenticatedAccessToErrors() {
            var result = mvc.get().uri("/api/admin/errors").exchange();
            assertThat(result).hasStatus(401);
        }

        @Test
        @WithMockOAuth2User(roles = {"USER"})
        void shouldReturn403ForUserRoleAccessingHealth() {
            var result = mvc.get().uri("/api/admin/health").exchange();
            assertThat(result).hasStatus(403);
        }

        @Test
        @WithMockOAuth2User(roles = {"PRODUCT_MANAGER"})
        void shouldReturn403ForProductManagerAccessingErrors() {
            var result = mvc.get().uri("/api/admin/errors").exchange();
            assertThat(result).hasStatus(403);
        }

        @Test
        @WithMockOAuth2User(roles = {"USER"})
        void shouldReturn403ForUserRoleAccessingReprocess() {
            var requestBody =
                    """
                {
                    "errorLogIds": [1],
                    "dryRun": true
                }
                """;

            var result = mvc.post()
                    .uri("/api/admin/reprocess")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();

            assertThat(result).hasStatus(403);
        }

        @Test
        @WithMockOAuth2User(roles = {"ADMIN"})
        void shouldReturn200ForAdminAccessingHealth() {
            var result = mvc.get().uri("/api/admin/health").exchange();
            assertThat(result).hasStatus2xxSuccessful();
        }
    }

    /**
     * Health metrics tests - verify accuracy of metrics calculation
     */
    @Nested
    @WithMockOAuth2User(roles = {"ADMIN"})
    class HealthMetricsTests {

        @Test
        void shouldGetSystemHealthMetricsWithCorrectStructure() {
            var result = mvc.get().uri("/api/admin/health").exchange();

            assertThat(result).hasStatus2xxSuccessful();
            assertThat(result).bodyJson().extractingPath("$.totalEvents").isNotNull();
            assertThat(result).bodyJson().extractingPath("$.failedEvents").isNotNull();
            assertThat(result).bodyJson().extractingPath("$.successRate").isNotNull();
            assertThat(result).bodyJson().extractingPath("$.errorRate").isNotNull();
            assertThat(result).bodyJson().extractingPath("$.errorsByType").isNotNull();
            assertThat(result).bodyJson().extractingPath("$.dataGaps").asList().isNotNull();
        }

        @Test
        void shouldCalculateSuccessRateCorrectly() {
            // Create 3 successful events
            createUsageEventViaAPI("FEATURE_VIEWED", "TEST-FEATURE", "TEST-PRODUCT");
            createUsageEventViaAPI("FEATURE_VIEWED", "TEST-FEATURE-2", "TEST-PRODUCT");
            createUsageEventViaAPI("FEATURE_UPDATED", "TEST-FEATURE", "TEST-PRODUCT");

            // Create 1 error directly in error_log
            createErrorLog("VALIDATION_ERROR", "Test validation error");

            // Use very wide date range to ensure we capture all events
            Instant startDate = Instant.now().minus(java.time.Duration.ofDays(1));
            Instant endDate = Instant.now().plus(java.time.Duration.ofDays(1));

            var result = mvc.get()
                    .uri("/api/admin/health?startDate=" + startDate + "&endDate=" + endDate)
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalEvents")
                    .asNumber()
                    .isEqualTo(3);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.failedEvents")
                    .asNumber()
                    .isEqualTo(1);
            // Success rate = (3-1)/3 * 100 = 66.67
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.successRate")
                    .asNumber()
                    .satisfies(rate -> assertThat(rate.doubleValue()).isBetween(66.0, 67.0));
        }

        @Test
        void shouldDetectDataGapsForLowActivityPeriods() {
            // Create events only in first hour
            Instant now = Instant.now();

            // Health check for last 6 hours should detect gaps
            Instant startDate = now.minus(java.time.Duration.ofHours(6));

            var result = mvc.get()
                    .uri("/api/admin/health?startDate=" + startDate + "&endDate=" + now)
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();
            assertThat(result).bodyJson().extractingPath("$.dataGaps").asList().isNotEmpty();
        }

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

            mvc.post()
                    .uri("/api/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();
        }

        private void createErrorLog(String errorType, String message) {
            createErrorLogWithTimestamp(errorType, message, Instant.now());
        }

        private void createErrorLogWithTimestamp(String errorType, String message, Instant timestamp) {
            jdbcTemplate.update(
                    "INSERT INTO error_log (timestamp, error_type, error_message, user_id, resolved, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    java.sql.Timestamp.from(timestamp),
                    errorType,
                    message,
                    "test-user",
                    false,
                    java.sql.Timestamp.from(Instant.now()));
        }
    }

    /**
     * Error management tests - verify error listing and retrieval
     */
    @Nested
    @WithMockOAuth2User(roles = {"ADMIN"})
    class ErrorManagementTests {

        @Test
        void shouldGetErrorsWithPagination() {
            // Create 3 error logs
            createErrorLog("VALIDATION_ERROR", "Error 1");
            createErrorLog("DATABASE_ERROR", "Error 2");
            createErrorLog("PROCESSING_ERROR", "Error 3");

            var result = mvc.get().uri("/api/admin/errors?page=0&size=10").exchange();

            assertThat(result).hasStatus2xxSuccessful();
            assertThat(result).bodyJson().extractingPath("$.content").asList().hasSize(3);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalElements")
                    .asNumber()
                    .isEqualTo(3);
        }

        @Test
        void shouldFilterErrorsByType() {
            // Create different error types
            createErrorLog("VALIDATION_ERROR", "Validation error");
            createErrorLog("DATABASE_ERROR", "Database error");
            createErrorLog("VALIDATION_ERROR", "Another validation error");

            var result = mvc.get()
                    .uri("/api/admin/errors?errorType=VALIDATION_ERROR")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();
            assertThat(result).bodyJson().extractingPath("$.content").asList().hasSize(2);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.content[0].errorType")
                    .asString()
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        void shouldGetErrorByIdWithFullDetails() {
            // Create error log and get its ID directly from database
            createErrorLog("VALIDATION_ERROR", "Test error message");
            Long errorId = jdbcTemplate.queryForObject(
                    "SELECT id FROM error_log WHERE error_message = ?", Long.class, "Test error message");

            var result = mvc.get().uri("/api/admin/errors/" + errorId).exchange();

            assertThat(result).hasStatus2xxSuccessful();
            assertThat(result).bodyJson().extractingPath("$.id").asNumber().isEqualTo(errorId.intValue());
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.errorType")
                    .asString()
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.errorMessage")
                    .asString()
                    .contains("Test error message");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.resolved")
                    .asBoolean()
                    .isFalse();
        }

        @Test
        void shouldReturn404ForNonExistentErrorId() {
            var result = mvc.get().uri("/api/admin/errors/99999").exchange();
            assertThat(result).hasStatus(404);
        }

        private void createErrorLog(String errorType, String message) {
            Instant now = Instant.now();
            jdbcTemplate.update(
                    "INSERT INTO error_log (timestamp, error_type, error_message, user_id, resolved, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    java.sql.Timestamp.from(now),
                    errorType,
                    message,
                    "test-user",
                    false,
                    java.sql.Timestamp.from(now));
        }
    }

    /**
     * Reprocess tests - verify reprocessing functionality with resolved tracking
     */
    @Nested
    @WithMockOAuth2User(roles = {"ADMIN"})
    class ReprocessTests {

        @Test
        void shouldReprocessErrorsUsingErrorLogIds() {
            // Create a failed event in error_log with valid payload
            String validPayload =
                    """
                {
                    "actionType": "FEATURE_VIEWED",
                    "featureCode": "REPROCESS-FEATURE",
                    "productCode": "REPROCESS-PRODUCT"
                }
                """;

            Long errorId = createErrorLogWithPayload("PROCESSING_ERROR", "Previous failure", validPayload);

            var requestBody = String.format(
                    """
                    {
                        "errorLogIds": [%d],
                        "dryRun": false
                    }
                    """,
                    errorId);

            var result = mvc.post()
                    .uri("/api/admin/reprocess")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalProcessed")
                    .asNumber()
                    .isEqualTo(1);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.successCount")
                    .asNumber()
                    .isEqualTo(1);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.failedCount")
                    .asNumber()
                    .isEqualTo(0);

            // Verify error_log.resolved was set to true
            Boolean resolved =
                    jdbcTemplate.queryForObject("SELECT resolved FROM error_log WHERE id = ?", Boolean.class, errorId);
            assertThat(resolved).isTrue();
        }

        @Test
        void shouldSkipResolvedErrorsWhenUsingDateRange() {
            // Create 2 errors: 1 resolved, 1 unresolved
            String validPayload =
                    """
                {
                    "actionType": "FEATURE_VIEWED",
                    "featureCode": "TEST-FEATURE",
                    "productCode": "TEST-PRODUCT"
                }
                """;

            // Create errors with current timestamp
            createErrorLogWithPayload("VALIDATION_ERROR", "Error 1", validPayload, true); // resolved
            createErrorLogWithPayload("VALIDATION_ERROR", "Error 2", validPayload, false); // unresolved

            // Use very wide date range to ensure we capture all errors
            Instant startDate = Instant.now().minus(java.time.Duration.ofDays(1));
            Instant endDate = Instant.now().plus(java.time.Duration.ofDays(1));

            var requestBody = String.format(
                    """
                    {
                        "dateRange": {
                            "startDate": "%s",
                            "endDate": "%s"
                        },
                        "dryRun": false
                    }
                    """,
                    startDate, endDate);

            var result = mvc.post()
                    .uri("/api/admin/reprocess")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();
            // Should process only 1 error (the unresolved one)
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalProcessed")
                    .asNumber()
                    .isEqualTo(1);
        }

        @Test
        void shouldNotModifyDataInDryRunMode() {
            String validPayload =
                    """
                {
                    "actionType": "FEATURE_VIEWED",
                    "featureCode": "TEST-FEATURE",
                    "productCode": "TEST-PRODUCT"
                }
                """;

            Long errorId = createErrorLogWithPayload("VALIDATION_ERROR", "Test error", validPayload);

            var requestBody = String.format(
                    """
                    {
                        "errorLogIds": [%d],
                        "dryRun": true
                    }
                    """,
                    errorId);

            var result = mvc.post()
                    .uri("/api/admin/reprocess")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalProcessed")
                    .asNumber()
                    .isEqualTo(1);

            // Verify error_log.resolved was NOT changed (still false in dry-run)
            Boolean resolved =
                    jdbcTemplate.queryForObject("SELECT resolved FROM error_log WHERE id = ?", Boolean.class, errorId);
            assertThat(resolved).isFalse();

            // Verify no new feature_usage record was created
            int usageCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feature_usage", Integer.class);
            assertThat(usageCount).isZero();
        }

        @Test
        void shouldReturn400WhenBothErrorLogIdsAndDateRangeAreMissing() {
            var requestBody =
                    """
                {
                    "dryRun": false
                }
                """;

            var result = mvc.post()
                    .uri("/api/admin/reprocess")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();

            assertThat(result).hasStatus(400);
        }

        @Test
        void shouldTrackFailedReprocessInResult() {
            // Create error with invalid payload (will fail to parse)
            Long errorId = createErrorLogWithPayload("VALIDATION_ERROR", "Test", "invalid json");

            var requestBody = String.format(
                    """
                    {
                        "errorLogIds": [%d],
                        "dryRun": false
                    }
                    """,
                    errorId);

            var result = mvc.post()
                    .uri("/api/admin/reprocess")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalProcessed")
                    .asNumber()
                    .isEqualTo(1);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.successCount")
                    .asNumber()
                    .isEqualTo(0);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.failedCount")
                    .asNumber()
                    .isEqualTo(1);
            assertThat(result).bodyJson().extractingPath("$.errors").asList().hasSize(1);
        }

        private Long createErrorLogWithPayload(String errorType, String message, String payload) {
            return createErrorLogWithPayload(errorType, message, payload, false);
        }

        private Long createErrorLogWithPayload(String errorType, String message, String payload, boolean resolved) {
            return createErrorLogWithPayloadAndTimestamp(errorType, message, payload, resolved, Instant.now());
        }

        private Long createErrorLogWithPayloadAndTimestamp(
                String errorType, String message, String payload, boolean resolved, Instant timestamp) {
            jdbcTemplate.update(
                    "INSERT INTO error_log (timestamp, error_type, error_message, event_payload, user_id, resolved, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    java.sql.Timestamp.from(timestamp),
                    errorType,
                    message,
                    payload,
                    "test-user", // Add user_id for reprocessing
                    resolved,
                    java.sql.Timestamp.from(Instant.now()));

            return jdbcTemplate.queryForObject(
                    "SELECT id FROM error_log WHERE error_message = ? ORDER BY id DESC LIMIT 1", Long.class, message);
        }
    }

    /**
     * Error logging integration tests - verify errors are logged automatically
     */
    @Nested
    @WithMockOAuth2User(roles = {"USER"})
    class ErrorLoggingIntegrationTests {

        @Test
        void shouldLogValidationErrorsToErrorLogTable() {
            // Trigger validation error by sending invalid action type
            var requestBody =
                    """
                {
                    "actionType": "INVALID_ACTION"
                }
                """;

            mvc.post()
                    .uri("/api/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .exchange();

            // Verify error was logged to error_log
            int errorCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM error_log WHERE error_type = 'VALIDATION_ERROR'", Integer.class);
            assertThat(errorCount).isGreaterThan(0);
        }
    }
}

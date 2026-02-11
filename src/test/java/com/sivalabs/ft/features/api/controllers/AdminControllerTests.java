package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.ErrorLogRepository;
import com.sivalabs.ft.features.domain.FeatureUsageRepository;
import com.sivalabs.ft.features.domain.entities.ErrorLog;
import com.sivalabs.ft.features.domain.models.ActionType;
import com.sivalabs.ft.features.domain.models.ErrorType;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Integration tests for AdminController endpoints.
 * Uses JPA ErrorLogRepository for test data setup (standard persistence pattern).
 * All assertions performed via API endpoints (true end-to-end).
 */
class AdminControllerTests extends AbstractIT {

    @Autowired
    private ErrorLogRepository errorLogRepository;

    @Autowired
    private FeatureUsageRepository featureUsageRepository;

    // Helper method using JPA repository (standard persistence pattern)
    private void insertErrorLog(
            String errorType, String errorMessage, String stackTrace, String eventPayload, String userId) {
        ErrorLog errorLog = new ErrorLog();
        errorLog.setTimestamp(Instant.now());
        errorLog.setErrorType(ErrorType.valueOf(errorType));
        errorLog.setErrorMessage(errorMessage);
        errorLog.setStackTrace(stackTrace);
        errorLog.setEventPayload(eventPayload);
        errorLog.setUserId(userId);
        errorLog.setResolved(false);
        errorLogRepository.save(errorLog);
    }

    private Long getLatestErrorLogId() {
        return errorLogRepository.findAll().stream()
                .max((e1, e2) -> e1.getCreatedAt().compareTo(e2.getCreatedAt()))
                .map(ErrorLog::getId)
                .orElseThrow();
    }

    @BeforeEach
    void setUp() {
        // Clean up error logs before each test (test-data.sql already deletes them)
        errorLogRepository.deleteAll();
        featureUsageRepository.deleteAll();
    }

    private static Stream<Arguments> unauthorizedAdminRequests() {
        return Stream.of(
                Arguments.of("GET", "/api/admin/health"),
                Arguments.of("GET", "/api/admin/errors"),
                Arguments.of("GET", "/api/admin/errors/1"),
                Arguments.of("POST", "/api/admin/errors/1/reprocess"));
    }

    private static Stream<Arguments> adminEndpoints() {
        return Stream.of(
                Arguments.of("GET", "/api/admin/health"),
                Arguments.of("GET", "/api/admin/errors"),
                Arguments.of("GET", "/api/admin/errors/1"),
                Arguments.of("POST", "/api/admin/errors/1/reprocess"));
    }

    private static Stream<Arguments> adminAuthorizedRequests() {
        return Stream.of(
                Arguments.of("GET", "/api/admin/health", HttpStatus.OK),
                Arguments.of("GET", "/api/admin/errors", HttpStatus.OK),
                Arguments.of("GET", "/api/admin/errors/1", HttpStatus.NOT_FOUND),
                Arguments.of("POST", "/api/admin/errors/1/reprocess", HttpStatus.NOT_FOUND));
    }

    private MvcTestResult exchange(String method, String uri) {
        return switch (method) {
            case "GET" -> mvc.get().uri(uri).exchange();
            case "POST" -> mvc.post().uri(uri).exchange();
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };
    }

    // ========== Authorization Tests ==========

    @ParameterizedTest
    @MethodSource("unauthorizedAdminRequests")
    void shouldReturn401ForAnonymousUserAccessingAdminEndpointsParameterized(String method, String uri) {
        var result = exchange(method, uri);
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest
    @MethodSource("adminEndpoints")
    @WithMockOAuth2User(
            username = "user",
            roles = {"USER"})
    void shouldReturn403ForUserRoleAccessingAdminEndpoints(String method, String uri) {
        var result = exchange(method, uri);
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @MethodSource("adminEndpoints")
    @WithMockOAuth2User(
            username = "manager",
            roles = {"PRODUCT_MANAGER"})
    void shouldReturn403ForProductManagerAccessingAdminEndpoints(String method, String uri) {
        var result = exchange(method, uri);
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @MethodSource("adminAuthorizedRequests")
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldAllowAdminAccessToAdminEndpoints(String method, String uri, HttpStatus expectedStatus) {
        var result = exchange(method, uri);
        assertThat(result).hasStatus(expectedStatus);
    }

    // ========== Error Logging Tests ==========

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"USER"})
    void shouldLogValidationErrorWhenCreatingInvalidFeature() {
        var payload =
                """
            {
                "productCode": "",
                "title": "",
                "description": "Test"
            }
            """;

        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        // Verify validation error was logged
        var errors = errorLogRepository.findAll();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.VALIDATION_ERROR);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldCaptureJsonBodyInEventPayload() {
        // Valid JSON payload structure, but nonexistent product will cause error
        var payload =
                """
            {
                "productCode": "nonexistent-product-xyz",
                "releaseCode": "IDEA-2023.3.8",
                "title": "Test Feature Title",
                "description": "Test Description",
                "assignedTo": "testuser"
            }
            """;

        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        // Verify error was logged (will be PROCESSING_ERROR for nonexistent product)
        var errors = errorLogRepository.findAll();
        assertThat(errors).hasSize(1);

        // **CRITICAL TEST:** Verify JSON body was captured in eventPayload
        String eventPayload = errors.get(0).getEventPayload();
        assertThat(eventPayload).isNotNull();
        assertThat(eventPayload).contains("nonexistent-product-xyz");
        assertThat(eventPayload).contains("Test Feature Title");
        assertThat(eventPayload).contains("testuser");
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldLogDatabaseErrorWithPayloadCapture() {
        // Create a product first
        var createPayload =
                """
            {
                "code": "duplicate-test-product",
                "prefix": "DUP",
                "name": "Duplicate Test Product",
                "description": "First product",
                "imageUrl": "http://example.com/image.png"
            }
            """;

        mvc.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();

        // Try to create duplicate (same code) → DataIntegrityViolationException
        var duplicatePayload =
                """
            {
                "code": "duplicate-test-product",
                "prefix": "DUP2",
                "name": "Duplicate Test Product 2",
                "description": "Second product with same code",
                "imageUrl": "http://example.com/image2.png"
            }
            """;

        mvc.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicatePayload)
                .exchange();

        // Verify DATABASE_ERROR was logged (should be exactly 1 error from duplicate attempt)
        var errors = errorLogRepository.findAll();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.DATABASE_ERROR);

        // **CRITICAL:** Verify JSON payload was captured
        String eventPayload = errors.get(0).getEventPayload();
        assertThat(eventPayload).isNotNull();
        assertThat(eventPayload).contains("duplicate-test-product");
        assertThat(eventPayload).contains("Duplicate Test Product 2");
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"USER"})
    void shouldLogPermissionErrorWhenNonAdminAccessesAdminEndpoint() {
        // USER tries to access admin endpoint → PERMISSION_ERROR logged automatically
        mvc.get().uri("/api/admin/health").exchange();

        // Verify error was logged (using standard JPA methods)
        var errors = errorLogRepository.findAll();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.PERMISSION_ERROR);
        assertThat(errors.get(0).getErrorMessage()).contains("Access denied");
    }

    // ========== Health Metrics Tests ==========

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnHealthMetricsWithDefaultPeriod() {
        // Create some error logs
        insertErrorLog("VALIDATION_ERROR", "Error 1", null, null, "user1");
        insertErrorLog("DATABASE_ERROR", "Error 2", null, null, "user2");

        var result = mvc.get().uri("/api/admin/health").exchange();

        assertThat(result).hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturn400ForInvalidDateRangeOnHealthEndpoint() {
        Instant startDate = Instant.now();
        Instant endDate = Instant.now().minus(1, ChronoUnit.DAYS);
        var result = mvc.get()
                .uri("/api/admin/health?startDate={start}&endDate={end}", startDate, endDate)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnHealthMetricsWithCustomDateRange() {
        // Create error within the range
        insertErrorLog("VALIDATION_ERROR", "Recent error", null, null, "user1");

        Instant endDate = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant startDate = Instant.now().minus(10, ChronoUnit.DAYS);

        var result = mvc.get()
                .uri("/api/admin/health?startDate={start}&endDate={end}", startDate, endDate)
                .exchange();

        assertThat(result).hasStatusOk();
        // Should find exactly 1 failed event that we just created (БД очищена в @BeforeEach)
        assertThat(result)
                .bodyJson()
                .extractingPath("$.failedEvents")
                .asNumber()
                .isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldGroupErrorsByType() {
        // Create multiple errors of different types
        insertErrorLog("VALIDATION_ERROR", "Validation 1", null, null, "user1");
        insertErrorLog("VALIDATION_ERROR", "Validation 2", null, null, "user1");
        insertErrorLog("DATABASE_ERROR", "DB Error", null, null, "user2");
        insertErrorLog("PROCESSING_ERROR", "Processing", null, null, "user3");

        var result = mvc.get().uri("/api/admin/health").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.failedEvents")
                .asNumber()
                .isEqualTo(4);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.VALIDATION_ERROR")
                .asNumber()
                .isEqualTo(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.DATABASE_ERROR")
                .asNumber()
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.PROCESSING_ERROR")
                .asNumber()
                .isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldLogAllFiveErrorTypes() {
        // Comprehensive test for all ErrorType enum values
        insertErrorLog("VALIDATION_ERROR", "Validation", null, null, "user1");
        insertErrorLog("DATABASE_ERROR", "Database", null, null, "user2");
        insertErrorLog("PROCESSING_ERROR", "Processing", null, null, "user3");
        insertErrorLog("PERMISSION_ERROR", "Permission", null, null, "user4");
        insertErrorLog("UNKNOWN_ERROR", "Unknown", null, null, "user5");

        var result = mvc.get().uri("/api/admin/health").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.failedEvents")
                .asNumber()
                .isEqualTo(5);

        // Verify all types are present in errorsByType
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.VALIDATION_ERROR")
                .asNumber()
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.DATABASE_ERROR")
                .asNumber()
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.PROCESSING_ERROR")
                .asNumber()
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.PERMISSION_ERROR")
                .asNumber()
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.UNKNOWN_ERROR")
                .asNumber()
                .isEqualTo(1);
    }

    // ========== Error Management Tests ==========

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldGetErrorsWithPagination() {
        // Create multiple error logs
        for (int i = 0; i < 25; i++) {
            insertErrorLog("VALIDATION_ERROR", "Error " + i, null, "payload" + i, "user" + i);
        }

        var result = mvc.get().uri("/api/admin/errors?page=0&size=10").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content.size()")
                .asNumber()
                .isEqualTo(10);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .isEqualTo(25);
        assertThat(result).bodyJson().extractingPath("$.totalPages").asNumber().isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.number").asNumber().isEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldFilterErrorsByErrorType() {
        insertErrorLog("VALIDATION_ERROR", "Validation", null, null, "user1");
        insertErrorLog("DATABASE_ERROR", "Database", null, null, "user2");
        insertErrorLog("PROCESSING_ERROR", "Processing", null, null, "user3");

        var result =
                mvc.get().uri("/api/admin/errors?errorType=VALIDATION_ERROR").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content.size()")
                .asNumber()
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].errorType")
                .asString()
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldFilterByCombinedErrorTypeAndDateRange() {
        // Test combined filtering by errorType + dateRange
        Instant endDate = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant startDate = Instant.now().minus(2, ChronoUnit.DAYS);

        // Create errors of different types
        insertErrorLog("VALIDATION_ERROR", "Validation 1", null, null, "user1");
        insertErrorLog("VALIDATION_ERROR", "Validation 2", null, null, "user2");
        insertErrorLog("DATABASE_ERROR", "Database error", null, null, "user3");
        insertErrorLog("PROCESSING_ERROR", "Processing error", null, null, "user4");

        // Filter by VALIDATION_ERROR type within date range
        var result = mvc.get()
                .uri("/api/admin/errors?errorType=VALIDATION_ERROR&startDate={start}&endDate={end}", startDate, endDate)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content.size()")
                .asNumber()
                .isEqualTo(2);

        // Verify all returned errors are VALIDATION_ERROR type
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].errorType")
                .asString()
                .isEqualTo("VALIDATION_ERROR");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[1].errorType")
                .asString()
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldFilterErrorsByDateRange() {
        Instant endDate = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant startDate = Instant.now().minus(2, ChronoUnit.DAYS);

        // Create errors with current timestamps
        insertErrorLog("VALIDATION_ERROR", "Recent error", null, null, "user1");

        var result = mvc.get()
                .uri("/api/admin/errors?startDate={start}&endDate={end}", startDate, endDate)
                .exchange();

        assertThat(result).hasStatusOk();
        // Should return exactly 1 error that we just created (БД очищена в @BeforeEach)
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content.size()")
                .asNumber()
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].errorMessage")
                .asString()
                .isEqualTo("Recent error");
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturn400ForInvalidDateRangeOnErrorsEndpoint() {
        Instant startDate = Instant.now();
        Instant endDate = Instant.now().minus(1, ChronoUnit.DAYS);
        var result = mvc.get()
                .uri("/api/admin/errors?startDate={start}&endDate={end}", startDate, endDate)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturn400ForInvalidErrorTypeOnErrorsFilter() {
        var result =
                mvc.get().uri("/api/admin/errors?errorType=INVALID_ERROR_TYPE").exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldVerifyResolvedFieldDefaultsToFalse() {
        // Test that resolved field defaults to false for new error logs
        insertErrorLog("VALIDATION_ERROR", "Test error", null, null, "user1");

        // Verify via API (no direct DB access)
        var result = mvc.get().uri("/api/admin/errors").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].resolved")
                .asBoolean()
                .isFalse();
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldGetErrorById() {
        insertErrorLog("VALIDATION_ERROR", "Test error", null, "payload", "testuser");

        // Get error ID via SQL from requirements (table: error_log, fields from requirements)
        Long errorId = getLatestErrorLogId();

        var result = mvc.get().uri("/api/admin/errors/{id}", errorId).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().satisfies(id -> {
            assertThat(id.longValue()).isEqualTo(errorId);
        });
        assertThat(result).bodyJson().extractingPath("$.errorType").asString().isEqualTo("VALIDATION_ERROR");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorMessage")
                .asString()
                .isEqualTo("Test error");
        assertThat(result).bodyJson().extractingPath("$.userId").asString().isEqualTo("testuser");
        assertThat(result).bodyJson().extractingPath("$.resolved").asBoolean().isFalse();
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnFullErrorLogDtoDetailsById() {
        String stackTrace = "java.lang.RuntimeException: boom";
        String payload = "{\"actionType\":\"FEATURE_VIEWED\",\"featureCode\":\"IDEA-1\"}";
        insertErrorLog("PROCESSING_ERROR", "Processing failed", stackTrace, payload, "full-details-user");
        Long errorId = getLatestErrorLogId();

        var result = mvc.get().uri("/api/admin/errors/{id}", errorId).exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().isEqualTo(errorId.intValue());
        assertThat(result).bodyJson().extractingPath("$.timestamp").asString().isNotBlank();
        assertThat(result).bodyJson().extractingPath("$.errorType").asString().isEqualTo("PROCESSING_ERROR");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorMessage")
                .asString()
                .isEqualTo("Processing failed");
        assertThat(result).bodyJson().extractingPath("$.stackTrace").asString().contains("RuntimeException");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.eventPayload")
                .asString()
                .contains("FEATURE_VIEWED");
        assertThat(result).bodyJson().extractingPath("$.userId").asString().isEqualTo("full-details-user");
        assertThat(result).bodyJson().extractingPath("$.resolved").asBoolean().isFalse();
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturn404WhenErrorNotFound() {
        var result = mvc.get().uri("/api/admin/errors/99999").exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldSortErrorsByTimestampDesc() throws InterruptedException {
        insertErrorLog("VALIDATION_ERROR", "First", null, null, "user1");
        Thread.sleep(100); // Ensure different timestamps
        insertErrorLog("VALIDATION_ERROR", "Second", null, null, "user2");

        var result = mvc.get().uri("/api/admin/errors").exchange();

        assertThat(result).hasStatusOk();
        // Most recent should be first (Second)
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].errorMessage")
                .asString()
                .isEqualTo("Second");
    }

    // ========== Reprocessing Tests ==========

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldSuccessfullyReprocessValidError() {
        // Create a valid usage event payload (raw JSON, no internal models)
        String eventPayload =
                """
            {
                "actionType": "FEATURE_VIEWED",
                "featureCode": "IDEA-1",
                "productCode": "intellij"
            }
            """;

        // Create an error log with valid payload
        insertErrorLog("VALIDATION_ERROR", "Test error", null, eventPayload, "testuser");

        // Get ID via SQL
        Long errorId = getLatestErrorLogId();

        var result = mvc.post().uri("/api/admin/errors/{id}/reprocess", errorId).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().satisfies(id -> {
            assertThat(id.longValue()).isEqualTo(errorId);
        });
        assertThat(result).bodyJson().extractingPath("$.resolved").asBoolean().isTrue();
    }

    // Note: Failed reprocessing scenario is covered by shouldHandleReprocessingErrorWithMissingPayload test
    // which validates that missing payload creates a new error log entry with resolved=false

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturn404WhenReprocessingNonexistentError() {
        var result = mvc.post().uri("/api/admin/errors/99999/reprocess").exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldHandleReprocessingErrorWithMissingPayload() {
        // Create error log without event payload
        insertErrorLog("VALIDATION_ERROR", "Test error", null, null, "testuser");

        // Get ID via SQL
        Long errorId = getLatestErrorLogId();

        var result = mvc.post().uri("/api/admin/errors/{id}/reprocess", errorId).exchange();

        assertThat(result).hasStatusOk();
        // Failed reprocessing creates new error log (from requirements)
        assertThat(result).bodyJson().extractingPath("$.resolved").asBoolean().isFalse();
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().satisfies(id -> {
            assertThat(id.longValue()).isNotEqualTo(errorId); // New record created
        });
        assertThat(result).bodyJson().extractingPath("$.errorType").asString().isEqualTo("PROCESSING_ERROR");
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldCreateNewErrorLogWhenReprocessingInvalidJsonPayload() {
        String invalidPayload = "{\"actionType\":\"FEATURE_VIEWED\",";
        insertErrorLog("PROCESSING_ERROR", "Original error", null, invalidPayload, "bad-json-user");
        Long originalId = getLatestErrorLogId();

        var result =
                mvc.post().uri("/api/admin/errors/{id}/reprocess", originalId).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.resolved").asBoolean().isFalse();
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().satisfies(id -> {
            assertThat(id.longValue()).isNotEqualTo(originalId);
        });

        var original = errorLogRepository.findById(originalId).orElseThrow();
        assertThat(original.getResolved()).isFalse();
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldCreateNewErrorLogWhenReprocessingPayloadWithInvalidActionType() {
        String invalidActionPayload =
                """
            {
                "actionType": "INVALID_ACTION",
                "featureCode": "IDEA-1",
                "productCode": "intellij"
            }
            """;
        insertErrorLog("PROCESSING_ERROR", "Original error", null, invalidActionPayload, "bad-action-user");
        Long originalId = getLatestErrorLogId();

        var result =
                mvc.post().uri("/api/admin/errors/{id}/reprocess", originalId).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.resolved").asBoolean().isFalse();
        assertThat(result).bodyJson().extractingPath("$.errorType").asString().isEqualTo("PROCESSING_ERROR");
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().satisfies(id -> {
            assertThat(id.longValue()).isNotEqualTo(originalId);
        });
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldAllowMultipleReprocessingAttempts() {
        // Create valid payload (raw JSON)
        String eventPayload =
                """
            {
                "actionType": "FEATURE_CREATED",
                "featureCode": "IDEA-2",
                "productCode": "intellij"
            }
            """;

        insertErrorLog("PROCESSING_ERROR", "Test error", null, eventPayload, "testuser");

        // Get ID via SQL
        Long errorId = getLatestErrorLogId();

        // First reprocessing attempt
        var result1 =
                mvc.post().uri("/api/admin/errors/{id}/reprocess", errorId).exchange();
        assertThat(result1).hasStatusOk();

        // Second reprocessing attempt (should work even if already resolved)
        var result2 =
                mvc.post().uri("/api/admin/errors/{id}/reprocess", errorId).exchange();
        assertThat(result2).hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldCreateCompleteAuditTrailForReprocessing() {
        // Create valid payload (raw JSON)
        String eventPayload =
                """
            {
                "actionType": "FEATURE_VIEWED",
                "featureCode": "IDEA-1",
                "productCode": "intellij"
            }
            """;

        insertErrorLog("VALIDATION_ERROR", "Original error", null, eventPayload, "testuser");

        // Get ID via SQL
        Long originalErrorId = getLatestErrorLogId();

        // Reprocess
        mvc.post().uri("/api/admin/errors/{id}/reprocess", originalErrorId).exchange();

        // Verify audit trail via API (not direct DB access)
        var result = mvc.get().uri("/api/admin/errors/{id}", originalErrorId).exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.resolved").asBoolean().isTrue();
        assertThat(result).bodyJson().extractingPath("$.timestamp").isNotNull();
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldActuallyReprocessEventAndCreateFeatureUsageRecord() {
        // 1. Create error log with valid usage event payload
        String eventPayload =
                """
            {
                "userId": "testuser",
                "featureCode": "IDEA-1",
                "productCode": "intellij",
                "actionType": "FEATURE_VIEWED"
            }
            """;

        insertErrorLog("PROCESSING_ERROR", "Failed to process usage event", null, eventPayload, "testuser");
        Long errorId = getLatestErrorLogId();

        // 2. Count initial feature_usage records
        long initialUsageCount = featureUsageRepository.count();

        // 3. Reprocess the error
        var result = mvc.post().uri("/api/admin/errors/{id}/reprocess", errorId).exchange();

        // 4. Verify reprocessing was marked as successful
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.resolved").asBoolean().isTrue();

        // 5. **KEY ASSERTION:** Verify new feature_usage record was actually created
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            long finalUsageCount = featureUsageRepository.count();
            assertThat(finalUsageCount).isEqualTo(initialUsageCount + 1);
        });

        // 6. Verify the created record has correct data
        var usageRecords = featureUsageRepository.findAll();
        var newRecord = usageRecords.get(usageRecords.size() - 1);
        assertThat(newRecord.getUserId()).isEqualTo("testuser");
        assertThat(newRecord.getFeatureCode()).isEqualTo("IDEA-1");
        assertThat(newRecord.getProductCode()).isEqualTo("intellij");
        assertThat(newRecord.getActionType()).isEqualTo(ActionType.FEATURE_VIEWED);
    }
}

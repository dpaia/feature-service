package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Integration tests for EmailDeliveryFailureController
 * Tests admin API endpoints for reviewing email delivery failures
 */
@Sql("/test-data.sql")
class EmailDeliveryFailureControllerTest extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Clean up tables before each test
        jdbcTemplate.execute("DELETE FROM email_delivery_failures");
        jdbcTemplate.execute("DELETE FROM notifications");
    }

    @AfterEach
    void tearDown() {
        // Clean up tables after each test
        jdbcTemplate.execute("DELETE FROM email_delivery_failures");
        jdbcTemplate.execute("DELETE FROM notifications");
    }

    private UUID createTestNotification() {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO notifications (id, recipient_user_id, recipient_email, event_type, event_details, created_at, read, delivery_status)
                VALUES (gen_random_uuid(), 'testuser', 'test@example.com', 'FEATURE_CREATED', 'Test notification', CURRENT_TIMESTAMP, false, 'FAILED')
                RETURNING id
                """,
                UUID.class);
    }

    private UUID createTestEmailDeliveryFailure(UUID notificationId) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO email_delivery_failures (notification_id, recipient_email, event_type, error_message, failed_at)
                VALUES (?, 'test@example.com', 'FEATURE_CREATED', 'SMTP connection failed', CURRENT_TIMESTAMP)
                RETURNING id
                """,
                UUID.class,
                notificationId);
    }

    @Test
    void shouldGetEmailDeliveryFailuresAsAdmin() throws Exception {
        // Given - Create test data
        UUID notificationId = createTestNotification();
        createTestEmailDeliveryFailure(notificationId);

        // When - Get email delivery failures as admin
        var response = mvc.get()
                .uri("/api/admin/email-failures")
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "admin")
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))))
                .exchange();

        // Then - Should return 200 with failures
        assertThat(response).hasStatus(HttpStatus.OK);

        String responseBody = response.getResponse().getContentAsString();
        Map<String, Object> pageResponse = objectMapper.readValue(responseBody, new TypeReference<>() {});
        List<Map<String, Object>> failures = (List<Map<String, Object>>) pageResponse.get("content");

        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).get("recipientEmail")).isEqualTo("test@example.com");
        assertThat(failures.get(0).get("eventType")).isEqualTo("FEATURE_CREATED");
        assertThat(failures.get(0).get("errorMessage")).isEqualTo("SMTP connection failed");
    }

    @Test
    void shouldReturnUnauthorizedWhenNotAuthenticated() throws Exception {
        // When - Try to access admin endpoint without authentication
        var response = mvc.get().uri("/api/admin/email-failures").exchange();

        // Then - Should return 401 Unauthorized
        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturnForbiddenForNonAdminUser() throws Exception {
        // When - Try to access admin endpoint as regular user
        var response = mvc.get()
                .uri("/api/admin/email-failures")
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "user")
                        .claim("realm_access", Map.of("roles", List.of("USER")))))
                .exchange();

        // Then - Should return 403 Forbidden
        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldGetEmailDeliveryFailureByIdAsAdmin() throws Exception {
        // Given - Create test data
        UUID notificationId = createTestNotification();
        UUID failureId = createTestEmailDeliveryFailure(notificationId);

        // When - Get specific failure by ID as admin
        var response = mvc.get()
                .uri("/api/admin/email-failures/{id}", failureId)
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "admin")
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))))
                .exchange();

        // Then - Should return 200 with failure details
        assertThat(response).hasStatus(HttpStatus.OK);

        String responseBody = response.getResponse().getContentAsString();
        Map<String, Object> failure = objectMapper.readValue(responseBody, new TypeReference<>() {});

        assertThat(failure.get("id")).isEqualTo(failureId.toString());
        assertThat(failure.get("recipientEmail")).isEqualTo("test@example.com");
        assertThat(failure.get("eventType")).isEqualTo("FEATURE_CREATED");
        assertThat(failure.get("errorMessage")).isEqualTo("SMTP connection failed");
    }

    @Test
    void shouldReturnNotFoundForNonExistentFailureId() throws Exception {
        // When - Try to get non-existent failure ID as admin
        UUID nonExistentId = UUID.randomUUID();
        var response = mvc.get()
                .uri("/api/admin/email-failures/{id}", nonExistentId)
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "admin")
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))))
                .exchange();

        // Then - Should return 404 Not Found
        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldGetEmailDeliveryFailuresByNotificationIdAsAdmin() throws Exception {
        // Given - Create test data with multiple failures for same notification
        UUID notificationId = createTestNotification();
        createTestEmailDeliveryFailure(notificationId);

        // Create another failure for the same notification
        jdbcTemplate.update(
                """
                INSERT INTO email_delivery_failures (notification_id, recipient_email, event_type, error_message, failed_at)
                VALUES (?, 'test@example.com', 'FEATURE_CREATED', 'Timeout error', CURRENT_TIMESTAMP)
                """,
                notificationId);

        // When - Get failures by notification ID as admin
        var response = mvc.get()
                .uri("/api/admin/email-failures/notification/{notificationId}", notificationId)
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "admin")
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))))
                .exchange();

        // Then - Should return 200 with all failures for that notification
        assertThat(response).hasStatus(HttpStatus.OK);

        String responseBody = response.getResponse().getContentAsString();
        List<Map<String, Object>> failures = objectMapper.readValue(responseBody, new TypeReference<>() {});

        assertThat(failures).hasSize(2);
        assertThat(failures.get(0).get("notificationId")).isEqualTo(notificationId.toString());
        assertThat(failures.get(1).get("notificationId")).isEqualTo(notificationId.toString());
    }

    @Test
    void shouldFilterEmailDeliveryFailuresByDateAsAdmin() throws Exception {
        // Given - Create test data with specific date
        UUID notificationId = createTestNotification();

        // Insert failure with today's date (using UTC)
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Timestamp todayTimestamp = Timestamp.from(today.atStartOfDay().toInstant(ZoneOffset.UTC));
        jdbcTemplate.update(
                """
                INSERT INTO email_delivery_failures (notification_id, recipient_email, event_type, error_message, failed_at)
                VALUES (?, 'test@example.com', 'FEATURE_CREATED', 'Today error', ?)
                """,
                notificationId,
                todayTimestamp);

        // Insert failure with yesterday's date (using UTC)
        LocalDate yesterday = today.minusDays(1);
        Timestamp yesterdayTimestamp = Timestamp.from(yesterday.atStartOfDay().toInstant(ZoneOffset.UTC));
        jdbcTemplate.update(
                """
                INSERT INTO email_delivery_failures (notification_id, recipient_email, event_type, error_message, failed_at)
                VALUES (?, 'test@example.com', 'FEATURE_CREATED', 'Yesterday error', ?)
                """,
                notificationId,
                yesterdayTimestamp);

        // When - Get failures filtered by today's date as admin
        var response = mvc.get()
                .uri("/api/admin/email-failures?date={date}", today.toString())
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "admin")
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))))
                .exchange();

        // Then - Should return 200 with only today's failures
        assertThat(response).hasStatus(HttpStatus.OK);

        String responseBody = response.getResponse().getContentAsString();
        Map<String, Object> pageResponse = objectMapper.readValue(responseBody, new TypeReference<>() {});
        List<Map<String, Object>> failures = (List<Map<String, Object>>) pageResponse.get("content");

        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).get("errorMessage")).isEqualTo("Today error");
    }

    @Test
    void shouldReturnBadRequestForInvalidDateFormat() throws Exception {
        // When - Try to filter with invalid date format as admin
        var response = mvc.get()
                .uri("/api/admin/email-failures?date=invalid-date")
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "admin")
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))))
                .exchange();

        // Then - Should return 400 Bad Request
        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturnEmptyListWhenNoFailuresExist() throws Exception {
        // When - Get email delivery failures when none exist as admin
        var response = mvc.get()
                .uri("/api/admin/email-failures")
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "admin")
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))))
                .exchange();

        // Then - Should return 200 with empty list
        assertThat(response).hasStatus(HttpStatus.OK);

        String responseBody = response.getResponse().getContentAsString();
        Map<String, Object> pageResponse = objectMapper.readValue(responseBody, new TypeReference<>() {});
        List<Map<String, Object>> failures = (List<Map<String, Object>>) pageResponse.get("content");

        assertThat(failures).isEmpty();
    }

    @Test
    void shouldReturnEmptyListForNonExistentNotificationId() throws Exception {
        // When - Get failures for non-existent notification ID as admin
        UUID nonExistentId = UUID.randomUUID();
        var response = mvc.get()
                .uri("/api/admin/email-failures/notification/{notificationId}", nonExistentId)
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "admin")
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))))
                .exchange();

        // Then - Should return 200 with empty list
        assertThat(response).hasStatus(HttpStatus.OK);

        String responseBody = response.getResponse().getContentAsString();
        List<Map<String, Object>> failures = objectMapper.readValue(responseBody, new TypeReference<>() {});

        assertThat(failures).isEmpty();
    }
}

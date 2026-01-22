package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import com.sivalabs.ft.features.testsupport.MockJavaMailSenderConfig;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.jdbc.Sql;

/**
 * Integration tests for Email Delivery Failure logging and Admin API.
 */
@Sql("/test-data.sql")
@Import(MockJavaMailSenderConfig.class)
class EmailDeliveryFailureControllerTests extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JavaMailSender javaMailSender;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM email_delivery_failures");
        jdbcTemplate.execute("DELETE FROM notifications");
        reset(javaMailSender);
        when(javaMailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
    }

    // ========== Test 1: Failure is persisted ==========

    @Test
    @WithMockOAuth2User(username = "alice")
    void shouldPersistEmailDeliveryFailureWhenEmailSendingFails() throws Exception {
        // Given - Configure mail sender to throw exception
        doThrow(new MailSendException("SMTP server unavailable"))
                .when(javaMailSender)
                .send(any(MimeMessage.class));

        CreateFeaturePayload payload =
                new CreateFeaturePayload("intellij", "Failure Test Feature", "Test failure persistence", null, "bob");

        // When - Create feature (email sending will fail)
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        // Then - Feature should still be created
        assertThat(result).hasStatus(HttpStatus.CREATED);

        // Wait for email send attempt
        verify(javaMailSender, timeout(2000).times(1)).send(any(MimeMessage.class));

        // Verify exactly one failure record exists
        Integer failureCount =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM email_delivery_failures", Integer.class);
        assertThat(failureCount).isEqualTo(1);

        // Verify all required fields are present and non-null
        Map<String, Object> failure =
                jdbcTemplate.queryForMap("SELECT * FROM email_delivery_failures ORDER BY failed_at DESC LIMIT 1");
        assertThat(failure.get("id")).isNotNull();
        assertThat(failure.get("notification_id")).isNotNull();
        assertThat(failure.get("recipient_email")).isEqualTo("bob@company.com");
        assertThat(failure.get("event_type")).isEqualTo("FEATURE_CREATED");
        assertThat(failure.get("error_message")).isNotNull();
        assertThat(failure.get("failed_at")).isNotNull();
    }

    // ========== Test 2: Admin list with pagination and sorting ==========

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnPaginatedFailuresSortedByDateDesc() throws Exception {
        // Given - Insert multiple failure records with controlled timestamps
        UUID notificationId = insertTestNotification();
        Instant now = Instant.now();

        insertFailure(notificationId, "user1@test.com", "FEATURE_CREATED", "Error 1", now.minus(2, ChronoUnit.HOURS));
        insertFailure(notificationId, "user2@test.com", "FEATURE_UPDATED", "Error 2", now.minus(1, ChronoUnit.HOURS));
        insertFailure(notificationId, "user3@test.com", "FEATURE_DELETED", "Error 3", now);

        // When - Call admin listing endpoint
        var result = mvc.get().uri("/api/admin/email-failures?page=0&size=10").exchange();

        // Then - Verify response
        assertThat(result).hasStatus(HttpStatus.OK);

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> pageResponse = objectMapper.readValue(responseBody, new TypeReference<>() {});
        List<Map<String, Object>> failures = (List<Map<String, Object>>) pageResponse.get("content");

        assertThat(failures).hasSize(3);
        // Verify sorted by failed_at DESC (newest first)
        assertThat(failures.get(0).get("recipientEmail")).isEqualTo("user3@test.com");
        assertThat(failures.get(1).get("recipientEmail")).isEqualTo("user2@test.com");
        assertThat(failures.get(2).get("recipientEmail")).isEqualTo("user1@test.com");

        // Verify pagination metadata
        assertThat(((Number) pageResponse.get("totalElements")).longValue()).isEqualTo(3L);
        assertThat(((Number) pageResponse.get("totalPages")).intValue()).isEqualTo(1);
        assertThat(((Number) pageResponse.get("size")).intValue()).isEqualTo(10);
        assertThat(((Number) pageResponse.get("number")).intValue()).isEqualTo(0);
    }

    // ========== Test 3: Date filter in UTC ==========

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldFilterFailuresByDateInUtc() throws Exception {
        // Given - Insert failure records with specific timestamps
        UUID notificationId = insertTestNotification();

        // 2026-01-12T00:00:00Z
        insertFailure(
                notificationId, "user1@test.com", "FEATURE_CREATED", "Error 1", Instant.parse("2026-01-12T00:00:00Z"));
        // 2026-01-12T23:59:59Z
        insertFailure(
                notificationId, "user2@test.com", "FEATURE_UPDATED", "Error 2", Instant.parse("2026-01-12T23:59:59Z"));
        // 2026-01-13T00:00:00Z
        insertFailure(
                notificationId, "user3@test.com", "FEATURE_DELETED", "Error 3", Instant.parse("2026-01-13T00:00:00Z"));

        // When - Call admin listing endpoint with date filter
        var result = mvc.get().uri("/api/admin/email-failures?date=2026-01-12").exchange();

        // Then - Only records within 2026-01-12 UTC range should be returned
        assertThat(result).hasStatus(HttpStatus.OK);

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> pageResponse = objectMapper.readValue(responseBody, new TypeReference<>() {});
        List<Map<String, Object>> failures = (List<Map<String, Object>>) pageResponse.get("content");

        assertThat(failures).hasSize(2);
        // Verify only the 2026-01-12 records are returned
        List<String> emails =
                failures.stream().map(f -> (String) f.get("recipientEmail")).toList();
        assertThat(emails).containsExactlyInAnyOrder("user1@test.com", "user2@test.com");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "invalid-date",
                "2026-13-01", // Invalid month
                "2026-01-32", // Invalid day
                "26-01-12", // Wrong year format
                "2026/01/12", // Wrong separator
                "2026-01-12T00:00:00Z", // Timestamp instead of date
                "null" // String "null"
            })
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturn400ForInvalidDateFormat(String invalidDate) throws Exception {
        // When - Call with invalid date format
        var result = mvc.get()
                .uri("/api/admin/email-failures?date={date}", invalidDate)
                .exchange();

        // Then - Should return 400 Bad Request
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    // ========== Test 4: Admin lookup by notification and by failure ID ==========

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnFailuresByNotificationId() throws Exception {
        // Given - Insert multiple failures for the same notification
        UUID notificationId1 = insertTestNotification();
        UUID notificationId2 = insertTestNotification();
        Instant now = Instant.now();

        insertFailure(notificationId1, "user1@test.com", "FEATURE_CREATED", "Error 1", now.minus(2, ChronoUnit.HOURS));
        insertFailure(notificationId1, "user2@test.com", "FEATURE_CREATED", "Error 2", now.minus(1, ChronoUnit.HOURS));
        insertFailure(notificationId2, "user3@test.com", "FEATURE_UPDATED", "Error 3", now);

        // When - Get failures for notification1
        var result = mvc.get()
                .uri("/api/admin/email-failures/notification/{notificationId}", notificationId1)
                .exchange();

        // Then - Should return only failures for notification1
        assertThat(result).hasStatus(HttpStatus.OK);

        String responseBody = result.getResponse().getContentAsString();
        List<Map<String, Object>> failures = objectMapper.readValue(responseBody, new TypeReference<>() {});

        assertThat(failures).hasSize(2);

        // Verify sorted by failed_at DESC
        assertThat(failures.get(0).get("recipientEmail")).isEqualTo("user2@test.com");
        assertThat(failures.get(1).get("recipientEmail")).isEqualTo("user1@test.com");

        // Verify isolation: failures from notificationId2 are NOT included
        List<String> emails =
                failures.stream().map(f -> (String) f.get("recipientEmail")).toList();
        assertThat(emails)
                .containsExactlyInAnyOrder("user1@test.com", "user2@test.com")
                .doesNotContain("user3@test.com");

        // Verify all returned failures have the correct notificationId
        for (Map<String, Object> failure : failures) {
            assertThat(failure.get("notificationId")).isEqualTo(notificationId1.toString());
        }
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnEmptyListForNonExistentNotificationId() throws Exception {
        // When - Get failures for non-existent notification
        var result = mvc.get()
                .uri("/api/admin/email-failures/notification/{notificationId}", UUID.randomUUID())
                .exchange();

        // Then - Should return empty list with 200 OK
        assertThat(result).hasStatus(HttpStatus.OK);

        String responseBody = result.getResponse().getContentAsString();
        List<Map<String, Object>> failures = objectMapper.readValue(responseBody, new TypeReference<>() {});

        assertThat(failures).isEmpty();
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnFailureById() throws Exception {
        // Given - Insert a failure record
        UUID notificationId = insertTestNotification();
        UUID failureId =
                insertFailure(notificationId, "user@test.com", "FEATURE_CREATED", "Test error message", Instant.now());

        // When - Get failure by ID
        var result = mvc.get().uri("/api/admin/email-failures/{id}", failureId).exchange();

        // Then - Should return the failure record
        assertThat(result).hasStatus(HttpStatus.OK);

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> failure = objectMapper.readValue(responseBody, new TypeReference<>() {});

        assertThat(failure.get("id")).isEqualTo(failureId.toString());
        assertThat(failure.get("notificationId")).isEqualTo(notificationId.toString());
        assertThat(failure.get("recipientEmail")).isEqualTo("user@test.com");
        assertThat(failure.get("eventType")).isEqualTo("FEATURE_CREATED");
        assertThat(failure.get("errorMessage")).isEqualTo("Test error message");
        assertThat(failure.get("failedAt")).isNotNull();
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturn404ForNonExistentFailureId() throws Exception {
        // When - Get non-existent failure
        var result = mvc.get()
                .uri("/api/admin/email-failures/{id}", UUID.randomUUID())
                .exchange();

        // Then - Should return 404
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ========== Test 5: Failure logging does not affect outcome ==========

    @Test
    @WithMockOAuth2User(username = "alice")
    void shouldNotAffectOutcomeWhenFailureLoggingFails() throws Exception {
        // Given - Configure mail sender to throw exception (email send fails)
        doThrow(new MailSendException("SMTP server unavailable"))
                .when(javaMailSender)
                .send(any(MimeMessage.class));

        // AND - Break the email_delivery_failures table so ANY logging attempt fails
        // This is implementation-agnostic: works regardless of how logging is implemented
        // Use UUID in name to avoid conflicts if test crashes before finally block
        String brokenTableName =
                "email_delivery_failures_broken_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.execute("ALTER TABLE email_delivery_failures RENAME TO " + brokenTableName);

        try {
            CreateFeaturePayload payload = new CreateFeaturePayload(
                    "intellij",
                    "Logging Failure Test",
                    "Test that failure logging doesn't affect outcome",
                    null,
                    "bob");

            // When - Create feature (email send fails AND failure logging fails)
            var result = mvc.post()
                    .uri("/api/features")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload))
                    .exchange();

            // Then - Feature should still be created (outcome unchanged)
            assertThat(result).hasStatus(HttpStatus.CREATED);

            // Wait until email send was attempted - this is our sync point
            // After this, async processing should have completed
            verify(javaMailSender, timeout(5000).times(1)).send(any(MimeMessage.class));

            // Now assertions are safe - notification should be saved in database
            Integer notificationCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "bob");
            assertThat(notificationCount).isEqualTo(1);

            // Delivery status should be FAILED (email status update still works)
            String deliveryStatus = jdbcTemplate.queryForObject(
                    "SELECT delivery_status FROM notifications WHERE recipient_user_id = ?", String.class, "bob");
            assertThat(deliveryStatus).isEqualTo("FAILED");

            // Key assertion: no exception leaked out, outcome unchanged
            // The test completing successfully proves failure logging didn't break the flow
        } finally {
            // Restore the table for other tests
            jdbcTemplate.execute("ALTER TABLE " + brokenTableName + " RENAME TO email_delivery_failures");
        }
    }

    // ========== Test 6: Admin API authorization ==========

    @Test
    void shouldReturn401ForUnauthenticatedRequests() throws Exception {
        // When - Call admin endpoint without authentication
        var listResult = mvc.get().uri("/api/admin/email-failures").exchange();
        var byIdResult = mvc.get()
                .uri("/api/admin/email-failures/{id}", UUID.randomUUID())
                .exchange();
        var byNotificationResult = mvc.get()
                .uri("/api/admin/email-failures/notification/{notificationId}", UUID.randomUUID())
                .exchange();

        // Then - Should return 401 Unauthorized
        assertThat(listResult).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(byIdResult).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(byNotificationResult).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockOAuth2User(
            username = "regularuser",
            roles = {"USER"})
    void shouldReturn403ForNonAdminUsers() throws Exception {
        // When - Call admin endpoint as non-admin user
        var listResult = mvc.get().uri("/api/admin/email-failures").exchange();
        var byIdResult = mvc.get()
                .uri("/api/admin/email-failures/{id}", UUID.randomUUID())
                .exchange();
        var byNotificationResult = mvc.get()
                .uri("/api/admin/email-failures/notification/{notificationId}", UUID.randomUUID())
                .exchange();

        // Then - Should return 403 Forbidden
        assertThat(listResult).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(byIdResult).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(byNotificationResult).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturn200ForAdminUsers() throws Exception {
        // When - Call admin endpoint as admin user
        var result = mvc.get().uri("/api/admin/email-failures").exchange();

        // Then - Should return 200 OK
        assertThat(result).hasStatus(HttpStatus.OK);
    }

    @Test
    @WithMockOAuth2User(
            username = "multiuser",
            roles = {"USER", "ADMIN"})
    void shouldAllowAccessWithMultipleRolesIncludingAdmin() throws Exception {
        // When - Call admin endpoint as user with both USER and ADMIN roles
        var result = mvc.get().uri("/api/admin/email-failures").exchange();

        // Then - Should return 200 OK (ADMIN role grants access)
        assertThat(result).hasStatus(HttpStatus.OK);
    }

    // ========== Test 7: API response contract and data integrity ==========

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnAllRequiredFieldsInApiResponse() throws Exception {
        // Given - Insert failure with known values
        UUID notificationId = insertTestNotification();
        Instant failedAt = Instant.parse("2026-01-12T15:30:45Z");
        UUID failureId =
                insertFailure(notificationId, "test@example.com", "FEATURE_CREATED", "Known error message", failedAt);

        // When - Get failure by ID
        var result = mvc.get().uri("/api/admin/email-failures/{id}", failureId).exchange();

        // Then - Verify ALL fields with exact values
        assertThat(result).hasStatus(HttpStatus.OK);

        Map<String, Object> failure =
                objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});

        assertThat(failure.get("id")).isEqualTo(failureId.toString());
        assertThat(failure.get("notificationId")).isEqualTo(notificationId.toString());
        assertThat(failure.get("recipientEmail")).isEqualTo("test@example.com");
        assertThat(failure.get("eventType")).isEqualTo("FEATURE_CREATED");
        assertThat(failure.get("errorMessage")).isEqualTo("Known error message");
        // Parse and compare as Instant to avoid format-specific issues (milliseconds, timezone offset)
        Instant returnedFailedAt = Instant.parse((String) failure.get("failedAt"));
        assertThat(returnedFailedAt).isEqualTo(failedAt);
    }

    // ========== Helper methods ==========

    private UUID insertTestNotification() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO notifications (id, recipient_user_id, recipient_email, event_type, event_details, created_at, read, delivery_status)
                VALUES (?, 'testuser', 'testuser@example.com', 'FEATURE_CREATED', '{}', NOW(), false, 'PENDING')
                """,
                id);
        return id;
    }

    private UUID insertFailure(
            UUID notificationId, String recipientEmail, String eventType, String errorMessage, Instant failedAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO email_delivery_failures (id, notification_id, recipient_email, event_type, error_message, failed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                notificationId,
                recipientEmail,
                eventType,
                errorMessage,
                java.sql.Timestamp.from(failedAt));
        return id;
    }
}

package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.sql.Timestamp;
import java.time.Instant;
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
 * Integration tests for the admin email delivery failures API.
 */
@Sql("/test-data.sql")
class EmailDeliveryFailureAdminApiTest extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM email_delivery_failures");
        jdbcTemplate.execute("DELETE FROM notifications");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM email_delivery_failures");
        jdbcTemplate.execute("DELETE FROM notifications");
    }

    private UUID insertFailure(UUID notificationId, String recipientEmail, String eventType, String errorMessage) {
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
                Timestamp.from(Instant.now()));
        return id;
    }

    // ========== Security Tests ==========

    @Test
    void shouldReturn401WhenUnauthenticated() throws Exception {
        var response = mvc.get().uri("/api/admin/email-failures").exchange();
        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockOAuth2User(
            username = "regularuser",
            roles = {"USER"})
    void shouldReturn403WhenNotAdmin() throws Exception {
        var response = mvc.get().uri("/api/admin/email-failures").exchange();
        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldReturn403WhenNotAdminUsingJwt() throws Exception {
        var response = mvc.get()
                .uri("/api/admin/email-failures")
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "regularuser")))
                .exchange();
        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturn200WhenAdmin() throws Exception {
        var response = mvc.get().uri("/api/admin/email-failures").exchange();
        assertThat(response).hasStatus(HttpStatus.OK);
    }

    // ========== GET /api/admin/email-failures ==========

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnEmptyPageWhenNoFailures() throws Exception {
        var response = mvc.get().uri("/api/admin/email-failures").exchange();
        assertThat(response).hasStatus(HttpStatus.OK);

        String body = response.getResponse().getContentAsString();
        Map<String, Object> page = objectMapper.readValue(body, new TypeReference<>() {});
        List<?> content = (List<?>) page.get("content");
        assertThat(content).isEmpty();
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnPaginatedFailuresSortedByDateDesc() throws Exception {
        UUID notificationId = UUID.randomUUID();
        insertFailure(notificationId, "user1@example.com", "FEATURE_CREATED", "Error 1");
        insertFailure(notificationId, "user2@example.com", "FEATURE_UPDATED", "Error 2");
        insertFailure(notificationId, "user3@example.com", "RELEASE_CREATED", "Error 3");

        var response = mvc.get().uri("/api/admin/email-failures?size=10").exchange();
        assertThat(response).hasStatus(HttpStatus.OK);

        String body = response.getResponse().getContentAsString();
        Map<String, Object> page = objectMapper.readValue(body, new TypeReference<>() {});
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");

        assertThat(content).hasSize(3);
        // Verify sorted by date DESC (newest first) - failedAt of later inserts should be >= earlier ones
        String firstFailedAt = (String) content.get(0).get("failedAt");
        String lastFailedAt = (String) content.get(2).get("failedAt");
        assertThat(firstFailedAt).isGreaterThanOrEqualTo(lastFailedAt);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldFilterByDate() throws Exception {
        UUID notificationId = UUID.randomUUID();
        // Insert a failure today
        insertFailure(notificationId, "user1@example.com", "FEATURE_CREATED", "Error 1");

        // Insert a failure with explicit old date
        jdbcTemplate.update(
                """
                INSERT INTO email_delivery_failures (id, notification_id, recipient_email, event_type, error_message, failed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                notificationId,
                "old@example.com",
                "FEATURE_CREATED",
                "Old error",
                Timestamp.from(Instant.parse("2020-01-15T12:00:00Z")));

        // Filter by today's date (UTC)
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        var response = mvc.get().uri("/api/admin/email-failures?date=" + today).exchange();
        assertThat(response).hasStatus(HttpStatus.OK);

        String body = response.getResponse().getContentAsString();
        Map<String, Object> page = objectMapper.readValue(body, new TypeReference<>() {});
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");

        // Only today's failure should be returned, not the 2020 one
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("recipientEmail")).isEqualTo("user1@example.com");
    }

    // ========== GET /api/admin/email-failures/{id} ==========

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnFailureById() throws Exception {
        UUID notificationId = UUID.randomUUID();
        UUID failureId = insertFailure(notificationId, "user@example.com", "FEATURE_CREATED", "SMTP error");

        var response =
                mvc.get().uri("/api/admin/email-failures/{id}", failureId).exchange();
        assertThat(response).hasStatus(HttpStatus.OK);

        String body = response.getResponse().getContentAsString();
        Map<String, Object> failure = objectMapper.readValue(body, new TypeReference<>() {});

        assertThat(failure.get("id")).isEqualTo(failureId.toString());
        assertThat(failure.get("notificationId")).isEqualTo(notificationId.toString());
        assertThat(failure.get("recipientEmail")).isEqualTo("user@example.com");
        assertThat(failure.get("eventType")).isEqualTo("FEATURE_CREATED");
        assertThat(failure.get("errorMessage")).isEqualTo("SMTP error");
        assertThat(failure.get("failedAt")).isNotNull();
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturn404WhenFailureNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        var response =
                mvc.get().uri("/api/admin/email-failures/{id}", nonExistentId).exchange();
        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReturn401ForGetByIdWhenUnauthenticated() throws Exception {
        UUID id = UUID.randomUUID();
        var response = mvc.get().uri("/api/admin/email-failures/{id}", id).exchange();
        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"USER"})
    void shouldReturn403ForGetByIdWhenNotAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        var response = mvc.get().uri("/api/admin/email-failures/{id}", id).exchange();
        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    // ========== GET /api/admin/email-failures/notification/{notificationId} ==========

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnFailuresByNotificationId() throws Exception {
        UUID notificationId = UUID.randomUUID();
        insertFailure(notificationId, "user@example.com", "FEATURE_CREATED", "Error A");
        insertFailure(notificationId, "user@example.com", "FEATURE_CREATED", "Error B");
        // Insert failure for a different notification
        insertFailure(UUID.randomUUID(), "other@example.com", "FEATURE_UPDATED", "Other error");

        var response = mvc.get()
                .uri("/api/admin/email-failures/notification/{notificationId}", notificationId)
                .exchange();
        assertThat(response).hasStatus(HttpStatus.OK);

        String body = response.getResponse().getContentAsString();
        List<Map<String, Object>> failures = objectMapper.readValue(body, new TypeReference<>() {});

        assertThat(failures).hasSize(2);
        failures.forEach(f -> assertThat(f.get("notificationId")).isEqualTo(notificationId.toString()));
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnEmptyListWhenNoFailuresForNotification() throws Exception {
        UUID notificationId = UUID.randomUUID();

        var response = mvc.get()
                .uri("/api/admin/email-failures/notification/{notificationId}", notificationId)
                .exchange();
        assertThat(response).hasStatus(HttpStatus.OK);

        String body = response.getResponse().getContentAsString();
        List<?> failures = objectMapper.readValue(body, new TypeReference<>() {});
        assertThat(failures).isEmpty();
    }

    @Test
    void shouldReturn401ForGetByNotificationIdWhenUnauthenticated() throws Exception {
        UUID notificationId = UUID.randomUUID();
        var response = mvc.get()
                .uri("/api/admin/email-failures/notification/{notificationId}", notificationId)
                .exchange();
        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}

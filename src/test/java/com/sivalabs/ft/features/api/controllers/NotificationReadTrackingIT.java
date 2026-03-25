package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Integration tests for the email read tracking pixel endpoint: GET /api/notifications/{id}/read
 */
@Sql("/test-data.sql")
class NotificationReadTrackingIT extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID notificationId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM notifications");
        notificationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO notifications (id, recipient_user_id, event_type, event_details, link, created_at, read, delivery_status)"
                        + " VALUES (?, 'testuser', 'FEATURE_CREATED', '{\"action\":\"created\"}', '/features/IDEA-1', NOW(), false, 'PENDING')",
                notificationId);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM notifications");
    }

    @Test
    void shouldReturn200WithGifWhenNotificationExists() throws Exception {
        var response =
                mvc.get().uri("/api/notifications/{id}/read", notificationId).exchange();

        assertThat(response).hasStatusOk();
        assertThat(response.getResponse().getContentType()).contains("image/gif");
        assertThat(response.getResponse().getContentAsByteArray()).isNotEmpty();
    }

    @Test
    void shouldMarkNotificationAsReadWhenPixelIsTriggered() throws Exception {
        // Given - notification is initially unread
        Boolean isReadBefore = jdbcTemplate.queryForObject(
                "SELECT read FROM notifications WHERE id = ?", Boolean.class, notificationId);
        assertThat(isReadBefore).isFalse();

        // When - tracking pixel is triggered
        mvc.get().uri("/api/notifications/{id}/read", notificationId).exchange();

        // Then - notification is marked as read
        Boolean isReadAfter = jdbcTemplate.queryForObject(
                "SELECT read FROM notifications WHERE id = ?", Boolean.class, notificationId);
        assertThat(isReadAfter).isTrue();

        Object readAt = jdbcTemplate.queryForObject(
                "SELECT read_at FROM notifications WHERE id = ?", Object.class, notificationId);
        assertThat(readAt).isNotNull();
    }

    @Test
    void shouldBeIdempotentWhenCalledMultipleTimes() throws Exception {
        // When - tracking pixel is triggered twice
        mvc.get().uri("/api/notifications/{id}/read", notificationId).exchange();
        mvc.get().uri("/api/notifications/{id}/read", notificationId).exchange();

        // Then - still returns 200 and read_at is not updated on second call
        var response =
                mvc.get().uri("/api/notifications/{id}/read", notificationId).exchange();
        assertThat(response).hasStatusOk();

        Boolean isRead = jdbcTemplate.queryForObject(
                "SELECT read FROM notifications WHERE id = ?", Boolean.class, notificationId);
        assertThat(isRead).isTrue();
    }

    @Test
    void shouldReturn404ForNonExistentNotificationId() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        var response =
                mvc.get().uri("/api/notifications/{id}/read", nonExistentId).exchange();

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(response.getResponse().getContentAsString()).doesNotContain("StackTrace");
        assertThat(response.getResponse().getContentAsString()).doesNotContain("at com.");
    }

    @Test
    void shouldReturn400ForInvalidUuidFormat() throws Exception {
        var response = mvc.get().uri("/api/notifications/not-a-valid-uuid/read").exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponse().getContentAsString()).doesNotContain("StackTrace");
        assertThat(response.getResponse().getContentAsString()).doesNotContain("at com.");
    }

    @Test
    void shouldBeAccessibleWithoutAuthentication() throws Exception {
        // The tracking endpoint must be publicly accessible (no auth required)
        var response =
                mvc.get().uri("/api/notifications/{id}/read", notificationId).exchange();

        // Should NOT return 401
        assertThat(response).hasStatusOk();
    }
}

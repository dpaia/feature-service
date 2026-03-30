package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@Sql("/test-data.sql")
@ExtendWith(OutputCaptureExtension.class)
class EmailNotificationIntegrationTest extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM notifications");
    }

    @Test
    void shouldAllowTrackingEndpointWithoutAuthentication() {
        UUID notificationId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO notifications (id, recipient_user_id, recipient_email, event_type, event_details, created_at, read, delivery_status)
                        VALUES (?, 'testuser', 'testuser@example.com', 'FEATURE_CREATED', '{}', NOW(), false, 'PENDING')
                        """,
                notificationId);

        var result =
                mvc.get().uri("/api/notifications/{id}/read", notificationId).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result.getResponse().getContentType()).isEqualTo("image/gif");
    }

    @Test
    void shouldReturn404ForNonExistentNotificationId() {
        UUID nonExistentId = UUID.randomUUID();
        var result =
                mvc.get().uri("/api/notifications/{id}/read", nonExistentId).exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReturn400ForInvalidUuidFormat() throws Exception {
        var result = mvc.get()
                .uri("/api/notifications/{id}/read", "not-a-valid-uuid")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("IllegalArgumentException");
        assertThat(responseBody).doesNotContain("java.util.UUID");
        assertThat(responseBody).doesNotContain("Exception");
    }
}

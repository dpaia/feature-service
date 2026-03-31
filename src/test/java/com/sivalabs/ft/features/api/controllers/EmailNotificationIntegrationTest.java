package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import com.sivalabs.ft.features.api.models.UpdateReleasePayload;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import com.sivalabs.ft.features.testsupport.MockJavaMailSenderConfig;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.jdbc.Sql;

/**
 * Integration tests for the email notification system.
 * Tests email sending and delivery failure handling.
 */
@Sql("/test-data.sql")
@Import(MockJavaMailSenderConfig.class)
class EmailNotificationIntegrationTest extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JavaMailSender javaMailSender;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM notifications");
        reset(javaMailSender);
        // Return new MimeMessage for each call so we can inspect content
        when(javaMailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
    }

    @Test
    @WithMockOAuth2User(username = "alice")
    void shouldCreateNotificationEvenWhenEmailSendingFails() throws Exception {
        // Given - Configure mail sender to throw exception
        doThrow(new MailSendException("SMTP server unavailable"))
                .when(javaMailSender)
                .send(any(MimeMessage.class));

        CreateFeaturePayload payload = new CreateFeaturePayload(
                "intellij", "Email Failure Test", "Test notification despite email failure", null, "user1");

        // When - Create feature (email sending will fail)
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        // Then - Feature should still be created
        assertThat(result).hasStatus(HttpStatus.CREATED);

        // Notification should be saved in the database
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "user1");
        assertThat(count).isEqualTo(1);

        // Verify email send was attempted (will fail due to mock configuration)
        verify(javaMailSender, timeout(2000).times(1)).send(any(MimeMessage.class));

        // Verify delivery_status is updated to FAILED
        String deliveryStatus = jdbcTemplate.queryForObject(
                "SELECT delivery_status FROM notifications WHERE recipient_user_id = ?", String.class, "user1");
        assertThat(deliveryStatus)
                .as("delivery_status should be FAILED after email send failure")
                .isEqualTo("FAILED");
    }

    @Test
    @WithMockOAuth2User(username = "creator")
    void shouldStoreRecipientEmailInNotification() throws Exception {
        // Given
        CreateFeaturePayload payload = new CreateFeaturePayload(
                "intellij", "Recipient Email Test", "Test recipient email storage", null, "user1");

        // When - Create feature
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);

        // Then - Verify recipient_email column is populated
        String recipientEmail = jdbcTemplate.queryForObject(
                "SELECT recipient_email FROM notifications WHERE recipient_user_id = ?", String.class, "user1");

        assertThat(recipientEmail)
                .as("recipient_email should match email from users table")
                .isEqualTo("user1@example.com");

        // Verify delivery_status is updated to DELIVERED after successful email send
        String deliveryStatus = jdbcTemplate.queryForObject(
                "SELECT delivery_status FROM notifications WHERE recipient_user_id = ?", String.class, "user1");
        assertThat(deliveryStatus)
                .as("delivery_status should be DELIVERED after successful email send")
                .isEqualTo("DELIVERED");
    }

    /**
     * Prepares a release with a feature assigned to user1, advanced to IN_PROGRESS,
     * with notifications cleared and mail sender mock reset — ready for the final
     * status transition that triggers batch notifications.
     */
    private void prepareReleaseForBatchNotifications() throws Exception {
        CreateFeaturePayload featurePayload = new CreateFeaturePayload(
                "intellij", "Batch Test Feature", "Test batch notifications", "IDEA-2025.1-DRAFT", "user1");

        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(featurePayload))
                .exchange();
        assertThat(createResult).hasStatus(HttpStatus.CREATED);

        // Advance release through valid transitions: DRAFT -> PLANNED -> IN_PROGRESS
        for (ReleaseStatus status : List.of(ReleaseStatus.PLANNED, ReleaseStatus.IN_PROGRESS)) {
            var transitionResult = mvc.put()
                    .uri("/api/releases/{code}", "IDEA-2025.1-DRAFT")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new UpdateReleasePayload(null, status, null)))
                    .exchange();
            assertThat(transitionResult).hasStatus2xxSuccessful();
        }

        // Clear notifications from feature creation and status transitions
        jdbcTemplate.execute("DELETE FROM notifications");
        reset(javaMailSender);
        when(javaMailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
    }

    private List<Map<String, Object>> releaseAndGetBatchNotifications() throws Exception {
        UpdateReleasePayload releasePayload = new UpdateReleasePayload(null, ReleaseStatus.RELEASED, null);

        var updateResult = mvc.put()
                .uri("/api/releases/{code}", "IDEA-2025.1-DRAFT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();
        assertThat(updateResult).hasStatus2xxSuccessful();

        List<Map<String, Object>> notifications =
                jdbcTemplate.queryForList("SELECT delivery_status, recipient_user_id FROM notifications");
        assertThat(notifications).isNotEmpty();
        return notifications;
    }

    @Test
    @WithMockOAuth2User(username = "admin")
    void shouldMarkBatchNotificationsAsDeliveredWhenEmailSendingSucceeds() throws Exception {
        prepareReleaseForBatchNotifications();

        List<Map<String, Object>> notifications = releaseAndGetBatchNotifications();

        for (Map<String, Object> notification : notifications) {
            assertThat(notification.get("delivery_status"))
                    .as(
                            "delivery_status for recipient %s should be DELIVERED after successful batch email",
                            notification.get("recipient_user_id"))
                    .isEqualTo("DELIVERED");
        }
    }

    @Test
    @WithMockOAuth2User(username = "admin")
    void shouldMarkBatchNotificationsAsFailedWhenEmailSendingFails() throws Exception {
        prepareReleaseForBatchNotifications();

        doThrow(new MailSendException("SMTP server unavailable"))
                .when(javaMailSender)
                .send(any(MimeMessage.class));

        List<Map<String, Object>> notifications = releaseAndGetBatchNotifications();

        for (Map<String, Object> notification : notifications) {
            assertThat(notification.get("delivery_status"))
                    .as(
                            "delivery_status for recipient %s should be FAILED after batch email failure",
                            notification.get("recipient_user_id"))
                    .isEqualTo("FAILED");
        }
    }
}

package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import jakarta.mail.BodyPart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.jdbc.Sql;

/**
 * Integration tests for email notification system.
 * Tests email sending, delivery failure handling, and read tracking via pixel.
 */
@Sql("/test-data.sql")
@ExtendWith(OutputCaptureExtension.class)
@Import(EmailNotificationIntegrationTest.TestConfig.class)
class EmailNotificationIntegrationTest extends AbstractIT {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        JavaMailSender mockJavaMailSender() {
            JavaMailSender mailSender = mock(JavaMailSender.class);
            // Return new MimeMessage for each call so we can inspect content
            when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
            return mailSender;
        }
    }

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

    // ========== Test 1: Email is sent after notification creation ==========

    @Test
    @WithMockOAuth2User(username = "alice")
    void shouldSendEmailWhenNotificationIsCreated() throws Exception {
        // Given - alice creates a feature assigned to bob
        CreateFeaturePayload payload =
                new CreateFeaturePayload("intellij", "Email Test Feature", "Test email notification", null, "bob");

        // When - Create feature
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);

        // Then - Verify notification was created with recipient_email
        String recipientEmail = jdbcTemplate.queryForObject(
                "SELECT recipient_email FROM notifications WHERE recipient_user_id = ?", String.class, "bob");
        assertThat(recipientEmail).isEqualTo("bob@company.com");

        // Get notification ID from database
        UUID notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE recipient_user_id = ?", UUID.class, "bob");

        // Verify email was sent (with timeout for async implementations)
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, timeout(2000).times(1)).send(messageCaptor.capture());

        // Verify email body contains tracking pixel link
        MimeMessage sentMessage = messageCaptor.getValue();
        String emailContent = extractEmailContent(sentMessage);
        assertThat(emailContent)
                .as("Email should contain tracking pixel link with notification ID")
                .contains("/notifications/" + notificationId + "/read");
    }

    private String extractEmailContent(MimeMessage message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String) {
            return (String) content;
        } else if (content instanceof MimeMultipart multipart) {
            return extractFromMultipart(multipart);
        }
        // Fallback: try to get raw content via DataHandler
        if (message.getDataHandler() != null) {
            try (java.io.InputStream is = message.getDataHandler().getInputStream()) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private String extractFromMultipart(MimeMultipart multipart) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            Object partContent = part.getContent();
            if (partContent instanceof String) {
                sb.append(partContent);
            } else if (partContent instanceof MimeMultipart nested) {
                sb.append(extractFromMultipart(nested));
            }
        }
        return sb.toString();
    }

    // ========== Test 2: Tracking endpoint marks notification as read ==========

    @Test
    void shouldMarkNotificationAsReadWhenTrackingPixelIsLoaded() throws Exception {
        // Given - Create an unread notification directly in database
        UUID notificationId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO notifications (id, recipient_user_id, recipient_email, event_type, event_details, created_at, read, delivery_status)
                VALUES (?, 'testuser', 'testuser@example.com', 'FEATURE_CREATED', '{}', NOW(), false, 'PENDING')
                """,
                notificationId);

        // When - Load tracking pixel
        var result = mvc.get().uri("/notifications/{id}/read", notificationId).exchange();

        // Then - Should return 200 OK with GIF image
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result.getResponse().getContentType()).isEqualTo("image/gif");

        // Verify notification is marked as read in database
        Boolean isRead = jdbcTemplate.queryForObject(
                "SELECT read FROM notifications WHERE id = ?", Boolean.class, notificationId);
        assertThat(isRead).isTrue();

        // Verify read_at is populated
        java.sql.Timestamp readAt = jdbcTemplate.queryForObject(
                "SELECT read_at FROM notifications WHERE id = ?", java.sql.Timestamp.class, notificationId);
        assertThat(readAt).isNotNull();
    }

    // ========== Test 3: Tracking endpoint is accessible without authentication ==========

    @Test
    void shouldAllowTrackingEndpointWithoutAuthentication() throws Exception {
        // Given - Create notification (no @WithMockOAuth2User = unauthenticated request)
        UUID notificationId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO notifications (id, recipient_user_id, recipient_email, event_type, event_details, created_at, read, delivery_status)
                VALUES (?, 'testuser', 'testuser@example.com', 'FEATURE_CREATED', '{}', NOW(), false, 'PENDING')
                """,
                notificationId);

        // When - Access tracking endpoint without authentication
        var result = mvc.get().uri("/notifications/{id}/read", notificationId).exchange();

        // Then - Should return 200 OK (not 401 Unauthorized)
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result.getResponse().getContentType()).isEqualTo("image/gif");
    }

    // ========== Test 4: Email failure does not affect notification creation ==========

    @Test
    @WithMockOAuth2User(username = "alice")
    void shouldCreateNotificationEvenWhenEmailSendingFails(CapturedOutput output) throws Exception {
        // Given - Configure mail sender to throw exception
        doThrow(new MailSendException("SMTP server unavailable"))
                .when(javaMailSender)
                .send(any(MimeMessage.class));

        CreateFeaturePayload payload = new CreateFeaturePayload(
                "intellij", "Email Failure Test", "Test notification despite email failure", null, "bob");

        // When - Create feature (email sending will fail)
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        // Then - Feature should still be created
        assertThat(result).hasStatus(HttpStatus.CREATED);

        // Notification should be saved in database
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "bob");
        assertThat(count).isEqualTo(1);

        // Wait for async email sending attempt to complete
        verify(javaMailSender, timeout(2000).times(1)).send(any(MimeMessage.class));

        // Error should be logged with recipient email
        assertThat(output.getOut()).contains("bob@company.com");
    }

    // ========== Test 5: Tracking endpoint returns 404 for non-existent notification ==========

    @Test
    void shouldReturn404ForNonExistentNotificationId() throws Exception {
        // Given - Random UUID that doesn't exist
        UUID nonExistentId = UUID.randomUUID();

        // When - Try to load tracking pixel for non-existent notification
        var result = mvc.get().uri("/notifications/{id}/read", nonExistentId).exchange();

        // Then - Should return 404 Not Found
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ========== Test 6: Tracking endpoint returns 400 for invalid UUID format ==========

    @Test
    void shouldReturn400ForInvalidUuidFormat() throws Exception {
        // When - Try to load tracking pixel with invalid UUID
        var result =
                mvc.get().uri("/notifications/{id}/read", "not-a-valid-uuid").exchange();

        // Then - Should return 400 Bad Request
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);

        // Response should not expose internal details (stack traces)
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("IllegalArgumentException");
        assertThat(responseBody).doesNotContain("java.util.UUID");
        assertThat(responseBody).doesNotContain("Exception");
    }

    // ========== Test 7: Tracking endpoint is idempotent ==========

    @Test
    void shouldBeIdempotentWhenCalledMultipleTimes() throws Exception {
        // Given - Create an unread notification
        UUID notificationId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO notifications (id, recipient_user_id, recipient_email, event_type, event_details, created_at, read, delivery_status)
                VALUES (?, 'testuser', 'testuser@example.com', 'FEATURE_CREATED', '{}', NOW(), false, 'PENDING')
                """,
                notificationId);

        // When - Call tracking endpoint first time
        var firstResult =
                mvc.get().uri("/notifications/{id}/read", notificationId).exchange();

        assertThat(firstResult).hasStatus(HttpStatus.OK);

        // Get the read_at timestamp after first call
        java.sql.Timestamp firstReadAt = jdbcTemplate.queryForObject(
                "SELECT read_at FROM notifications WHERE id = ?", java.sql.Timestamp.class, notificationId);

        // When - Call tracking endpoint second time
        var secondResult =
                mvc.get().uri("/notifications/{id}/read", notificationId).exchange();

        // Then - Should still return 200 OK
        assertThat(secondResult).hasStatus(HttpStatus.OK);

        // read_at should NOT be updated (idempotent)
        java.sql.Timestamp secondReadAt = jdbcTemplate.queryForObject(
                "SELECT read_at FROM notifications WHERE id = ?", java.sql.Timestamp.class, notificationId);

        assertThat(secondReadAt).isEqualTo(firstReadAt);
    }

    // ========== Test 8: Notification contains recipient_email in database ==========

    @Test
    @WithMockOAuth2User(username = "creator")
    void shouldStoreRecipientEmailInNotification() throws Exception {
        // Given
        CreateFeaturePayload payload = new CreateFeaturePayload(
                "intellij", "Recipient Email Test", "Test recipient email storage", null, "recipient");

        // When - Create feature
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);

        // Then - Verify recipient_email column is populated
        String recipientEmail = jdbcTemplate.queryForObject(
                "SELECT recipient_email FROM notifications WHERE recipient_user_id = ?", String.class, "recipient");

        assertThat(recipientEmail)
                .as("recipient_email should match email from users table")
                .isEqualTo("recipient@company.com");
    }

    // ========== Test 9: Tracking endpoint returns valid GIF image ==========

    @Test
    void shouldReturnValidGifImage() throws Exception {
        // Given - Create notification
        UUID notificationId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO notifications (id, recipient_user_id, recipient_email, event_type, event_details, created_at, read, delivery_status)
                VALUES (?, 'testuser', 'testuser@example.com', 'FEATURE_CREATED', '{}', NOW(), false, 'PENDING')
                """,
                notificationId);

        // When - Load tracking pixel
        var result = mvc.get().uri("/notifications/{id}/read", notificationId).exchange();

        // Then - Should return valid GIF
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result.getResponse().getContentType()).isEqualTo("image/gif");

        // Verify response body is not empty (actual GIF content)
        byte[] content = result.getResponse().getContentAsByteArray();
        assertThat(content).isNotEmpty();
    }

    // ========== Test 10: XSS prevention - HTML should be escaped in email content ==========

    @Test
    @WithMockOAuth2User(username = "alice")
    void shouldEscapeHtmlInEmailContent() throws Exception {
        // Given - Create feature with potentially malicious HTML content
        String maliciousTitle = "<script>alert('XSS')</script>Malicious Feature";
        String maliciousDescription = "<img src=x onerror=alert('XSS')>Description";
        CreateFeaturePayload payload =
                new CreateFeaturePayload("intellij", maliciousTitle, maliciousDescription, null, "bob");

        // When - Create feature
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);

        // Then - Capture email and verify HTML is escaped (with timeout for async)
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, timeout(2000).times(1)).send(messageCaptor.capture());

        MimeMessage sentMessage = messageCaptor.getValue();
        String emailContent = extractEmailContent(sentMessage);

        // Verify malicious tags are escaped (not present as raw HTML)
        assertThat(emailContent).doesNotContain("<script>");
        assertThat(emailContent).doesNotContain("onerror=");
        assertThat(emailContent).doesNotContain("<img src=x");
    }

    // ========== Test 11: Email should include all required fields ==========

    @Test
    @WithMockOAuth2User(username = "alice")
    void shouldIncludeAllRequiredFieldsInEmail() throws Exception {
        // Given
        CreateFeaturePayload payload =
                new CreateFeaturePayload("intellij", "Complete Email Test", "Full email validation", null, "bob");

        // When - Create feature
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);

        // Capture email (with timeout for async implementations)
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, timeout(2000).times(1)).send(messageCaptor.capture());

        MimeMessage sentMessage = messageCaptor.getValue();
        String emailContent = extractEmailContent(sentMessage);

        // Verify required fields are present (per Task Description)
        // Note: Tracking pixel is verified in shouldSendEmailWhenNotificationIsCreated
        // 1. Link to affected entity
        assertThat(emailContent).as("Email should contain link to feature").contains("/features/");
        // 2. Event summary (feature title or code should be present)
        assertThat(emailContent)
                .as("Email should contain event summary with feature info")
                .satisfiesAnyOf(
                        content -> assertThat(content).contains("Complete Email Test"),
                        content -> assertThat(content).contains("IDEA-"));
        // 3. Actor (who triggered the event)
        assertThat(emailContent)
                .as("Email should contain actor who triggered the event")
                .contains("alice");
    }

    // ========== Test 12: No email sent when user not found in users table ==========

    @Test
    @WithMockOAuth2User(username = "alice")
    void shouldNotSendEmailWhenUserNotFoundInUsersTable() throws Exception {
        // Given - Create feature assigned to user that does NOT exist in users table
        CreateFeaturePayload payload = new CreateFeaturePayload(
                "intellij", "Feature for Unknown User", "Test unknown user handling", null, "nonexistent_user");

        // When - Create feature
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        // Then - Feature should be created successfully
        assertThat(result).hasStatus(HttpStatus.CREATED);

        // No email should be sent for user not in users table
        // Use after() to wait and verify no email was sent (for async implementations)
        verify(javaMailSender, after(1000).never()).send(any(MimeMessage.class));
    }

    // ========== Test 13: Batch notifications send multiple emails for release status change ==========

    @Test
    @WithMockOAuth2User(username = "admin")
    void shouldSendMultipleEmailsForBatchNotifications() throws Exception {
        // Given - Create release and multiple features assigned to different users

        // Create release
        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"productCode\":\"intellij\",\"code\":\"BATCH-TEST-REL\",\"description\":\"Batch Test Release\"}")
                .exchange();

        String releaseCode = "IDEA-BATCH-TEST-REL";

        // Create features assigned to different users (user1, user2)
        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateFeaturePayload("intellij", "Feature 1", "Desc 1", releaseCode, "user1")))
                .exchange();

        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateFeaturePayload("intellij", "Feature 2", "Desc 2", releaseCode, "user2")))
                .exchange();

        // Wait for feature creation emails to complete before resetting mock
        verify(javaMailSender, timeout(2000).times(2)).send(any(MimeMessage.class));

        // Now safe to reset - feature creation emails are done
        jdbcTemplate.execute("DELETE FROM notifications");
        reset(javaMailSender);
        when(javaMailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));

        // Transition release: DRAFT → PLANNED → IN_PROGRESS → RELEASED
        mvc.put()
                .uri("/api/releases/{code}", releaseCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Planned\",\"status\":\"PLANNED\"}")
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", releaseCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"In Progress\",\"status\":\"IN_PROGRESS\"}")
                .exchange();

        // When - Update to RELEASED status (triggers batch notifications)
        var result = mvc.put()
                .uri("/api/releases/{code}", releaseCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Released\",\"status\":\"RELEASED\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);

        // Then - Verify multiple notifications created (for user1 and user2, not admin who made the update)
        Integer totalNotifications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalNotifications)
                .as("Should create notifications for both feature assignees")
                .isEqualTo(2);

        // Verify emails were sent for batch notifications (with timeout for async)
        verify(javaMailSender, timeout(2000).times(totalNotifications)).send(any(MimeMessage.class));
    }

    // ========== Test 14: Tracking pixel returns Cache-Control header ==========

    @Test
    void shouldReturnCacheControlHeaderForTrackingPixel() throws Exception {
        // Given - Create notification
        UUID notificationId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO notifications (id, recipient_user_id, recipient_email, event_type, event_details, created_at, read, delivery_status)
                VALUES (?, 'testuser', 'testuser@example.com', 'FEATURE_CREATED', '{}', NOW(), false, 'PENDING')
                """,
                notificationId);

        // When - Load tracking pixel
        var result = mvc.get().uri("/notifications/{id}/read", notificationId).exchange();

        // Then - Cache-Control must prevent caching (required for tracking to work correctly)
        String cacheControl = result.getResponse().getHeader("Cache-Control");
        assertThat(cacheControl)
                .as("Cache-Control header must be present to prevent pixel caching")
                .isNotNull()
                .satisfiesAnyOf(s -> assertThat(s.toLowerCase()).contains("no-cache"), s -> assertThat(s.toLowerCase())
                        .contains("no-store"));
    }
}

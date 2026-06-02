package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.testsupport.MockJavaMailSenderConfig;
import jakarta.mail.BodyPart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

@Import(MockJavaMailSenderConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class EmailNotificationIntegrationTests extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JavaMailSender javaMailSender;

    @BeforeEach
    void configureMailSender() {
        reset(javaMailSender);
        when(javaMailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    @WithMockOAuth2User(username = "alice")
    void shouldSendEmailWhenFeatureNotificationIsCreated() throws Exception {
        var payload =
                """
                {
                    "productCode": "intellij",
                    "releaseCode": "IDEA-2023.3.8",
                    "title": "New Feature",
                    "description": "New feature description",
                    "assignedTo": "bob"
                }
                """;

        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String notificationId = jdbcTemplate.queryForObject(
                "select id::text from notifications where recipient_user_id = ?", String.class, "bob");
        assertThat(notificationId).isNotBlank();
        assertThat(jdbcTemplate.queryForObject(
                        "select recipient_email from notifications where id = ?::uuid", String.class, notificationId))
                .isEqualTo("bob@example.com");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, timeout(2000)).send(captor.capture());

        MimeMessage message = captor.getValue();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("bob@example.com");
        assertThat(message.getSubject()).isEqualTo("New Feature Created");
        assertThat(extractContent(message))
                .contains("Summary: Feature")
                .contains("Actor: alice")
                .contains("/api/features/")
                .contains("http://localhost:8081/notifications/" + notificationId + "/read");
    }

    @Test
    @WithMockOAuth2User(username = "admin")
    void shouldSendEmailsWhenReleaseNotificationIsCreated() {
        var payload =
                """
                {
                    "productCode": "intellij",
                    "code": "2025.1",
                    "description": "IntelliJ IDEA 2025.1"
                }
                """;

        var result = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        Integer notifications = jdbcTemplate.queryForObject(
                "select count(*) from notifications where event_type = 'RELEASE_CREATED'", Integer.class);
        assertThat(notifications).isEqualTo(7);
        Integer selfNotifications = jdbcTemplate.queryForObject(
                "select count(*) from notifications where recipient_user_id = 'admin'", Integer.class);
        assertThat(selfNotifications).isZero();
        verify(javaMailSender, timeout(2000).times(7)).send(any(MimeMessage.class));
    }

    @Test
    @WithMockOAuth2User(username = "alice")
    void shouldCreateNotificationWhenEmailSendingFails(CapturedOutput output) {
        doThrow(new MailSendException("smtp unavailable")).when(javaMailSender).send(any(MimeMessage.class));
        var payload =
                """
                {
                    "productCode": "intellij",
                    "releaseCode": "IDEA-2023.3.8",
                    "title": "Failure Still Creates Notification",
                    "description": "Email failure must not block creation",
                    "assignedTo": "bob"
                }
                """;

        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        await().untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "select delivery_status from notifications where recipient_user_id = ?", String.class, "bob"))
                .isEqualTo("FAILED"));
        assertThat(output)
                .contains("Email delivery failed - recipient: bob@example.com")
                .contains("eventType: FEATURE_CREATED")
                .contains("smtp unavailable");
    }

    @Test
    void shouldMarkNotificationAsReadWithTrackingPixel() {
        UUID notificationId = insertNotification(false);

        var result = mvc.get().uri("/notifications/{id}/read", notificationId).exchange();

        assertThat(result).hasStatusOk();
        var response = result.getMvcResult().getResponse();
        assertThat(response.getContentType()).isEqualTo(MediaType.IMAGE_GIF_VALUE);
        assertThat(new String(response.getContentAsByteArray(), 0, 3, StandardCharsets.US_ASCII))
                .isEqualTo("GIF");
        assertThat(jdbcTemplate.queryForObject(
                        "select read from notifications where id = ?", Boolean.class, notificationId))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "select read_at from notifications where id = ?", Timestamp.class, notificationId))
                .isNotNull();
    }

    @Test
    void shouldReturn404WhenTrackingNotificationDoesNotExist() throws Exception {
        var result =
                mvc.get().uri("/notifications/{id}/read", UUID.randomUUID()).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result.getMvcResult().getResponse().getContentAsString()).contains("Notification not found");
    }

    @Test
    void shouldReturn400ForInvalidTrackingNotificationId() throws Exception {
        var result = mvc.get().uri("/notifications/not-a-uuid/read").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result.getMvcResult().getResponse().getContentAsString())
                .contains("Invalid notification ID format")
                .doesNotContain("IllegalArgumentException")
                .doesNotContain("java.lang");
    }

    @Test
    void shouldNotChangeReadTimestampOnRepeatedTrackingPixelCalls() {
        UUID notificationId = insertNotification(false);

        mvc.get().uri("/notifications/{id}/read", notificationId).exchange();
        Timestamp firstReadAt = jdbcTemplate.queryForObject(
                "select read_at from notifications where id = ?", Timestamp.class, notificationId);

        mvc.get().uri("/notifications/{id}/read", notificationId).exchange();
        Timestamp secondReadAt = jdbcTemplate.queryForObject(
                "select read_at from notifications where id = ?", Timestamp.class, notificationId);

        assertThat(secondReadAt).isEqualTo(firstReadAt);
    }

    private UUID insertNotification(boolean read) {
        UUID notificationId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into notifications
                    (id, recipient_user_id, recipient_email, event_type, event_details, link, created_at, read, delivery_status)
                values (?, ?, ?, ?, ?, ?, current_timestamp, ?, ?)
                """,
                notificationId,
                "bob",
                "bob@example.com",
                "FEATURE_CREATED",
                "Summary: Feature was created. Actor: alice. Link: /api/features/IDEA-999",
                "/api/features/IDEA-999",
                read,
                "PENDING");
        return notificationId;
    }

    private String extractContent(MimeMessage message) throws Exception {
        return extractContent(message.getContent());
    }

    private String extractContent(Object content) throws Exception {
        if (content instanceof MimeMultipart multipart) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                builder.append(extractContent(bodyPart.getContent()));
            }
            return builder.toString();
        }
        return String.valueOf(content);
    }
}

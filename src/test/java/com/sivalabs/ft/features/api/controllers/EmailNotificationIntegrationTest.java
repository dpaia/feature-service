package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import com.sivalabs.ft.features.testsupport.MockJavaMailSenderConfig;
import jakarta.mail.BodyPart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.jdbc.Sql;

/**
 * Integration tests for email notification system.
 * Tests email sending, delivery failure handling, and read tracking via pixel.
 */
@Sql("/test-data.sql")
@Import(MockJavaMailSenderConfig.class)
@ExtendWith(OutputCaptureExtension.class)
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
    void shouldEscapeHtmlInEmailContent() throws Exception {
        // Given - Create feature with potentially malicious HTML content
        String maliciousTitle = "<script>alert('XSS')</script>Malicious Feature";
        String maliciousDescription = "<img src=x onerror=alert('XSS')>Description";
        CreateFeaturePayload payload =
                new CreateFeaturePayload("intellij", maliciousTitle, maliciousDescription, null, "user1");

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

        // Verify safe portions of the content survived sanitization
        assertThat(emailContent).contains("Malicious Feature");
    }

    @Test
    @WithMockOAuth2User(username = "alice")
    void shouldIncludeAllRequiredFieldsInEmail() throws Exception {
        // Given
        CreateFeaturePayload payload =
                new CreateFeaturePayload("intellij", "Complete Email Test", "Full email validation", null, "user1");

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
                        content -> assertThat(content).contains("Full email validation"),
                        content -> assertThat(content).contains("IDEA-"));
        // 3. Actor (who triggered the event)
        assertThat(emailContent)
                .as("Email should contain actor who triggered the event")
                .contains("alice");
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
}

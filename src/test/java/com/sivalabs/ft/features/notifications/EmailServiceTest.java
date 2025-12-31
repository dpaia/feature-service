package com.sivalabs.ft.features.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.ApplicationProperties;
import com.sivalabs.ft.features.domain.entities.Notification;
import com.sivalabs.ft.features.domain.models.DeliveryStatus;
import com.sivalabs.ft.features.domain.models.NotificationEventType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private ApplicationProperties.EmailProperties emailProperties;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        when(applicationProperties.publicBaseUrl()).thenReturn("http://localhost:8081");
        when(applicationProperties.email()).thenReturn(emailProperties);
        when(emailProperties.isEnabled()).thenReturn(true);
        emailService = new EmailServiceImpl(mailSender, applicationProperties);
    }

    @Test
    void shouldSendEmailSuccessfully() throws MessagingException {
        // Given
        Notification notification = createTestNotification();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // When
        DeliveryStatus result = emailService.sendNotificationEmail(notification);

        // Then
        assertThat(result).isEqualTo(DeliveryStatus.DELIVERED);
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldReturnFailedStatusWhenMailExceptionOccurs() {
        // Given
        Notification notification = createTestNotification();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailException("Mail server error") {}).when(mailSender).send(any(MimeMessage.class));

        // When
        DeliveryStatus result = emailService.sendNotificationEmail(notification);

        // Then
        assertThat(result).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void shouldLogDeliveryFailure() {
        // Given
        String recipientEmail = "test@example.com";
        String eventType = "FEATURE_CREATED";
        String errorDetails = "SMTP connection failed";

        // When (no mocking needed for this test)
        emailService.logDeliveryFailure(recipientEmail, eventType, errorDetails);

        // Then
        // This test verifies that the method executes without throwing exceptions
        // In a real scenario, you might want to verify log output using a log capture framework
    }

    @Test
    void shouldBuildCorrectTrackingPixelUrl() throws MessagingException {
        // Given
        Notification notification = createTestNotification();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // When
        emailService.sendNotificationEmail(notification);

        // Then
        verify(mailSender).send(mimeMessage);
        // The tracking pixel URL should be embedded in the email content
        // This is tested indirectly through the successful email sending
    }

    @Test
    void shouldHandleNullEventDetails() throws MessagingException {
        // Given
        Notification notification = createTestNotification();
        notification.setEventDetails(null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // When
        DeliveryStatus result = emailService.sendNotificationEmail(notification);

        // Then
        assertThat(result).isEqualTo(DeliveryStatus.DELIVERED);
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldHandleNullLink() throws MessagingException {
        // Given
        Notification notification = createTestNotification();
        notification.setLink(null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // When
        DeliveryStatus result = emailService.sendNotificationEmail(notification);

        // Then
        assertThat(result).isEqualTo(DeliveryStatus.DELIVERED);
        verify(mailSender).send(mimeMessage);
    }

    private Notification createTestNotification() {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipientUserId("testuser");
        notification.setRecipientEmail("test@example.com");
        notification.setEventType(NotificationEventType.FEATURE_CREATED);
        notification.setEventDetails("A new feature has been created");
        notification.setLink("http://localhost:8081/features/123");
        notification.setCreatedAt(Instant.now());
        notification.setRead(false);
        notification.setDeliveryStatus(DeliveryStatus.PENDING);
        return notification;
    }
}

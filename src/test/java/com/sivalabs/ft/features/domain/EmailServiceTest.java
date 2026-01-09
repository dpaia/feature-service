package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.sivalabs.ft.features.config.EmailProperties;
import com.sivalabs.ft.features.domain.entities.Notification;
import com.sivalabs.ft.features.domain.models.NotificationEventType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private EmailDeliveryLogger emailDeliveryLogger;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailProperties emailProperties;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        // Use lenient() to avoid unnecessary stubbing errors
        lenient().when(emailProperties.isEnabled()).thenReturn(false); // Disable actual email sending in tests
        lenient().when(emailProperties.getFrom()).thenReturn("test@example.com");

        emailService = new EmailServiceImpl(emailDeliveryLogger, mailSender, emailProperties);
        ReflectionTestUtils.setField(emailService, "publicBaseUrl", "http://localhost:8081");
    }

    @Test
    void shouldGenerateCorrectTrackingPixelUrl() {
        // Given
        String notificationId = UUID.randomUUID().toString();

        // When
        String trackingUrl = emailService.generateTrackingPixelUrl(notificationId);

        // Then
        assertThat(trackingUrl).isEqualTo("http://localhost:8081/api/notifications/" + notificationId + "/read");
    }

    @Test
    void shouldReturnTrueWhenEmailDisabledAndRecipientEmailIsNull() {
        // Given
        Notification notification = createTestNotification();
        notification.setRecipientEmail(null);

        // When
        boolean result = emailService.sendNotificationEmail(notification);

        // Then
        assertThat(result).isTrue(); // Email is disabled, so it returns true
    }

    @Test
    void shouldReturnTrueWhenEmailDisabledAndRecipientEmailIsEmpty() {
        // Given
        Notification notification = createTestNotification();
        notification.setRecipientEmail("");

        // When
        boolean result = emailService.sendNotificationEmail(notification);

        // Then
        assertThat(result).isTrue(); // Email is disabled, so it returns true
    }

    @Test
    void shouldReturnTrueWhenEmailDisabledAndRecipientEmailIsBlank() {
        // Given
        Notification notification = createTestNotification();
        notification.setRecipientEmail("   ");

        // When
        boolean result = emailService.sendNotificationEmail(notification);

        // Then
        assertThat(result).isTrue(); // Email is disabled, so it returns true
    }

    @Test
    void shouldReturnTrueWhenEmailIsDisabled() {
        // Given
        Notification notification = createTestNotification();
        notification.setRecipientEmail("test@example.com");

        // When
        boolean result = emailService.sendNotificationEmail(notification);

        // Then
        assertThat(result).isTrue(); // Email is disabled in tests, so it returns true
    }

    @Test
    void shouldReturnFalseWhenEmailEnabledButRecipientEmailIsNull() {
        // Given
        when(emailProperties.isEnabled()).thenReturn(true);
        Notification notification = createTestNotification();
        notification.setRecipientEmail(null);

        // When
        boolean result = emailService.sendNotificationEmail(notification);

        // Then
        assertThat(result).isFalse(); // Email enabled but no recipient email
    }

    @Test
    void shouldReturnFalseWhenEmailEnabledButRecipientEmailIsEmpty() {
        // Given
        when(emailProperties.isEnabled()).thenReturn(true);
        Notification notification = createTestNotification();
        notification.setRecipientEmail("");

        // When
        boolean result = emailService.sendNotificationEmail(notification);

        // Then
        assertThat(result).isFalse(); // Email enabled but no recipient email
    }

    private Notification createTestNotification() {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipientUserId("testuser");
        notification.setEventType(NotificationEventType.FEATURE_CREATED);
        notification.setEventDetails("Test feature created");
        notification.setLink("http://localhost:8081/features/123");
        notification.setCreatedAt(Instant.now());
        notification.setRead(false);
        return notification;
    }
}

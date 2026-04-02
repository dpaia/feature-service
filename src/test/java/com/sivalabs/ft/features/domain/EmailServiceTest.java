package com.sivalabs.ft.features.domain;

import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.ApplicationProperties;
import com.sivalabs.ft.features.domain.dtos.NotificationDto;
import com.sivalabs.ft.features.domain.models.DeliveryStatus;
import com.sivalabs.ft.features.domain.models.NotificationEventType;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ApplicationProperties.EventsProperties eventsProps =
                new ApplicationProperties.EventsProperties("new_features", "updated_features", "deleted_features");
        ApplicationProperties props = new ApplicationProperties(eventsProps, "http://localhost:8081");
        emailService = new EmailService(mailSender, props);
    }

    @Test
    void shouldSendEmailWithTrackingPixel() throws Exception {
        // Given
        UUID notificationId = UUID.randomUUID();
        NotificationDto notification = new NotificationDto(
                notificationId,
                "testuser",
                "testuser@example.com",
                NotificationEventType.FEATURE_CREATED,
                "{\"action\":\"created\",\"actor\":\"creator\",\"featureCode\":\"IDEA-1\",\"title\":\"Test Feature\"}",
                "/features/IDEA-1",
                Instant.now(),
                false,
                null,
                DeliveryStatus.PENDING);

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // When
        emailService.sendNotificationEmail(notification, "testuser@example.com");

        // Then
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldHandleNullEventDetails() throws Exception {
        // Given
        UUID notificationId = UUID.randomUUID();
        NotificationDto notification = new NotificationDto(
                notificationId,
                "testuser",
                "testuser@example.com",
                NotificationEventType.RELEASE_UPDATED,
                null,
                "/releases/IDEA-2024.1",
                Instant.now(),
                false,
                null,
                DeliveryStatus.PENDING);

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // When
        emailService.sendNotificationEmail(notification, "testuser@example.com");

        // Then
        verify(mailSender).send(mimeMessage);
    }
}

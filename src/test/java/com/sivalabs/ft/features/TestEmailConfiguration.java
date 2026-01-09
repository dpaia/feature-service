package com.sivalabs.ft.features;

import static org.mockito.Mockito.mock;

import com.sivalabs.ft.features.config.EmailProperties;
import com.sivalabs.ft.features.domain.EmailDeliveryLogger;
import com.sivalabs.ft.features.domain.EmailService;
import com.sivalabs.ft.features.domain.entities.Notification;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration
public class TestEmailConfiguration {

    @Bean
    @Primary
    public EmailService testEmailService() {
        return new EmailService() {
            @Override
            public boolean sendNotificationEmail(Notification notification) {
                // Mock implementation for tests - just return true
                return true;
            }

            @Override
            public String generateTrackingPixelUrl(String notificationId) {
                // Mock implementation for tests
                return "http://localhost:8080/api/notifications/" + notificationId + "/read";
            }
        };
    }

    @Bean
    @Primary
    public EmailProperties testEmailProperties() {
        EmailProperties properties = new EmailProperties();
        properties.setEnabled(false);
        properties.setFrom("test@example.com");
        return properties;
    }

    @Bean
    @Primary
    public EmailDeliveryLogger testEmailDeliveryLogger() {
        return new EmailDeliveryLogger() {
            @Override
            public void logDeliveryFailure(String recipientEmail, String eventType, String errorDetails) {
                // Mock implementation for tests
                System.out.println("Test: Email delivery failed for " + recipientEmail + ": " + errorDetails);
            }
        };
    }

    @Bean
    public JavaMailSender testJavaMailSender() {
        return mock(JavaMailSender.class);
    }

    @Bean
    public JwtDecoder testJwtDecoder() {
        return mock(JwtDecoder.class);
    }
}

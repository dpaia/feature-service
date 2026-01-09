package com.sivalabs.ft.features;

import com.sivalabs.ft.features.config.EmailProperties;
import com.sivalabs.ft.features.domain.EmailDeliveryLogger;
import com.sivalabs.ft.features.domain.EmailService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Global test configuration that provides mock beans for all tests.
 * This ensures that tests can run without requiring actual email infrastructure.
 */
@TestConfiguration
public class GlobalTestConfiguration {

    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        return Mockito.mock(JavaMailSender.class);
    }

    @Bean
    public EmailService emailService() {
        return Mockito.mock(EmailService.class);
    }

    @Bean
    public EmailProperties emailProperties() {
        EmailProperties properties = new EmailProperties();
        properties.setFrom("test@example.com");
        properties.setEnabled(false); // Disable email sending in tests
        return properties;
    }

    @Bean
    public EmailDeliveryLogger emailDeliveryLogger() {
        return Mockito.mock(EmailDeliveryLogger.class);
    }

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return Mockito.mock(JwtDecoder.class);
    }

    @Bean
    @Primary
    public ApplicationProperties applicationProperties() {
        ApplicationProperties.EventsProperties eventsProperties = new ApplicationProperties.EventsProperties(
                "new_features_test", "updated_features_test", "deleted_features_test");
        return new ApplicationProperties(eventsProperties);
    }
}

package com.sivalabs.ft.features.testsupport;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Test configuration that provides a mock JavaMailSender.
 * Import this config in tests that trigger email sending to avoid real SMTP connections.
 */
@TestConfiguration
public class MockJavaMailSenderConfig {
    @Bean
    @Primary
    public JavaMailSender mockJavaMailSender() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        return mailSender;
    }
}

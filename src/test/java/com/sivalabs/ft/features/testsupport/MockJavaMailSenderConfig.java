package com.sivalabs.ft.features.testsupport;

import static org.mockito.Mockito.mock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

@TestConfiguration
public class MockJavaMailSenderConfig {

    @Bean
    @Primary
    public JavaMailSender mockJavaMailSender() {
        return mock(JavaMailSender.class);
    }
}

package com.sivalabs.ft.features;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AbstractIT.GlobalMailMockConfig.class})
@Sql(scripts = {"/test-data.sql"})
public abstract class AbstractIT {
    @Autowired
    protected MockMvcTester mvc;

    /**
     * Global mock for JavaMailSender to prevent real SMTP connections in tests.
     * Individual tests can override behavior using reset() and when().
     */
    @TestConfiguration
    static class GlobalMailMockConfig {
        @Bean
        @Primary
        JavaMailSender mockJavaMailSender() {
            JavaMailSender mailSender = mock(JavaMailSender.class);
            when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
            return mailSender;
        }
    }
}

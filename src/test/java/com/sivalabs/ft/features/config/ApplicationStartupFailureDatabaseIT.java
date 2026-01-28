package com.sivalabs.ft.features.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sivalabs.ft.features.FeatureServiceApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test to verify application startup fails when database is
 * unavailable.
 * Uses Testcontainers for Kafka but NO database container.
 *
 * This test verifies Requirement: "Database unavailable → FAIL (throw
 * exception)"
 */
@DisplayName("Startup Failure - Database Unavailable Integration Test")
@Testcontainers
class ApplicationStartupFailureDatabaseIT {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupFailureDatabaseIT.class);

    private ConfigurableApplicationContext context;

    @TestConfiguration
    static class KafkaOnlyTestConfiguration {

        static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

        static {
            kafka.start();
        }

        @Bean
        @ServiceConnection
        KafkaContainer kafkaContainer() {
            return kafka;
        }
    }

    @AfterEach
    void cleanup() {
        if (context != null && context.isActive()) {
            context.close();
        }
    }

    @Test
    @DisplayName("Should fail to start when database is unavailable")
    void shouldFailToStartWithoutDatabase() {
        // Given - No database container is started, only Kafka
        log.info("Testing application startup without database...");

        // When - Attempting to start the application programmatically
        // Then - Application startup should fail with exception
        assertThatThrownBy(() -> {
                    SpringApplication app =
                            new SpringApplication(FeatureServiceApplication.class, KafkaOnlyTestConfiguration.class);

                    app.setAdditionalProfiles("test");

                    context = app.run(
                            "--ft.lifecycle.lifecycle-enabled=true",
                            "--ft.lifecycle.shutdown-timeout-millis=30000",
                            "--ft.lifecycle.kafka-flush-timeout-millis=10000",
                            "--spring.datasource.url=jdbc:postgresql://localhost:54321/nonexistent",
                            "--spring.datasource.username=test",
                            "--spring.datasource.password=test");
                })
                .as("Application should fail to start when database is unavailable")
                .isInstanceOf(Exception.class)
                .satisfies(exception -> {
                    String message = exception.getMessage();
                    log.info("Application startup failed as expected: {}", message);

                    assertThat(message)
                            .as("Error message should indicate database connectivity issue")
                            .satisfiesAnyOf(
                                    msg -> assertThat(msg.toLowerCase()).contains("connection"),
                                    msg -> assertThat(msg.toLowerCase()).contains("database"),
                                    msg -> assertThat(msg.toLowerCase()).contains("datasource"),
                                    msg -> assertThat(msg.toLowerCase()).contains("postgresql"),
                                    msg -> assertThat(msg.toLowerCase()).contains("jdbc"));
                });

        log.info("Verified: Application fails to start when database is unavailable");
    }
}

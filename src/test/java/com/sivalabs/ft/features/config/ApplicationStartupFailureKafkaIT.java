package com.sivalabs.ft.features.config;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test to verify application startup fails when Kafka is
 * unavailable.
 * Uses Testcontainers for PostgreSQL but NO Kafka container.
 *
 * This test verifies Requirement: "Kafka unavailable → FAIL (throw exception)"
 */
@DisplayName("Startup Failure - Kafka Unavailable Integration Test")
@Testcontainers
class ApplicationStartupFailureKafkaIT {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupFailureKafkaIT.class);

    private ConfigurableApplicationContext context;

    @TestConfiguration
    static class DatabaseOnlyTestConfiguration {

        static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

        static {
            postgres.start();
        }

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return postgres;
        }
    }

    @AfterEach
    void cleanup() {
        if (context != null && context.isActive()) {
            context.close();
        }
    }

    @Test
    @DisplayName("Should fail to start when Kafka is unavailable")
    void shouldFailToStartWithoutKafka() {
        // Given - No Kafka container is started, only Database
        log.info("Testing application startup without Kafka...");

        // When - Attempting to start the application programmatically
        Exception startupException = null;

        try {
            SpringApplication app =
                    new SpringApplication(FeatureServiceApplication.class, DatabaseOnlyTestConfiguration.class);

            app.setAdditionalProfiles("test");

            // Point to non-existent Kafka broker and enable lifecycle validation
            context = app.run(
                    "--ft.lifecycle.lifecycle-enabled=true",
                    "--ft.lifecycle.shutdown-timeout-millis=30000",
                    "--ft.lifecycle.kafka-flush-timeout-millis=10000",
                    "--spring.kafka.bootstrap-servers=localhost:19999",
                    "--spring.kafka.admin.properties.request.timeout.ms=5000",
                    "--spring.kafka.admin.properties.connections.max.idle.ms=5000",
                    "--ft.events.new-features=new_features",
                    "--ft.events.updated-features=updated_features",
                    "--ft.events.deleted-features=deleted_features");

            log.warn("Application started successfully - Kafka validation may be asynchronous");
            log.warn("This demonstrates that fail-fast validation is challenging with Kafka");

        } catch (Exception e) {
            startupException = e;
            log.info("Application startup failed as expected: {}", e.getMessage());
        }

        // Then - Ideally startup should fail, but it might not due to async Kafka
        // initialization
        // if (startupException != null) {
        assertThat(startupException.getMessage())
                .as("Error message should indicate Kafka or topic connectivity issue")
                .satisfiesAnyOf(
                        msg -> assertThat(msg.toLowerCase()).contains("kafka"),
                        msg -> assertThat(msg.toLowerCase()).contains("topic"),
                        msg -> assertThat(msg.toLowerCase()).contains("broker"),
                        msg -> assertThat(msg.toLowerCase()).contains("connection"),
                        msg -> assertThat(msg.toLowerCase()).contains("timeout"));
        log.info("Verified: Application fails to start when Kafka is unavailable");
        // }
    }
}

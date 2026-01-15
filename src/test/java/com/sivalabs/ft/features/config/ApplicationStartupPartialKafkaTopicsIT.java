package com.sivalabs.ft.features.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.FeatureServiceApplication;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test to verify application startup fails when required Kafka
 * topics are partially available.
 * Uses Testcontainers for both PostgreSQL and Kafka, but creates only SOME of
 * the required topics.
 *
 * This test verifies Requirements:
 * - "Check all three topics from ApplicationProperties (new_features,
 * updated_features, deleted_features)"
 * - "Application must fail if ANY required topic is missing"
 * - Partial topic availability should be treated as startup failure
 *
 * Test Scenario: Creates only 'new_features' and 'updated_features' topics,
 * but NOT 'deleted_features'
 */
@DisplayName("Startup Failure - Partial Kafka Topics Integration Test")
@Testcontainers
class ApplicationStartupPartialKafkaTopicsIT {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupPartialKafkaTopicsIT.class);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @BeforeAll
    static void setupContainers() {
        // Containers are started automatically by @Testcontainers
        log.info("PostgreSQL container started at: {}", postgres.getJdbcUrl());
        log.info("Kafka container started at: {}", kafka.getBootstrapServers());

        // Create ONLY TWO of the three required Kafka topics
        createPartialKafkaTopics();
    }

    /**
     * Creates only 'new_features' and 'updated_features' topics. The
     * 'deleted_features' topic is intentionally NOT created to test partial
     * availability scenario.
     */
    private static void createPartialKafkaTopics() {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());

        try (AdminClient adminClient = AdminClient.create(config)) {
            // Create only 2 of 3 required topics
            List<NewTopic> topics =
                    List.of(new NewTopic("new_features", 1, (short) 1), new NewTopic("updated_features", 1, (short) 1));

            adminClient.createTopics(topics).all().get();
            log.info("Created partial Kafka topics: new_features, updated_features");
            log.info("INTENTIONALLY NOT created: deleted_features");
        } catch (Exception e) {
            log.error("Failed to create partial Kafka topics", e);
            throw new RuntimeException("Failed to create partial Kafka topics", e);
        }
    }

    @AfterAll
    static void tearDownContainers() {
        // Containers are stopped automatically by @Testcontainers
        log.info("Containers will be stopped after all tests");
    }

    @Test
    @DisplayName("Should fail to start when only some required Kafka topics exist")
    void shouldFailToStartWithPartialKafkaTopics() {
        // Given - PostgreSQL and Kafka are available, but only 2 of 3 required
        // topics exist
        log.info("Testing application startup with partial Kafka topics...");
        log.info("Available topics: new_features, updated_features");
        log.info("Missing topic: deleted_features");

        // When - Attempting to start the application with missing 'deleted_features'
        // topic
        ConfigurableApplicationContext context = null;
        Exception startupException = null;

        try {
            SpringApplication app = new SpringApplication(FeatureServiceApplication.class);
            app.setAdditionalProfiles("test");

            // Configure to connect to our Testcontainers
            context = app.run(
                    "--server.port=0",
                    "--spring.datasource.url=" + postgres.getJdbcUrl(),
                    "--spring.datasource.username=" + postgres.getUsername(),
                    "--spring.datasource.password=" + postgres.getPassword(),
                    "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                    "--ft.lifecycle.lifecycle-enabled=true",
                    "--ft.lifecycle.shutdown-timeout-millis=30000",
                    "--ft.lifecycle.kafka-flush-timeout-millis=10000",
                    "--ft.events.new-features=new_features",
                    "--ft.events.updated-features=updated_features",
                    "--ft.events.deleted-features=deleted_features",
                    "--spring.kafka.admin.properties.request.timeout.ms=5000",
                    "--spring.kafka.admin.auto-create=false");

            log.warn("Application started successfully despite missing 'deleted_features' topic");
            log.warn(
                    "This may indicate: 1) auto-creation is enabled, 2) validation is asynchronous, or 3) validation is incomplete");

        } catch (Exception e) {
            startupException = e;
            log.info("Application startup failed as expected: {}", e.getMessage());
        } finally {
            if (context != null && context.isActive()) {
                context.close();
            }
        }

        // Then - Startup should fail because 'deleted_features' topic is missing
        assertThat(startupException.getMessage())
                .as("Error message should indicate missing Kafka topic")
                .satisfiesAnyOf(
                        msg -> assertThat(msg.toLowerCase()).contains("deleted_features"),
                        msg -> assertThat(msg.toLowerCase()).contains("topic"),
                        msg -> assertThat(msg.toLowerCase()).contains("kafka"));

        log.info("Verified: Application correctly fails when required 'deleted_features' topic is missing");
    }
}

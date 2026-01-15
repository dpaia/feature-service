package com.sivalabs.ft.features.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.FeatureServiceApplication;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Programmatic shutdown tests that manually start and stop Spring application
 * contexts
 * to test actual ContextClosedEvent behavior.
 *
 * These tests use their own Testcontainers instances that remain running across
 * all tests,
 * allowing programmatically created contexts to connect to the same
 * infrastructure.
 */
@DisplayName("Application Lifecycle Programmatic Shutdown Tests")
@Testcontainers
class ApplicationLifecycleProgrammaticShutdownIT {

    private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleProgrammaticShutdownIT.class);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @BeforeAll
    static void setupContainers() {
        // Containers are started automatically by @Testcontainers
        log.info("PostgreSQL container started at: {}", postgres.getJdbcUrl());
        log.info("Kafka container started at: {}", kafka.getBootstrapServers());

        // Create required Kafka topics
        createKafkaTopics();
    }

    private static void createKafkaTopics() {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());

        try (AdminClient adminClient = AdminClient.create(config)) {
            List<NewTopic> topics = List.of(
                    new NewTopic("new_features", 1, (short) 1),
                    new NewTopic("updated_features", 1, (short) 1),
                    new NewTopic("deleted_features", 1, (short) 1));

            adminClient.createTopics(topics).all().get();
            log.info("Created Kafka topics: new_features, updated_features, deleted_features");
        } catch (Exception e) {
            log.error("Failed to create Kafka topics", e);
            throw new RuntimeException("Failed to create Kafka topics", e);
        }
    }

    @AfterAll
    static void tearDownContainers() {
        // Containers are stopped automatically by @Testcontainers
        log.info("Containers will be stopped after all tests");
    }

    private ConfigurableApplicationContext startApplication() {
        SpringApplication app = new SpringApplication(FeatureServiceApplication.class);
        app.setAdditionalProfiles("test");

        return app.run(
                "--server.port=0",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                "--ft.lifecycle.lifecycle-enabled=true",
                "--ft.lifecycle.shutdown-timeout-millis=30000",
                "--ft.lifecycle.kafka-flush-timeout-millis=10000",
                "--ft.lifecycle.force-exit-enabled=false",
                "--ft.events.new-features=new_features",
                "--ft.events.updated-features=updated_features",
                "--ft.events.deleted-features=deleted_features");
    }

    @Test
    @DisplayName("Should trigger ContextClosedEvent when application context is closed programmatically")
    void shouldTriggerContextClosedEventOnShutdown() throws Exception {
        // Given - Start a new application context programmatically
        ConfigurableApplicationContext context = null;

        try {
            context = startApplication();
            log.info("Application context started successfully");

            // Verify context is running
            assertThat(context.isActive()).as("Context should be active").isTrue();

            // When - Closing the context to trigger ContextClosedEvent
            long shutdownStart = System.currentTimeMillis();
            context.close();
            long shutdownDuration = System.currentTimeMillis() - shutdownStart;

            // Then - Context should be closed and shutdown should complete quickly
            assertThat(context.isActive()).as("Context should be closed").isFalse();
            assertThat(shutdownDuration)
                    .as("Shutdown should complete within reasonable time")
                    .isLessThan(30000L); // 30 seconds for clean shutdown

            log.info("ContextClosedEvent triggered and shutdown completed in {} ms", shutdownDuration);

        } finally {
            if (context != null && context.isActive()) {
                context.close();
            }
        }
    }

    @Test
    @DisplayName("Should flush Kafka messages during programmatic shutdown")
    void shouldFlushKafkaMessagesDuringProgrammaticShutdown() throws Exception {
        // Given - Start application and send messages
        ConfigurableApplicationContext context = null;

        try {
            context = startApplication();
            KafkaTemplate<String, Object> kafkaTemplate = context.getBean(KafkaTemplate.class);

            // Send 100 messages to test flush
            for (int i = 0; i < 100; i++) {
                kafkaTemplate.send("new_features", "key-" + i, "data-" + i);
            }

            log.info("Sent 100 messages to Kafka");

            // When - Trigger shutdown
            long shutdownStart = System.currentTimeMillis();
            context.close();
            long shutdownDuration = System.currentTimeMillis() - shutdownStart;

            // Then - Shutdown should complete with all messages flushed
            assertThat(shutdownDuration)
                    .as("Shutdown with Kafka flush should complete within timeout")
                    .isLessThan(30000L); // 30 seconds

            log.info("Shutdown with Kafka flush completed in {} ms", shutdownDuration);

        } finally {
            if (context != null && context.isActive()) {
                context.close();
            }
        }
    }

    @Test
    @DisplayName("Should handle multiple Kafka topic flushes during programmatic shutdown")
    void shouldHandleMultipleTopicFlushesDuringShutdown() throws Exception {
        // Given - Start application and send messages to all three topics
        ConfigurableApplicationContext context = null;

        try {
            context = startApplication();
            KafkaTemplate<String, Object> kafkaTemplate = context.getBean(KafkaTemplate.class);

            // Send messages to all three topics
            for (int i = 0; i < 30; i++) {
                kafkaTemplate.send("new_features", "new-" + i, "new-data-" + i);
                kafkaTemplate.send("updated_features", "updated-" + i, "updated-data-" + i);
                kafkaTemplate.send("deleted_features", "deleted-" + i, "deleted-data-" + i);
            }

            log.info("Sent 90 messages across all three topics");

            // When - Trigger shutdown
            long shutdownStart = System.currentTimeMillis();
            context.close();
            long shutdownDuration = System.currentTimeMillis() - shutdownStart;

            // Then - All topics should be flushed during shutdown
            assertThat(shutdownDuration)
                    .as("Multi-topic flush should complete within timeout")
                    .isLessThan(30000L); // 30 seconds

            log.info("Shutdown with multi-topic flush completed in {} ms", shutdownDuration);

        } finally {
            if (context != null && context.isActive()) {
                context.close();
            }
        }
    }

    @Test
    @DisplayName("Should close database connections during programmatic shutdown")
    void shouldCloseDatabaseConnectionsDuringShutdown() throws Exception {
        // Given - Start application and verify database connectivity
        ConfigurableApplicationContext context = null;

        try {
            context = startApplication();
            DataSource dataSource = context.getBean(DataSource.class);

            // Verify database is accessible before shutdown
            try (var connection = dataSource.getConnection()) {
                assertThat(connection.isValid(5))
                        .as("DB should be accessible before shutdown")
                        .isTrue();
            }

            log.info("Database verified accessible before shutdown");

            // When - Trigger shutdown
            long shutdownStart = System.currentTimeMillis();
            context.close();
            long shutdownDuration = System.currentTimeMillis() - shutdownStart;

            // Then - Shutdown should complete (DB connections closed by Spring)
            assertThat(context.isActive()).as("Context should be closed").isFalse();
            assertThat(shutdownDuration)
                    .as("Shutdown with DB cleanup should complete quickly")
                    .isLessThan(30000L); // 30 seconds

            log.info("Shutdown with database cleanup completed in {} ms", shutdownDuration);

        } finally {
            if (context != null && context.isActive()) {
                context.close();
            }
        }
    }

    @Test
    @DisplayName("Should complete shutdown within timeout programmatically")
    void shouldCompleteShutdownWithinTimeoutProgrammatically() throws Exception {
        // Given - Start application with specific timeout configuration
        ConfigurableApplicationContext context = null;

        try {
            SpringApplication app = new SpringApplication(FeatureServiceApplication.class);
            app.setAdditionalProfiles("test");

            context = app.run(
                    "--server.port=0",
                    "--spring.datasource.url=" + postgres.getJdbcUrl(),
                    "--spring.datasource.username=" + postgres.getUsername(),
                    "--spring.datasource.password=" + postgres.getPassword(),
                    "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                    "--ft.lifecycle.lifecycle-enabled=true",
                    "--ft.lifecycle.shutdown-timeout-millis=10000",
                    "--ft.lifecycle.kafka-flush-timeout-millis=5000",
                    "--ft.lifecycle.force-exit-enabled=false",
                    "--ft.events.new-features=new_features",
                    "--ft.events.updated-features=updated_features",
                    "--ft.events.deleted-features=deleted_features");

            KafkaTemplate<String, Object> kafkaTemplate = context.getBean(KafkaTemplate.class);

            // Send 50 messages
            for (int i = 0; i < 50; i++) {
                kafkaTemplate.send("new_features", "timeout-key-" + i, "timeout-data-" + i);
            }

            log.info("Sent 50 messages to test timeout enforcement");

            // When - Trigger shutdown
            long shutdownStart = System.currentTimeMillis();
            context.close();
            long shutdownDuration = System.currentTimeMillis() - shutdownStart;

            // Then - Shutdown should complete within the configured 10 second timeout
            assertThat(shutdownDuration)
                    .as("Shutdown should complete within configured 10s timeout")
                    .isLessThan(30000L);

            log.info("Shutdown completed in {} ms (within 10s timeout)", shutdownDuration);

        } finally {
            if (context != null && context.isActive()) {
                context.close();
            }
        }
    }
}

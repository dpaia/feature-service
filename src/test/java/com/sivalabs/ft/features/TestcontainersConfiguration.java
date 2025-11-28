package com.sivalabs.ft.features;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
@Testcontainers
public class TestcontainersConfiguration {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Container
    public static RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    /**
     * Configure custom app.rabbitmq.* properties for the application to use the testcontainer.
     * This is in addition to @ServiceConnection which configures standard Spring Boot properties.
     */
    @DynamicPropertySource
    static void configureRabbitMQProperties(DynamicPropertyRegistry registry) {
        // Test-specific constants that match the main application configuration
        final String FEATURE_EVENTS_EXCHANGE = "feature.events";
        final String FEATURE_DLX_EXCHANGE = "feature.events.dlx";
        final String FEATURE_DLQ_QUEUE = "feature.events.dlq";
        final String ROUTING_KEY_FEATURE_CREATED = "feature.created";
        final String ROUTING_KEY_FEATURE_UPDATED = "feature.updated";
        final String ROUTING_KEY_FEATURE_DELETED = "feature.deleted";

        // Configure connection properties from testcontainer (these match the record structure)
        registry.add("app.rabbitmq.host", rabbitmq::getHost);
        registry.add("app.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("app.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("app.rabbitmq.password", rabbitmq::getAdminPassword);
        registry.add("app.rabbitmq.virtual-host", () -> "/");

        // Configure nested properties to match RabbitMQProperties record structure
        registry.add("app.rabbitmq.exchange.name", () -> FEATURE_EVENTS_EXCHANGE);
        registry.add("app.rabbitmq.exchange.type", () -> "topic");
        registry.add("app.rabbitmq.exchange.durable", () -> "true");

        // Routing keys - must match the nested record structure
        registry.add("app.rabbitmq.exchange.routing-keys.feature-created", () -> ROUTING_KEY_FEATURE_CREATED);
        registry.add("app.rabbitmq.exchange.routing-keys.feature-updated", () -> ROUTING_KEY_FEATURE_UPDATED);
        registry.add("app.rabbitmq.exchange.routing-keys.feature-deleted", () -> ROUTING_KEY_FEATURE_DELETED);

        // Retry configuration - must match the nested record structure
        registry.add("app.rabbitmq.retry.max-attempts", () -> "3");
        registry.add("app.rabbitmq.retry.initial-interval", () -> "1000");
        registry.add("app.rabbitmq.retry.multiplier", () -> "2.0");
        registry.add("app.rabbitmq.retry.max-interval", () -> "10000");

        // Dead letter configuration - must match the nested record structure
        registry.add("app.rabbitmq.dead-letter.exchange-name", () -> FEATURE_DLX_EXCHANGE);
        registry.add("app.rabbitmq.dead-letter.queue-name", () -> FEATURE_DLQ_QUEUE);
        registry.add("app.rabbitmq.dead-letter.routing-key", () -> "feature.failed");
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return postgres;
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return kafka;
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitMQContainer() {
        return rabbitmq;
    }
}

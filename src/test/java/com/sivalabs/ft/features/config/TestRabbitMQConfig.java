package com.sivalabs.ft.features.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test configuration that sets up RabbitMQ testcontainer and configures connection properties.
 * Uses test-specific properties instead of importing main application properties.
 */
@TestConfiguration
@EnableConfigurationProperties(TestApplicationProperties.class)
@Profile("test")
public class TestRabbitMQConfig {

    // Test-specific constants that match the main application configuration
    public static final String FEATURE_EVENTS_EXCHANGE = "feature-events-exchange";
    public static final String FEATURE_EVENTS_QUEUE = "feature-events-queue";
    public static final String FEATURE_DLX_EXCHANGE = "feature-events-dlq-exchange";
    public static final String FEATURE_DLQ_QUEUE = "feature-events-dlq-queue";

    public static final String ROUTING_KEY_FEATURE_CREATED = "feature.created";
    public static final String ROUTING_KEY_FEATURE_UPDATED = "feature.updated";
    public static final String ROUTING_KEY_FEATURE_DELETED = "feature.deleted";
    public static final String ROUTING_KEY_DLQ = "feature.dlq";

    // Lazy container initialization to avoid Docker issues during class loading
    private static RabbitMQContainer rabbitmqContainer;

    private static synchronized RabbitMQContainer getRabbitMQContainer() {
        if (rabbitmqContainer == null) {
            rabbitmqContainer = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"))
                    .withExposedPorts(5672, 15672);
            rabbitmqContainer.start();
        }
        return rabbitmqContainer;
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        RabbitMQContainer container = getRabbitMQContainer();
        registry.add("spring.rabbitmq.host", container::getHost);
        registry.add("spring.rabbitmq.port", container::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");

        // Override RabbitMQ application properties to match test constants
        registry.add("ft.test.rabbitmq.exchange", () -> FEATURE_EVENTS_EXCHANGE);
        registry.add("ft.test.rabbitmq.queue", () -> FEATURE_EVENTS_QUEUE);
        registry.add("ft.test.rabbitmq.dlq-exchange", () -> FEATURE_DLX_EXCHANGE);
        registry.add("ft.test.rabbitmq.dlq-queue", () -> FEATURE_DLQ_QUEUE);
        registry.add("ft.test.rabbitmq.routing-key.created", () -> ROUTING_KEY_FEATURE_CREATED);
        registry.add("ft.test.rabbitmq.routing-key.updated", () -> ROUTING_KEY_FEATURE_UPDATED);
        registry.add("ft.test.rabbitmq.routing-key.deleted", () -> ROUTING_KEY_FEATURE_DELETED);
    }

    @Bean
    public RabbitMQContainer rabbitMQContainer() {
        return getRabbitMQContainer();
    }

    @Bean
    @Primary
    public TestApplicationProperties.RabbitMQProperties testRabbitMQProperties() {
        return new TestApplicationProperties.RabbitMQProperties(
                FEATURE_EVENTS_EXCHANGE,
                FEATURE_EVENTS_QUEUE,
                FEATURE_DLX_EXCHANGE,
                FEATURE_DLQ_QUEUE,
                new TestApplicationProperties.RabbitMQProperties.RoutingKeyProperties(
                        ROUTING_KEY_FEATURE_CREATED, ROUTING_KEY_FEATURE_UPDATED, ROUTING_KEY_FEATURE_DELETED));
    }
}

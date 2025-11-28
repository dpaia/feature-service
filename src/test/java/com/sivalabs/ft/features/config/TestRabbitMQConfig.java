package com.sivalabs.ft.features.config;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Test configuration that configures RabbitMQ to use the shared testcontainer.
 * This overrides the main application's ConnectionFactory to point to the test container.
 */
@TestConfiguration
@Profile("test")
public class TestRabbitMQConfig {

    // Test-specific constants that match the main application configuration
    public static final String FEATURE_EVENTS_EXCHANGE = "feature.events";
    public static final String FEATURE_EVENTS_QUEUE = "feature.events.queue";
    public static final String FEATURE_DLX_EXCHANGE = "feature.events.dlx";
    public static final String FEATURE_DLQ_QUEUE = "feature.events.dlq";

    public static final String ROUTING_KEY_FEATURE_CREATED = "feature.created";
    public static final String ROUTING_KEY_FEATURE_UPDATED = "feature.updated";
    public static final String ROUTING_KEY_FEATURE_DELETED = "feature.deleted";

    @Autowired
    private RabbitMQContainer rabbitMQContainer;

    /**
     * Override the main application's ConnectionFactory to use the testcontainer.
     * This ensures the application connects to the test RabbitMQ instance.
     */
    @Bean
    @Primary
    public ConnectionFactory testConnectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(rabbitMQContainer.getHost());
        factory.setPort(rabbitMQContainer.getAmqpPort());
        factory.setUsername(rabbitMQContainer.getAdminUsername());
        factory.setPassword(rabbitMQContainer.getAdminPassword());
        // Use default virtual host
        factory.setVirtualHost("/");
        return factory;
    }
}

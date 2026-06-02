package com.sivalabs.ft.features.messaging;

import com.sivalabs.ft.features.ApplicationProperties;
import com.sivalabs.ft.features.domain.events.FeatureCreatedEvent;
import com.sivalabs.ft.features.domain.events.FeatureDeletedEvent;
import com.sivalabs.ft.features.domain.events.FeatureUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitMQEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(RabbitMQEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ApplicationProperties properties;

    public RabbitMQEventPublisher(RabbitTemplate rabbitTemplate, ApplicationProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publishFeatureCreatedEvent(FeatureCreatedEvent event) {
        try {
            log.info("Publishing FeatureCreatedEvent to RabbitMQ: {}", event.code());
            rabbitTemplate.convertAndSend(
                    properties.rabbitmq().exchange(),
                    properties.rabbitmq().routingKey().created(),
                    event);
            log.info("Successfully published FeatureCreatedEvent to RabbitMQ: {}", event.code());
        } catch (Exception e) {
            log.error("Failed to publish FeatureCreatedEvent to RabbitMQ: {}", event.code(), e);
            throw new RabbitMQPublishException("Failed to publish FeatureCreatedEvent", e);
        }
    }

    public void publishFeatureUpdatedEvent(FeatureUpdatedEvent event) {
        try {
            log.info("Publishing FeatureUpdatedEvent to RabbitMQ: {}", event.code());
            rabbitTemplate.convertAndSend(
                    properties.rabbitmq().exchange(),
                    properties.rabbitmq().routingKey().updated(),
                    event);
            log.info("Successfully published FeatureUpdatedEvent to RabbitMQ: {}", event.code());
        } catch (Exception e) {
            log.error("Failed to publish FeatureUpdatedEvent to RabbitMQ: {}", event.code(), e);
            throw new RabbitMQPublishException("Failed to publish FeatureUpdatedEvent", e);
        }
    }

    public void publishFeatureDeletedEvent(FeatureDeletedEvent event) {
        try {
            log.info("Publishing FeatureDeletedEvent to RabbitMQ: {}", event.code());
            rabbitTemplate.convertAndSend(
                    properties.rabbitmq().exchange(),
                    properties.rabbitmq().routingKey().deleted(),
                    event);
            log.info("Successfully published FeatureDeletedEvent to RabbitMQ: {}", event.code());
        } catch (Exception e) {
            log.error("Failed to publish FeatureDeletedEvent to RabbitMQ: {}", event.code(), e);
            throw new RabbitMQPublishException("Failed to publish FeatureDeletedEvent", e);
        }
    }

    public static class RabbitMQPublishException extends RuntimeException {
        public RabbitMQPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

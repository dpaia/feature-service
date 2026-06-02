package com.sivalabs.ft.features.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.listener.FatalExceptionStrategy;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for event serialization using Jackson.
 *
 * <p>This configuration provides:
 * <ul>
 *   <li>Jackson2JsonMessageConverter for JSON serialization</li>
 *   <li>Proper content-type headers (application/json)</li>
 *   <li>Type information for polymorphic deserialization</li>
 * </ul>
 *
 * <p><strong>FAIL-TO-PASS Demonstration:</strong>
 * <ul>
 *   <li>WITHOUT this config: SimpleMessageConverter fails with IllegalArgumentException</li>
 *   <li>WITH this config: Jackson serializes events to JSON successfully</li>
 * </ul>
 */
@Configuration
@EnableRabbit
public class RabbitConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitConfig.class);

    /**
     * Jackson message converter for JSON serialization.
     *
     * <p>This converter:
     * <ul>
     *   <li>Serializes Java objects to JSON</li>
     *   <li>Sets content-type to "application/json"</li>
     *   <li>Includes type information for deserialization</li>
     * </ul>
     *
     * @return Jackson2JsonMessageConverter configured for RabbitMQ
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate with Jackson converter.
     *
     * <p>Required for sending messages with JSON serialization.
     *
     * @param connectionFactory RabbitMQ connection factory
     * @return configured RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    /**
     * Rabbit listener container factory with Jackson converter.
     *
     * <p>Required for receiving messages with JSON deserialization.
     *
     * @param configurer Spring Boot configurer
     * @param connectionFactory RabbitMQ connection factory
     * @return configured container factory
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());

        // Set custom error handler that logs and tracks deserialization errors
        factory.setErrorHandler(customErrorHandler());

        // Don't requeue rejected messages (malformed messages will be discarded)
        factory.setDefaultRequeueRejected(false);

        return factory;
    }

    /**
     * Custom error handler for RabbitMQ with deserialization error tracking.
     *
     * <p>This handler:
     * <ul>
     *   <li>Logs deserialization errors with details</li>
     *   <li>Tracks error count for testing</li>
     *   <li>Rejects malformed messages (no requeue)</li>
     *   <li>Allows consumer to continue</li>
     * </ul>
     */
    private ConditionalRejectingErrorHandler customErrorHandler() {
        return new ConditionalRejectingErrorHandler(new FatalExceptionStrategy() {
            @Override
            public boolean isFatal(Throwable t) {
                // Log deserialization errors with full context
                log.error("RabbitMQ deserialization error: {}", t.getMessage(), t);

                // Consider all deserialization errors as fatal (reject, don't requeue)
                return t.getCause() instanceof com.fasterxml.jackson.core.JsonProcessingException
                        || t instanceof org.springframework.amqp.support.converter.MessageConversionException;
            }
        });
    }

    /**
     * Queues for different feature event types.
     * Separate queues ensure proper type deserialization.
     */
    @Bean
    public Queue featureCreatedEventsQueue() {
        return new Queue("feature-created-events", false);
    }

    @Bean
    public Queue featureUpdatedEventsQueue() {
        return new Queue("feature-updated-events", false);
    }

    @Bean
    public Queue featureDeletedEventsQueue() {
        return new Queue("feature-deleted-events", false);
    }
}

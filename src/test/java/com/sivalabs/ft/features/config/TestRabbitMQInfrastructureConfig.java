package com.sivalabs.ft.features.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * Test-specific RabbitMQ infrastructure configuration.
 * This configuration creates the necessary beans for testing without depending on main application classes.
 */
@TestConfiguration
@Profile("test")
public class TestRabbitMQInfrastructureConfig {

    @Bean
    public MessageConverter testJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate testRabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setRetryTemplate(testRetryTemplate());
        return rabbitTemplate;
    }

    @Bean
    public RetryTemplate testRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(10000);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }

    @Bean
    public TopicExchange testFeatureEventsExchange() {
        return new TopicExchange(TestRabbitMQConfig.FEATURE_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue testFeatureEventsQueue() {
        return QueueBuilder.durable(TestRabbitMQConfig.FEATURE_EVENTS_QUEUE)
                .withArgument("x-dead-letter-exchange", TestRabbitMQConfig.FEATURE_DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Binding testFeatureCreatedBinding() {
        return BindingBuilder.bind(testFeatureEventsQueue())
                .to(testFeatureEventsExchange())
                .with(TestRabbitMQConfig.ROUTING_KEY_FEATURE_CREATED);
    }

    @Bean
    public Binding testFeatureUpdatedBinding() {
        return BindingBuilder.bind(testFeatureEventsQueue())
                .to(testFeatureEventsExchange())
                .with(TestRabbitMQConfig.ROUTING_KEY_FEATURE_UPDATED);
    }

    @Bean
    public Binding testFeatureDeletedBinding() {
        return BindingBuilder.bind(testFeatureEventsQueue())
                .to(testFeatureEventsExchange())
                .with(TestRabbitMQConfig.ROUTING_KEY_FEATURE_DELETED);
    }

    @Bean
    public TopicExchange testDeadLetterExchange() {
        return new TopicExchange(TestRabbitMQConfig.FEATURE_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue testDeadLetterQueue() {
        return QueueBuilder.durable(TestRabbitMQConfig.FEATURE_DLQ_QUEUE).build();
    }

    @Bean
    public Binding testDeadLetterBinding() {
        return BindingBuilder.bind(testDeadLetterQueue())
                .to(testDeadLetterExchange())
                .with("#");
    }

    @Bean
    public RabbitAdmin testRabbitAdmin(ConnectionFactory connectionFactory) {
        // RabbitAdmin will automatically declare all the queues, exchanges, and bindings
        // when the application context starts up
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.setAutoStartup(true);

        // Explicitly declare all our infrastructure
        rabbitAdmin.declareExchange(testFeatureEventsExchange());
        rabbitAdmin.declareExchange(testDeadLetterExchange());
        rabbitAdmin.declareQueue(testFeatureEventsQueue());
        rabbitAdmin.declareQueue(testDeadLetterQueue());
        rabbitAdmin.declareBinding(testFeatureCreatedBinding());
        rabbitAdmin.declareBinding(testFeatureUpdatedBinding());
        rabbitAdmin.declareBinding(testFeatureDeletedBinding());
        rabbitAdmin.declareBinding(testDeadLetterBinding());

        return rabbitAdmin;
    }
}

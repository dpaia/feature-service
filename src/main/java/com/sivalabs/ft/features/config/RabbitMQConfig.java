package com.sivalabs.ft.features.config;

import com.sivalabs.ft.features.ApplicationProperties;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RabbitMQConfig {

    private final ApplicationProperties properties;

    public RabbitMQConfig(ApplicationProperties properties) {
        this.properties = properties;
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setRetryTemplate(retryTemplate());
        return rabbitTemplate;
    }

    @Bean
    public RetryTemplate retryTemplate() {
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
    public TopicExchange featureEventsExchange() {
        return new TopicExchange(properties.rabbitmq().exchange(), true, false);
    }

    @Bean
    public Queue featureEventsQueue() {
        return QueueBuilder.durable(properties.rabbitmq().queue())
                .withArgument("x-dead-letter-exchange", properties.rabbitmq().dlqExchange())
                .build();
    }

    @Bean
    public Binding featureCreatedBinding() {
        return BindingBuilder.bind(featureEventsQueue())
                .to(featureEventsExchange())
                .with(properties.rabbitmq().routingKey().created());
    }

    @Bean
    public Binding featureUpdatedBinding() {
        return BindingBuilder.bind(featureEventsQueue())
                .to(featureEventsExchange())
                .with(properties.rabbitmq().routingKey().updated());
    }

    @Bean
    public Binding featureDeletedBinding() {
        return BindingBuilder.bind(featureEventsQueue())
                .to(featureEventsExchange())
                .with(properties.rabbitmq().routingKey().deleted());
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(properties.rabbitmq().dlqExchange(), true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(properties.rabbitmq().dlqQueue()).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with("#");
    }
}

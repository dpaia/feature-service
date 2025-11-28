package com.sivalabs.ft.features.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ft.test")
public record TestApplicationProperties(EventsProperties events, RabbitMQProperties rabbitmq) {

    public record EventsProperties(String newFeatures, String updatedFeatures, String deletedFeatures) {}

    public record RabbitMQProperties(
            String exchange, String queue, String dlqExchange, String dlqQueue, RoutingKeyProperties routingKey) {

        public record RoutingKeyProperties(String created, String updated, String deleted) {}
    }
}

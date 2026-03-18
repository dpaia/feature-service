package com.sivalabs.ft.features.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
class KafkaConfig {

    @Bean
    DefaultKafkaProducerFactoryCustomizer kafkaJsonSerializerCustomizer(ObjectMapper objectMapper) {
        return producerFactory -> producerFactory.setValueSerializer(new JsonSerializer<>(objectMapper));
    }
}

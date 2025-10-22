package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.ApplicationProperties;
import com.sivalabs.ft.features.domain.entities.Feature;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ApplicationProperties properties;
    private final ApplicationEventPublisher applicationEventPublisher;

    public EventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            ApplicationProperties properties,
            ApplicationEventPublisher applicationEventPublisher) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Publishes a feature created event both to Kafka and as a Spring ApplicationEvent.
     * The Kafka event is used for inter-service communication, while the ApplicationEvent
     * is used for synchronous handling within this application.
     *
     * @param feature the Feature entity that was created
     */
    public void publishFeatureCreatedEvent(Feature feature) {
        // Publish to Kafka for inter-service communication
        FeatureCreatedEvent event = new FeatureCreatedEvent(
                feature.getId(),
                feature.getCode(),
                feature.getTitle(),
                feature.getDescription(),
                feature.getStatus(),
                feature.getRelease() == null ? null : feature.getRelease().getCode(),
                feature.getAssignedTo(),
                feature.getCreatedBy(),
                feature.getCreatedAt());
        kafkaTemplate.send(properties.events().newFeatures(), event);

        // Publish Spring ApplicationEvent for synchronous handling within the application
        applicationEventPublisher.publishEvent(new FeatureCreatedApplicationEvent(this, feature));
    }

    public void publishFeatureUpdatedEvent(Feature feature) {
        FeatureUpdatedEvent event = new FeatureUpdatedEvent(
                feature.getId(),
                feature.getCode(),
                feature.getTitle(),
                feature.getDescription(),
                feature.getStatus(),
                feature.getRelease() == null ? null : feature.getRelease().getCode(),
                feature.getAssignedTo(),
                feature.getCreatedBy(),
                feature.getCreatedAt(),
                feature.getUpdatedBy(),
                feature.getUpdatedAt());
        kafkaTemplate.send(properties.events().updatedFeatures(), event);
    }

    public void publishFeatureDeletedEvent(Feature feature, String deletedBy, Instant deletedAt) {
        FeatureDeletedEvent event = new FeatureDeletedEvent(
                feature.getId(),
                feature.getCode(),
                feature.getTitle(),
                feature.getDescription(),
                feature.getStatus(),
                feature.getRelease() == null ? null : feature.getRelease().getCode(),
                feature.getAssignedTo(),
                feature.getCreatedBy(),
                feature.getCreatedAt(),
                feature.getUpdatedBy(),
                feature.getUpdatedAt(),
                deletedBy,
                deletedAt);
        kafkaTemplate.send(properties.events().deletedFeatures(), event);
    }
}

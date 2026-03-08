package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.ApplicationProperties;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.models.ActionType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ApplicationProperties properties;

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate, ApplicationProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publishFeatureCreatedEvent(Feature feature) {
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

    public void publishFeatureUsageEvent(
            String userId,
            String featureCode,
            String productCode,
            String releaseCode,
            ActionType actionType,
            Map<String, Object> context,
            String ipAddress,
            String userAgent) {
        FeatureUsageEvent event = new FeatureUsageEvent(
                UUID.randomUUID().toString(),
                userId,
                featureCode,
                productCode,
                releaseCode,
                actionType,
                Instant.now(),
                context,
                ipAddress,
                userAgent);
        kafkaTemplate.send(properties.events().featureUsage(), event);
    }
}

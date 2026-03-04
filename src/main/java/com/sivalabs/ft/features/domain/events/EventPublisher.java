package com.sivalabs.ft.features.domain.events;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.ApplicationProperties;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.dtos.MilestoneDto;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.entities.Feature;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ApplicationProperties properties;
    private final ObjectMapper objectMapper;

    public EventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate, ApplicationProperties properties, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
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
        kafkaTemplate.flush();
    }

    public void publishFeatureUpdatedEvent(FeatureDto featureDto) {
        Map<String, Object> payload = toPayload(featureDto, EventType.UPDATED);
        kafkaTemplate.send(properties.events().updatedFeatures(), payload);
        kafkaTemplate.flush();
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
        kafkaTemplate.flush();
    }

    public void publishReleaseEvent(ReleaseDto releaseDto, EventType eventType) {
        Map<String, Object> payload = toPayload(releaseDto, eventType);
        kafkaTemplate.send(properties.events().updatedReleases(), payload);
        kafkaTemplate.flush();
    }

    public void publishMilestoneEvent(MilestoneDto milestoneDto, EventType eventType) {
        Map<String, Object> payload = toPayload(milestoneDto, eventType);
        kafkaTemplate.send(properties.events().updatedMilestones(), payload);
        kafkaTemplate.flush();
    }

    private Map<String, Object> toPayload(Object dto, EventType eventType) {
        Map<String, Object> payload =
                objectMapper.convertValue(dto, new TypeReference<LinkedHashMap<String, Object>>() {});
        payload.put("eventType", eventType.name());
        return payload;
    }
}

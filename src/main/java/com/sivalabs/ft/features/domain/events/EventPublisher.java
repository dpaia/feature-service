package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.ApplicationProperties;
import com.sivalabs.ft.features.domain.dtos.MilestoneDto;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.entities.Feature;
import java.time.Instant;
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

    public void publishFeatureUpdatedEvent(Feature feature) {
        FeatureEvent event = new FeatureEvent(
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
                feature.getPlannedCompletionDate(),
                feature.getPlanningStatus(),
                feature.getFeatureOwner(),
                feature.getNotes(),
                feature.getBlockageReason(),
                "UPDATED");
        kafkaTemplate.send(properties.events().updatedFeatures(), event);
    }

    // Release event publishing
    public void publishReleaseCreatedEvent(ReleaseDto releaseDto) {
        ReleaseEvent event = new ReleaseEvent(
                releaseDto.id(),
                releaseDto.code(),
                releaseDto.description(),
                releaseDto.status(),
                releaseDto.releasedAt(),
                releaseDto.milestoneCode(),
                releaseDto.productCode(),
                releaseDto.createdBy(),
                releaseDto.createdAt(),
                releaseDto.updatedBy(),
                releaseDto.updatedAt(),
                "CREATED");
        kafkaTemplate.send(properties.events().updatedReleases(), event);
    }

    public void publishReleaseUpdatedEvent(ReleaseDto releaseDto) {
        ReleaseEvent event = new ReleaseEvent(
                releaseDto.id(),
                releaseDto.code(),
                releaseDto.description(),
                releaseDto.status(),
                releaseDto.releasedAt(),
                releaseDto.milestoneCode(),
                releaseDto.productCode(),
                releaseDto.createdBy(),
                releaseDto.createdAt(),
                releaseDto.updatedBy(),
                releaseDto.updatedAt(),
                "UPDATED");
        kafkaTemplate.send(properties.events().updatedReleases(), event);
    }

    // Milestone event publishing
    public void publishMilestoneCreatedEvent(MilestoneDto milestoneDto) {
        MilestoneEvent event = new MilestoneEvent(
                milestoneDto.id(),
                milestoneDto.code(),
                milestoneDto.name(),
                milestoneDto.description(),
                milestoneDto.targetDate(),
                milestoneDto.actualDate(),
                milestoneDto.status(),
                milestoneDto.productCode(),
                milestoneDto.owner(),
                milestoneDto.notes(),
                milestoneDto.progress(),
                milestoneDto.releases(),
                milestoneDto.createdBy(),
                milestoneDto.createdAt(),
                milestoneDto.updatedBy(),
                milestoneDto.updatedAt(),
                "CREATED");
        kafkaTemplate.send(properties.events().updatedMilestones(), event);
    }

    public void publishMilestoneUpdatedEvent(MilestoneDto milestoneDto) {
        MilestoneEvent event = new MilestoneEvent(
                milestoneDto.id(),
                milestoneDto.code(),
                milestoneDto.name(),
                milestoneDto.description(),
                milestoneDto.targetDate(),
                milestoneDto.actualDate(),
                milestoneDto.status(),
                milestoneDto.productCode(),
                milestoneDto.owner(),
                milestoneDto.notes(),
                milestoneDto.progress(),
                milestoneDto.releases(),
                milestoneDto.createdBy(),
                milestoneDto.createdAt(),
                milestoneDto.updatedBy(),
                milestoneDto.updatedAt(),
                "UPDATED");
        kafkaTemplate.send(properties.events().updatedMilestones(), event);
    }

    public void publishMilestoneDeletedEvent(MilestoneDto milestoneDto) {
        MilestoneEvent event = new MilestoneEvent(
                milestoneDto.id(),
                milestoneDto.code(),
                milestoneDto.name(),
                milestoneDto.description(),
                milestoneDto.targetDate(),
                milestoneDto.actualDate(),
                milestoneDto.status(),
                milestoneDto.productCode(),
                milestoneDto.owner(),
                milestoneDto.notes(),
                milestoneDto.progress(),
                milestoneDto.releases(),
                milestoneDto.createdBy(),
                milestoneDto.createdAt(),
                milestoneDto.updatedBy(),
                milestoneDto.updatedAt(),
                "DELETED");
        kafkaTemplate.send(properties.events().updatedMilestones(), event);
    }
}

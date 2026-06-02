package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.domain.models.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka event listener with deduplication support for feature events
 * Uses EventDeduplicationService for constraint-based deduplication
 */
@Component
public class FeatureEventListener {

    private static final Logger logger = LoggerFactory.getLogger(FeatureEventListener.class);

    private final EventDeduplicationService eventDeduplicationService;

    public FeatureEventListener(EventDeduplicationService eventDeduplicationService) {
        this.eventDeduplicationService = eventDeduplicationService;
    }

    @KafkaListener(topics = "${ft.events.new-features}", groupId = "feature-service-group")
    @Transactional
    public void handleFeatureCreatedEvent(
            @Payload FeatureCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        logger.info(
                "Received FeatureCreatedEvent from topic: {}, partition: {}, offset: {}, eventId: {}",
                topic,
                partition,
                offset,
                event.eventId());

        eventDeduplicationService.executeIdempotent(event.eventId(), EventType.EVENT, () -> {
            processFeatureCreatedEvent(event);
            logger.info(
                    "Successfully processed FeatureCreatedEvent for feature: {} with eventId: {}",
                    event.code(),
                    event.eventId());
            return "processed";
        });
    }

    @KafkaListener(topics = "${ft.events.updated-features}", groupId = "feature-service-group")
    @Transactional
    public void handleFeatureUpdatedEvent(
            @Payload FeatureUpdatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        logger.info(
                "Received FeatureUpdatedEvent from topic: {}, partition: {}, offset: {}, eventId: {}",
                topic,
                partition,
                offset,
                event.eventId());

        eventDeduplicationService.executeIdempotent(event.eventId(), EventType.EVENT, () -> {
            processFeatureUpdatedEvent(event);
            logger.info(
                    "Successfully processed FeatureUpdatedEvent for feature: {} with eventId: {}",
                    event.code(),
                    event.eventId());
            return "processed";
        });
    }

    @KafkaListener(topics = "${ft.events.deleted-features}", groupId = "feature-service-group")
    @Transactional
    public void handleFeatureDeletedEvent(
            @Payload FeatureDeletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        logger.info(
                "Received FeatureDeletedEvent from topic: {}, partition: {}, offset: {}, eventId: {}",
                topic,
                partition,
                offset,
                event.eventId());

        eventDeduplicationService.executeIdempotent(event.eventId(), EventType.EVENT, () -> {
            processFeatureDeletedEvent(event);
            logger.info(
                    "Successfully processed FeatureDeletedEvent for feature: {} with eventId: {}",
                    event.code(),
                    event.eventId());
            return "processed";
        });
    }

    /**
     * Process feature created event - implement your business logic here
     */
    private void processFeatureCreatedEvent(FeatureCreatedEvent event) {
        logger.info(
                "EventListener business logic: Processing feature created: {} - {} (eventId: {})",
                event.code(),
                event.title(),
                event.eventId());

        // Example business logic:
        // - Send notifications
        // - Update search indexes
        // - Trigger workflows
        // - Update analytics
        // - Send emails to stakeholders
        // - Update external systems
        logger.debug("Feature created event processed successfully for: {}", event.code());
    }

    /**
     * Process feature updated event - implement your business logic here
     */
    private void processFeatureUpdatedEvent(FeatureUpdatedEvent event) {
        logger.info(
                "EventListener business logic: Processing feature updated: {} - {} (eventId: {})",
                event.code(),
                event.title(),
                event.eventId());

        // Example business logic:
        // - Send notifications about updates
        // - Update search indexes
        // - Trigger status change workflows
        // - Update analytics
        // - Send emails about status changes
        // - Update external systems
        logger.debug("Feature updated event processed successfully for: {}", event.code());
    }

    /**
     * Process feature deleted event - implement your business logic here
     */
    private void processFeatureDeletedEvent(FeatureDeletedEvent event) {
        logger.info(
                "EventListener business logic: Processing feature deleted: {} - {} (eventId: {})",
                event.code(),
                event.title(),
                event.eventId());

        // Example business logic:
        // - Send notifications about deletion
        // - Clean up related data
        // - Update search indexes
        // - Update analytics
        // - Send emails about deletion
        // - Clean up external systems
        logger.debug("Feature deleted event processed successfully for: {}", event.code());
    }
}

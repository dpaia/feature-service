package com.sivalabs.ft.features.domain.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.domain.EventStoreRepository;
import com.sivalabs.ft.features.domain.entities.EventStore;
import com.sivalabs.ft.features.domain.models.EventType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for replaying events from the event store
 * Supports idempotent event replay with time range and feature filtering
 */
@Service
public class EventReplayService {

    private static final Logger logger = LoggerFactory.getLogger(EventReplayService.class);

    private final EventStoreRepository eventStoreRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public EventReplayService(
            EventStoreRepository eventStoreRepository, EventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.eventStoreRepository = eventStoreRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Replay all events within a time range
     * Events are replayed in chronological order
     *
     * @param startTime start of time range
     * @param endTime end of time range
     * @return number of events replayed
     */
    @Transactional
    public ReplayResult replayEventsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        logger.info("Starting event replay for time range: {} to {}", startTime, endTime);
        List<EventStore> events = eventStoreRepository.findEventsForReplay(startTime, endTime, EventType.EVENT);
        return processEventsWithDetails(events);
    }

    /**
     * Replay events for specific features within a time range
     *
     * @param startTime start of time range
     * @param endTime end of time range
     * @param featureCodes list of feature codes to replay
     * @return number of events replayed
     */
    @Transactional
    public ReplayResult replayEventsByFeatures(
            LocalDateTime startTime, LocalDateTime endTime, List<String> featureCodes) {
        logger.info("Starting event replay for features {} in time range: {} to {}", featureCodes, startTime, endTime);
        List<EventStore> events =
                eventStoreRepository.findEventsForReplayByFeatures(startTime, endTime, featureCodes, EventType.EVENT);
        return processEventsWithDetails(events);
    }

    /**
     * Replay events by operation type within a time range
     *
     * @param startTime start of time range
     * @param endTime end of time range
     * @param operationType operation type (CREATED, UPDATED, DELETED)
     * @return number of events replayed
     */
    @Transactional
    public ReplayResult replayEventsByOperation(LocalDateTime startTime, LocalDateTime endTime, String operationType) {
        logger.info(
                "Starting event replay for operation type {} in time range: {} to {}",
                operationType,
                startTime,
                endTime);
        List<EventStore> events =
                eventStoreRepository.findEventsForReplayByOperation(startTime, endTime, operationType, EventType.EVENT);
        return processEventsWithDetails(events);
    }

    /**
     * Replay all events for a specific feature
     *
     * @param featureCode the feature code
     * @return number of events replayed
     */
    @Transactional
    public ReplayResult replayEventsByFeatureCode(String featureCode) {
        logger.info("Starting event replay for feature: {}", featureCode);
        List<EventStore> events = eventStoreRepository.findEventsByFeatureCode(featureCode, EventType.EVENT);
        return processEventsWithDetails(events);
    }

    /**
     * Replay a single event by republishing it to Kafka
     * The event will be processed idempotently by listeners
     * Updates replay tracking in the database
     */
    private void replayEvent(EventStore event) {
        logger.debug(
                "Replaying event: {} ({}) for feature: {}",
                event.getEventId(),
                event.getOperationType(),
                event.getFeatureCode());

        try {
            String payload = event.getEventPayload();

            switch (event.getOperationType()) {
                case "CREATED" -> {
                    FeatureCreatedEvent createdEvent = objectMapper.readValue(payload, FeatureCreatedEvent.class);
                    eventPublisher.publishFeatureCreatedEvent(createdEvent);
                    logger.info("Replayed CREATED event for feature: {}", event.getFeatureCode());
                }
                case "UPDATED" -> {
                    FeatureUpdatedEvent updatedEvent = objectMapper.readValue(payload, FeatureUpdatedEvent.class);
                    eventPublisher.publishFeatureUpdatedEvent(updatedEvent);
                    logger.info("Replayed UPDATED event for feature: {}", event.getFeatureCode());
                }
                case "DELETED" -> {
                    FeatureDeletedEvent deletedEvent = objectMapper.readValue(payload, FeatureDeletedEvent.class);
                    eventPublisher.publishFeatureDeletedEvent(deletedEvent);
                    logger.info("Replayed DELETED event for feature: {}", event.getFeatureCode());
                }
                default -> logger.warn("Unknown operation type for replay: {}", event.getOperationType());
            }

            // Update replay tracking in database as successful
            eventStoreRepository.updateReplayTrackingSuccess(
                    event.getEventId(), event.getEventType().name(), LocalDateTime.now());

        } catch (Exception e) {
            logger.error("Failed to deserialize or publish event for replay: {}", event.getEventId(), e);
            throw new RuntimeException("Event replay failed", e);
        }
    }

    /**
     * Get count of events in time range for preview
     */
    public long countEventsInTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return eventStoreRepository.countEventsInTimeRange(startTime, endTime, EventType.EVENT);
    }

    /**
     * Process list of events and return detailed replay result
     */
    private ReplayResult processEventsWithDetails(List<EventStore> events) {
        int replayedCount = 0;
        int failedCount = 0;
        List<EventReplayDetail> eventDetails = new ArrayList<>();

        for (EventStore event : events) {
            try {
                replayEvent(event);
                replayedCount++;
                eventDetails.add(new EventReplayDetail(
                        event.getEventId(),
                        event.getFeatureCode(),
                        event.getOperationType(),
                        "SUCCESS",
                        event.getEventTimestamp().toString()));
            } catch (Exception e) {
                logger.error(
                        "Failed to replay event {} ({}): {}",
                        event.getEventId(),
                        event.getOperationType(),
                        e.getMessage(),
                        e);
                // Mark replay as failed and continue with next event
                eventStoreRepository.updateReplayTrackingFailure(
                        event.getEventId(), event.getEventType().name(), LocalDateTime.now());
                failedCount++;
                eventDetails.add(new EventReplayDetail(
                        event.getEventId(),
                        event.getFeatureCode(),
                        event.getOperationType(),
                        "FAILED",
                        event.getEventTimestamp().toString()));
            }
        }

        logger.info(
                "Completed event replay: {} successful, {} failed out of {} total events",
                replayedCount,
                failedCount,
                events.size());
        return new ReplayResult(replayedCount, failedCount, eventDetails);
    }

    /**
     * Result of replay operation with success and failure counts and feature codes
     */
    public record ReplayResult(int successCount, int failedCount, List<EventReplayDetail> eventDetails) {}

    /**
     * Detailed information about each replayed event
     */
    public record EventReplayDetail(
            String eventId, String featureCode, String operationType, String status, String eventTimestamp) {}
}

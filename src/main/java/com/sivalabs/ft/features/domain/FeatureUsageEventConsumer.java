package com.sivalabs.ft.features.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import com.sivalabs.ft.features.domain.events.FeatureUsageEvent;
import com.sivalabs.ft.features.domain.models.ErrorType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class FeatureUsageEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(FeatureUsageEventConsumer.class);

    private final FeatureUsageRepository featureUsageRepository;
    private final ObjectMapper objectMapper;
    private final ErrorLoggingService errorLoggingService;

    public FeatureUsageEventConsumer(
            FeatureUsageRepository featureUsageRepository,
            ObjectMapper objectMapper,
            ErrorLoggingService errorLoggingService) {
        this.featureUsageRepository = featureUsageRepository;
        this.objectMapper = objectMapper;
        this.errorLoggingService = errorLoggingService;
    }

    @KafkaListener(
            topics = "${ft.events.feature-usage}",
            groupId = "${spring.application.name}",
            batch = "true",
            autoStartup = "${spring.kafka.listener.auto-startup:true}",
            containerFactory = "batchKafkaListenerContainerFactory")
    public void consume(List<FeatureUsageEvent> events) {
        log.debug("Received batch of {} FeatureUsage events from Kafka", events.size());

        // In-memory deduplication within the batch: track eventIds seen in this batch
        // This is needed because existsByEventId() checks the DB, but if two events with
        // the same eventId are in the same batch, both would pass the DB check before either
        // is committed, causing a unique constraint violation on INSERT.
        Set<String> seenEventIds = new HashSet<>();
        List<FeatureUsage> toSave = new ArrayList<>();
        List<FeatureUsageEvent> toSaveEvents = new ArrayList<>();

        for (FeatureUsageEvent event : events) {
            try {
                // eventId is mandatory by contract; deduplicate within batch first.
                if (!seenEventIds.add(event.eventId())) {
                    log.debug("Skipping intra-batch duplicate event with eventId={}", event.eventId());
                    continue;
                }
                // Then deduplicate against persisted data across batches/replays.
                if (featureUsageRepository.existsByEventId(event.eventId())) {
                    log.debug("Skipping already-persisted event with eventId={}", event.eventId());
                    continue;
                }

                FeatureUsage featureUsage = toEntity(event);
                toSave.add(featureUsage);
                toSaveEvents.add(event);
            } catch (Exception e) {
                log.error("Failed to process FeatureUsage event eventId={}", event.eventId(), e);
                errorLoggingService.logError(
                        ErrorType.PROCESSING_ERROR,
                        "Failed to process FeatureUsage Kafka event",
                        e,
                        toPayload(event),
                        event.userId());
            }
        }

        if (!toSave.isEmpty()) {
            try {
                // Force DB constraints check inside this try/catch so database failures
                // are captured and logged into error_log.
                featureUsageRepository.saveAllAndFlush(toSave);
                log.debug("Saved {} FeatureUsage records from Kafka batch", toSave.size());
            } catch (Exception e) {
                log.error("Failed to persist FeatureUsage Kafka batch", e);
                for (FeatureUsageEvent event : toSaveEvents) {
                    errorLoggingService.logError(
                            ErrorType.DATABASE_ERROR,
                            "Failed to persist FeatureUsage Kafka event",
                            e,
                            toPayload(event),
                            event.userId());
                }
            }
        }
    }

    private FeatureUsage toEntity(FeatureUsageEvent event) {
        String contextJson = null;
        if (event.context() != null && !event.context().isEmpty()) {
            try {
                contextJson = objectMapper.writeValueAsString(event.context());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize context for eventId={}", event.eventId(), e);
            }
        }

        FeatureUsage featureUsage = new FeatureUsage();
        featureUsage.setEventId(event.eventId());
        featureUsage.setUserId(event.userId());
        featureUsage.setFeatureCode(event.featureCode());
        featureUsage.setProductCode(event.productCode());
        featureUsage.setReleaseCode(event.releaseCode());
        featureUsage.setActionType(event.actionType());
        featureUsage.setTimestamp(event.timestamp());
        featureUsage.setContext(contextJson);
        return featureUsage;
    }

    private String toPayload(FeatureUsageEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return "eventId=" + event.eventId();
        }
    }
}

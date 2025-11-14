package com.sivalabs.ft.features.messaging;

import com.sivalabs.ft.features.domain.events.FeatureCreatedEvent;
import com.sivalabs.ft.features.domain.events.FeatureDeletedEvent;
import com.sivalabs.ft.features.domain.events.FeatureUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FeatureEventListener {
    private static final Logger log = LoggerFactory.getLogger(FeatureEventListener.class);

    private final RabbitMQEventPublisher rabbitMQEventPublisher;

    public FeatureEventListener(RabbitMQEventPublisher rabbitMQEventPublisher) {
        this.rabbitMQEventPublisher = rabbitMQEventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleFeatureCreatedEvent(FeatureCreatedEvent event) {
        log.debug("Received FeatureCreatedEvent for feature: {}", event.code());
        try {
            rabbitMQEventPublisher.publishFeatureCreatedEvent(event);
        } catch (Exception e) {
            log.error("Failed to relay FeatureCreatedEvent to RabbitMQ for feature: {}", event.code(), e);
            // Exception is logged but not re-thrown to avoid affecting the main transaction
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleFeatureUpdatedEvent(FeatureUpdatedEvent event) {
        log.debug("Received FeatureUpdatedEvent for feature: {}", event.code());
        try {
            rabbitMQEventPublisher.publishFeatureUpdatedEvent(event);
        } catch (Exception e) {
            log.error("Failed to relay FeatureUpdatedEvent to RabbitMQ for feature: {}", event.code(), e);
            // Exception is logged but not re-thrown to avoid affecting the main transaction
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleFeatureDeletedEvent(FeatureDeletedEvent event) {
        log.debug("Received FeatureDeletedEvent for feature: {}", event.code());
        try {
            rabbitMQEventPublisher.publishFeatureDeletedEvent(event);
        } catch (Exception e) {
            log.error("Failed to relay FeatureDeletedEvent to RabbitMQ for feature: {}", event.code(), e);
            // Exception is logged but not re-thrown to avoid affecting the main transaction
        }
    }
}

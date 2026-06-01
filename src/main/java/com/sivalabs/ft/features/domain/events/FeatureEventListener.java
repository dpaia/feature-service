package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.domain.events.internal.InternalFeatureCreatedEvent;
import com.sivalabs.ft.features.domain.events.internal.InternalFeatureDeletedEvent;
import com.sivalabs.ft.features.domain.events.internal.InternalFeatureUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FeatureEventListener {
    private static final Logger logger = LoggerFactory.getLogger(FeatureEventListener.class);
    
    private final EventPublisher eventPublisher;

    public FeatureEventListener(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFeatureCreatedEvent(InternalFeatureCreatedEvent event) {
        logger.info("Processing feature created event after transaction commit: {}", 
                event.feature().getCode());
        eventPublisher.publishFeatureCreatedEvent(event.feature());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFeatureUpdatedEvent(InternalFeatureUpdatedEvent event) {
        logger.info("Processing feature updated event after transaction commit: {}", 
                event.feature().getCode());
        eventPublisher.publishFeatureUpdatedEvent(event.feature());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFeatureDeletedEvent(InternalFeatureDeletedEvent event) {
        logger.info("Processing feature deleted event after transaction commit: {}", 
                event.feature().getCode());
        eventPublisher.publishFeatureDeletedEvent(
                event.feature(), event.deletedBy(), event.deletedAt());
    }
}
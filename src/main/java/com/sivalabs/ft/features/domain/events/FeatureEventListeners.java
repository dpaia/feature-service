package com.sivalabs.ft.features.domain.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class FeatureEventListeners {
    private static final Logger logger = LoggerFactory.getLogger(FeatureEventListeners.class);

    @EventListener
    @Order(1)
    public void handleFeatureEvent(FeatureEvent event) {
        logger.info("Base listener - Received feature event for feature with ID: {}, code: {}, type: {}",
                event.getId(), event.getCode(), event.getClass().getSimpleName());
    }

    @EventListener
    @Order(2)
    public void handleFeatureCreatedEvent(FeatureCreatedEvent event) {
        logger.info("Specific listener - Feature created with ID: {}, code: {}, by: {}",
                event.getId(), event.getCode(), event.getCreatedBy());
    }

    @EventListener
    @Order(2)
    public void handleFeatureUpdatedEvent(FeatureUpdatedEvent event) {
        logger.info("Specific listener - Feature updated with ID: {}, code: {}, by: {}",
                event.getId(), event.getCode(), event.getUpdatedBy());
    }

    @EventListener
    @Order(2)
    public void handleFeatureDeletedEvent(FeatureDeletedEvent event) {
        logger.info("Specific listener - Feature deleted with ID: {}, code: {}, by: {}",
                event.getId(), event.getCode(), event.getDeletedBy());
    }
}
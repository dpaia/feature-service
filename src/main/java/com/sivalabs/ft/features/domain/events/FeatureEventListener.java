package com.sivalabs.ft.features.domain.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Component that listens for Feature-related events.
 * This listener demonstrates conditional event processing using SpEL expressions.
 */
@Component
public class FeatureEventListener {
    private static final Logger logger = LoggerFactory.getLogger(FeatureEventListener.class);

    /**
     * Handles FeatureCreatedEvent only if the creator has the ADMIN role.
     * This demonstrates conditional event processing using SpEL expressions.
     * 
     * @param event the FeatureCreatedEvent containing the created Feature
     */
    @EventListener(condition = "#event.creatorRole == 'ADMIN'")
    public void handleAdminFeatureCreatedEvent(FeatureCreatedEvent event) {
        logger.info("Admin feature created event received - ID: {}, Name: {}, Creator: {}, Role: {}", 
                event.id(), event.title(), event.createdBy(), event.creatorRole());
        
        // Additional business logic for admin-created features can be added here
        // For example:
        // - Special validation or processing for admin-created features
        // - Notifications to specific teams
        // - Automatic approval workflows
        // - Audit logging for compliance
    }
}
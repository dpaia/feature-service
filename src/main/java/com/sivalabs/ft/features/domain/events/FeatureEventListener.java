package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.domain.entities.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Component that listens for Feature-related application events.
 * This listener handles events asynchronously using the configured thread pool.
 */
@Component
public class FeatureEventListener {
    private static final Logger logger = LoggerFactory.getLogger(FeatureEventListener.class);

    /**
     * Handles FeatureCreatedApplicationEvent.
     * This method is called asynchronously when a new Feature is created.
     * It logs feature details and can perform additional business logic as needed.
     *
     * @param event the FeatureCreatedApplicationEvent containing the created Feature
     */
    @Async("taskExecutor")
    @EventListener
    public void handleFeatureCreatedEvent(FeatureCreatedApplicationEvent event) {
        Feature feature = event.getFeature();
        logger.info(
                "Feature created event received - ID: {}, Name: {}, Thread: {}",
                feature.getId(),
                feature.getTitle(),
                Thread.currentThread().getName());

        // Additional business logic can be added here
        // For example:
        // - Send notifications
        // - Update statistics
        // - Trigger other processes
    }
}

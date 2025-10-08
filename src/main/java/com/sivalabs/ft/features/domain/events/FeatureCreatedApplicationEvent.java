package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.domain.entities.Feature;
import org.springframework.context.ApplicationEvent;

/**
 * Spring ApplicationEvent that is published when a new Feature is created.
 * This event is published synchronously and can be handled by components
 * within the application using the @EventListener annotation.
 */
public class FeatureCreatedApplicationEvent extends ApplicationEvent {
    private final Feature feature;

    /**
     * Create a new FeatureCreatedApplicationEvent.
     *
     * @param source the object on which the event initially occurred or with
     *               which the event is associated (never {@code null})
     * @param feature the Feature entity that was created
     */
    public FeatureCreatedApplicationEvent(Object source, Feature feature) {
        super(source);
        this.feature = feature;
    }

    /**
     * Get the Feature entity that was created.
     *
     * @return the Feature entity
     */
    public Feature getFeature() {
        return feature;
    }
}

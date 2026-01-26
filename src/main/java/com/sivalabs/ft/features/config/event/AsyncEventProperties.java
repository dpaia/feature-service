package com.sivalabs.ft.features.config.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ft.event.async")
public record AsyncEventProperties(int corePoolSize, int maxPoolSize, int queueCapacity, String threadNamePrefix) {

    /**
     * Default constructor with sensible defaults for when properties are not configured.
     */
    public AsyncEventProperties() {
        this(4, 8, 200, "event-listener-");
    }
}

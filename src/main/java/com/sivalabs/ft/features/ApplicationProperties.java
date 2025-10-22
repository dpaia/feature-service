package com.sivalabs.ft.features;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ft")
public record ApplicationProperties(EventsProperties events, AsyncProperties async) {

    public record EventsProperties(String newFeatures, String updatedFeatures, String deletedFeatures) {}

    public record AsyncProperties(int corePoolSize, int maxPoolSize, int queueCapacity, String threadNamePrefix) {

        // Default values if not specified in properties
        public AsyncProperties {
            if (corePoolSize <= 0) corePoolSize = 2;
            if (maxPoolSize <= 0) maxPoolSize = 5;
            if (queueCapacity <= 0) queueCapacity = 100;
            if (threadNamePrefix == null) threadNamePrefix = "feature-async-";
        }
    }
}

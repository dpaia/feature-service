package com.sivalabs.ft.features;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ft")
public record ApplicationProperties(EventsProperties events, LifecycleProperties lifecycle) {

    public record EventsProperties(String newFeatures, String updatedFeatures, String deletedFeatures) {}

    public record LifecycleProperties(
            Boolean lifecycleEnabled,
            Long shutdownTimeoutMillis,
            Long kafkaFlushTimeoutMillis,
            Boolean forceExitEnabled) {
        public LifecycleProperties {
            if (lifecycleEnabled == null) {
                lifecycleEnabled = true;
            }
            if (shutdownTimeoutMillis == null) {
                shutdownTimeoutMillis = 30000L;
            }
            if (kafkaFlushTimeoutMillis == null) {
                kafkaFlushTimeoutMillis = 10000L;
            }
            if (forceExitEnabled == null) {
                forceExitEnabled = true;
            }
        }
    }
}

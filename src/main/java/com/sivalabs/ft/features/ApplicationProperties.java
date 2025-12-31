package com.sivalabs.ft.features;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ft")
public record ApplicationProperties(EventsProperties events, String publicBaseUrl, EmailProperties email) {

    public record EventsProperties(String newFeatures, String updatedFeatures, String deletedFeatures) {}

    public record EmailProperties(Boolean enabled) {
        public boolean isEnabled() {
            return enabled != null && enabled;
        }
    }
}

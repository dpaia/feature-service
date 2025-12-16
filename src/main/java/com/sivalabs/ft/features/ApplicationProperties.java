package com.sivalabs.ft.features;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ft")
public record ApplicationProperties(
        EventsProperties events, UsageTrackingProperties usageTracking, GdprProperties gdpr) {

    public record EventsProperties(String newFeatures, String updatedFeatures, String deletedFeatures) {}

    public record UsageTrackingProperties(boolean enabled) {}

    public record GdprProperties(boolean enabled, boolean anonymizeUserIds, boolean anonymizeIpAddresses) {
        public GdprProperties {
            if (enabled) {
                anonymizeUserIds = true;
                anonymizeIpAddresses = true;
            }
        }
    }
}

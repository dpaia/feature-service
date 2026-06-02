package com.sivalabs.ft.features;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "ft")
@Validated
public record ApplicationProperties(
        EventsProperties events, @NotBlank(message = "ft.publicBaseUrl property is required") String publicBaseUrl) {

    public record EventsProperties(String newFeatures, String updatedFeatures, String deletedFeatures) {}
}

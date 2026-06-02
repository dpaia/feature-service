package com.sivalabs.ft.features.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.websocket")
@Validated
public record WebSocketProperties(
        @NotBlank(message = "app.websocket.endpoint property is required") String endpoint, String allowedOrigins) {
    public WebSocketProperties {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            allowedOrigins = "*";
        }
    }
}

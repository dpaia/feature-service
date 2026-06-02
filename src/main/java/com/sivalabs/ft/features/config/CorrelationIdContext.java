package com.sivalabs.ft.features.config;

import java.util.UUID;

/**
 * ThreadLocal context to store and retrieve correlation ID
 */
public class CorrelationIdContext {
    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    public static void setCorrelationId(String correlationId) {
        CORRELATION_ID.set(correlationId);
    }

    public static String getCorrelationId() {
        return CORRELATION_ID.get();
    }

    public static String getCorrelationIdOrDefault() {
        String correlationId = CORRELATION_ID.get();
        return correlationId != null ? correlationId : UUID.randomUUID().toString();
    }

    public static void clear() {
        CORRELATION_ID.remove();
    }
}

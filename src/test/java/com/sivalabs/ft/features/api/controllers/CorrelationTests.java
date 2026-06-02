package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.sivalabs.ft.features.AbstractIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CorrelationTests extends AbstractIT {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Test
    void shouldPropagateProvidedCorrelationId() {
        // Given
        String correlationId = UUID.randomUUID().toString();

        // When/Then
        var result = mvc.perform(get("/api/features").header(CORRELATION_ID_HEADER, correlationId));
        assertThat(result).hasStatusOk().hasHeader(CORRELATION_ID_HEADER, correlationId);
    }

    @Test
    void shouldGenerateCorrelationIdWhenNotProvided() throws Exception {

        // When/Then
        var result = mvc.perform(get("/api/features"));
        assertThat(result).hasStatusOk().headers().containsHeader(CORRELATION_ID_HEADER);
    }
}

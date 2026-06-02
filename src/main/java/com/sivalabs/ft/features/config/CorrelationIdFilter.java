package com.sivalabs.ft.features.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter to extract or generate correlation ID from request headers
 * and make it available in the context for the duration of the request
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // Extract the correlation ID from the incoming request, or generate a new one
            String correlationId = request.getHeader(CorrelationIdContext.CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = CorrelationIdContext.getCorrelationIdOrDefault();
            }

            // Store in thread-local context
            CorrelationIdContext.setCorrelationId(correlationId);

            // Add to MDC for logging
            MDC.put(CORRELATION_ID_KEY, correlationId);

            // Add the correlation ID to the response
            response.addHeader(CorrelationIdContext.CORRELATION_ID_HEADER, correlationId);

            // Continue with the request
            filterChain.doFilter(request, response);
        } finally {
            // Clean up
            CorrelationIdContext.clear();
            MDC.remove(CORRELATION_ID_KEY);
        }
    }
}

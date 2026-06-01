package com.sivalabs.ft.features.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that adds contextual information to MDC for logging
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LogRequestFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // Get or generate request ID
            String requestId = request.getHeader(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }

            // Add the request ID to the response header
            response.addHeader(REQUEST_ID_HEADER, requestId);

            // Add request ID to MDC
            MDC.put(REQUEST_ID_KEY, requestId);

            // Continue with the filter chain
            filterChain.doFilter(request, response);

        } finally {
            // Clear MDC values after request completes
            MDC.remove(REQUEST_ID_KEY);
            // Clear entire MDC to be safe
            MDC.clear();
        }
    }
}

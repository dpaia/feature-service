package com.sivalabs.ft.features.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Filter to cache request body for later access (e.g., in exception handlers).
 * Required for error logging functionality to capture POST/PUT/PATCH payloads.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCachingFilter extends OncePerRequestFilter {

    public static final String CACHED_REQUEST_BODY_ATTR = "cachedRequestBody";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Wrap request to cache content
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);

        // Pre-save wrappedRequest reference for exception handler access
        request.setAttribute("wrappedRequest", wrappedRequest);

        filterChain.doFilter(wrappedRequest, response);
    }
}

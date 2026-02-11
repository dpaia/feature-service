package com.sivalabs.ft.features.api;

import static org.springframework.http.HttpStatus.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.ErrorLoggingService;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.models.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ErrorLoggingService errorLoggingService;
    private final ObjectMapper objectMapper;

    GlobalExceptionHandler(ErrorLoggingService errorLoggingService, ObjectMapper objectMapper) {
        this.errorLoggingService = errorLoggingService;
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handle(Exception e, WebRequest request) {
        log.error("Unhandled exception", e);

        // Try to extract request body if available
        String eventPayload = extractRequestBody(request);
        if (eventPayload == null) {
            eventPayload = extractRequestPayload(request);
        }

        String userId = SecurityUtils.getCurrentUsername();
        errorLoggingService.logError(ErrorType.PROCESSING_ERROR, e.getMessage(), e, eventPayload, userId);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(INTERNAL_SERVER_ERROR, e.getMessage());
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    private String extractRequestBody(WebRequest request) {
        try {
            if (request instanceof ServletWebRequest servletRequest) {
                HttpServletRequest httpRequest = servletRequest.getRequest();

                // Get ContentCachingRequestWrapper from attribute (set by RequestCachingFilter)
                Object wrappedObj = httpRequest.getAttribute("wrappedRequest");
                if (wrappedObj instanceof org.springframework.web.util.ContentCachingRequestWrapper wrapper) {
                    byte[] content = wrapper.getContentAsByteArray();
                    if (content.length > 0) {
                        return new String(content, java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract request body", e);
        }
        return null;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handle(ResourceNotFoundException e) {
        log.error("Resource not found", e);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(NOT_FOUND, e.getMessage());
        problemDetail.setTitle("Resource not found");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail handle(BadRequestException e) {
        log.error("Bad Request", e);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, e.getMessage());
        problemDetail.setTitle("Bad Request");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handle(HttpMessageNotReadableException e, WebRequest request) {
        log.warn("Invalid JSON or enum value", e);
        String message = "Invalid request format";
        if (e.getMessage() != null && e.getMessage().contains("ActionType")) {
            message = "Invalid action type. Valid values are: "
                    + java.util.Arrays.toString(com.sivalabs.ft.features.domain.models.ActionType.values());
        }
        String userId = SecurityUtils.getCurrentUsername();
        String eventPayload = extractRequestPayload(request);
        errorLoggingService.logError(ErrorType.VALIDATION_ERROR, message, e, eventPayload, userId);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, message);
        problemDetail.setTitle("Bad Request");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handle(MethodArgumentNotValidException e, WebRequest request) {
        log.warn("Validation error", e);
        String message = "Validation failed";
        if (e.getBindingResult().hasFieldErrors()) {
            message = e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        }

        // For validation errors, we already have the deserialized object
        // Try to serialize it back to JSON for eventPayload
        String eventPayload = null;
        try {
            Object target = e.getBindingResult().getTarget();
            if (target != null) {
                eventPayload = objectMapper.writeValueAsString(target);
            }
        } catch (Exception ex) {
            log.debug("Failed to serialize validation error target", ex);
            eventPayload = extractRequestPayload(request);
        }

        String userId = SecurityUtils.getCurrentUsername();
        errorLoggingService.logError(ErrorType.VALIDATION_ERROR, message, e, eventPayload, userId);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, message);
        problemDetail.setTitle("Validation Error");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handle(MethodArgumentTypeMismatchException e, WebRequest request) {
        log.warn("Method argument type mismatch", e);
        String message = "Invalid request parameter";
        if (e.getRequiredType() != null && e.getRequiredType().isEnum()) {
            message = "Invalid " + e.getName() + " value";
        }
        String userId = SecurityUtils.getCurrentUsername();
        String eventPayload = extractRequestPayload(request);
        errorLoggingService.logError(ErrorType.VALIDATION_ERROR, message, e, eventPayload, userId);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, message);
        problemDetail.setTitle("Bad Request");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ProblemDetail handle(AuthorizationDeniedException e, WebRequest request) {
        log.warn("Access denied", e);
        String eventPayload = extractRequestPayload(request);
        String userId = SecurityUtils.getCurrentUsername();
        errorLoggingService.logError(ErrorType.PERMISSION_ERROR, "Access denied", e, eventPayload, userId);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(FORBIDDEN, "Access denied");
        problemDetail.setTitle("Forbidden");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handle(DataIntegrityViolationException e, WebRequest request) {
        log.error("Database constraint violation", e);
        String eventPayload = extractRequestPayload(request);
        String userId = SecurityUtils.getCurrentUsername();
        errorLoggingService.logError(ErrorType.DATABASE_ERROR, e.getMessage(), e, eventPayload, userId);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, "Database constraint violation");
        problemDetail.setTitle("Database Error");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    private String extractRequestPayload(WebRequest request) {
        try {
            if (request instanceof ServletWebRequest servletRequest) {
                HttpServletRequest httpRequest = servletRequest.getRequest();

                // Try to extract body from ContentCachingRequestWrapper
                String body = extractRequestBody(request);
                if (body != null && !body.isBlank()) {
                    return body;
                }

                // Fallback: for GET/DELETE or if body not available, return URI + query params
                String method = httpRequest.getMethod();
                String uri = httpRequest.getRequestURI();
                String queryString = httpRequest.getQueryString();
                StringBuilder payload = new StringBuilder();
                payload.append(method).append(" ").append(uri);
                if (queryString != null) {
                    payload.append("?").append(queryString);
                }
                return payload.toString();
            }
        } catch (Exception e) {
            log.debug("Failed to extract request payload", e);
        }
        return null;
    }
}

package com.sivalabs.ft.features.api;

import static org.springframework.http.HttpStatus.*;

import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    ProblemDetail handle(Exception e) {
        log.error("Unhandled exception", e);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(INTERNAL_SERVER_ERROR, e.getMessage());
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
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
    ProblemDetail handle(HttpMessageNotReadableException e) {
        log.warn("Invalid JSON or enum value", e);
        String message = "Invalid request format";
        if (e.getMessage() != null && e.getMessage().contains("ActionType")) {
            message = "Invalid action type. Valid values are: "
                    + java.util.Arrays.toString(com.sivalabs.ft.features.domain.models.ActionType.values());
        }
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, message);
        problemDetail.setTitle("Bad Request");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handle(MethodArgumentNotValidException e) {
        log.warn("Validation error", e);
        String message = "Validation failed";
        if (e.getBindingResult().hasFieldErrors()) {
            message = e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        }
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, message);
        problemDetail.setTitle("Validation Error");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ProblemDetail handle(AuthorizationDeniedException e) {
        log.warn("Access denied", e);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(FORBIDDEN, "Access denied");
        problemDetail.setTitle("Forbidden");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail handle(MissingServletRequestParameterException e) {
        log.warn("Missing required parameter: {}", e.getParameterName());
        String message = "Required parameter '" + e.getParameterName() + "' is missing";
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, message);
        problemDetail.setTitle("Missing Parameter");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}

package com.sivalabs.ft.features.api;

import static org.springframework.http.HttpStatus.*;

import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handle(MethodArgumentNotValidException e) {
        log.error("Validation error", e);
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .findFirst()
                .orElse(e.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, errorMessage);
        problemDetail.setTitle("Validation Error");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handle(HttpMessageNotReadableException e) {
        log.error("Message not readable", e);
        String message = e.getMessage();
        if (message != null && message.contains("JSON parse error")) {
            message = "Invalid request body: " + message.substring(message.indexOf("JSON parse error"));
        } else {
            message = "Malformed JSON request";
        }
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, message);
        problemDetail.setTitle("Invalid Request Body");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handle(MethodArgumentTypeMismatchException e) {
        log.error("Type mismatch", e);
        String message = String.format(
                "Invalid value '%s' for parameter '%s'. Expected type: %s",
                e.getValue(),
                e.getName(),
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown");
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, message);
        problemDetail.setTitle("Invalid Parameter Type");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(TypeMismatchException.class)
    ProblemDetail handle(TypeMismatchException e) {
        log.error("Type mismatch", e);
        String message = String.format(
                "Invalid value '%s'. Expected type: %s",
                e.getValue(), e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown");
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, message);
        problemDetail.setTitle("Type Conversion Error");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}

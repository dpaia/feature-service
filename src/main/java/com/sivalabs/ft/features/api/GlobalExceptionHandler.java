package com.sivalabs.ft.features.api;

import static org.springframework.http.HttpStatus.*;

import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.InvalidStatusTransitionException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
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

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handle(ConstraintViolationException e) {
        log.error("Validation failed", e);
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(BAD_REQUEST, "Validation failed: " + e.getMessage());
        problemDetail.setTitle("Validation Error");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handle(MethodArgumentNotValidException e) {
        log.error("Method argument validation failed", e);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, "Invalid request parameters");
        problemDetail.setTitle("Validation Error");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handle(MethodArgumentTypeMismatchException e) {
        log.error("Method argument type mismatch", e);
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(BAD_REQUEST, "Invalid parameter type: " + e.getMessage());
        problemDetail.setTitle("Parameter Type Error");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handle(IllegalArgumentException e) {
        log.error("Illegal argument", e);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, e.getMessage());
        problemDetail.setTitle("Invalid Argument");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    ProblemDetail handle(InvalidStatusTransitionException e) {
        log.error("Invalid status transition", e);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, e.getMessage());
        problemDetail.setTitle("Invalid Status Transition");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}

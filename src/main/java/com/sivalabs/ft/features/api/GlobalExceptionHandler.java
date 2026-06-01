package com.sivalabs.ft.features.api;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handle(ResourceNotFoundException e) {
        log.error("Resource not found", e);
        return buildProblemDetail(NOT_FOUND, "Resource not found", e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail handle(BadRequestException e) {
        log.error("Bad Request", e);
        return buildProblemDetail(BAD_REQUEST, "Bad Request", e.getMessage());
    }

    @ExceptionHandler({
        BindException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class,
        MissingServletRequestParameterException.class
    })
    ProblemDetail handleBadRequest(Exception e) {
        log.error("Bad Request", e);
        String detail = resolveBadRequestDetail(e);
        return buildProblemDetail(BAD_REQUEST, "Bad Request", detail);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handle(Exception e) {
        log.error("Unhandled exception", e);
        return buildProblemDetail(INTERNAL_SERVER_ERROR, "Internal Server Error", e.getMessage());
    }

    private String resolveBadRequestDetail(Exception e) {
        if (e instanceof BindException ex) {
            return formatBindingErrors(ex.getBindingResult());
        }
        if (e instanceof ConstraintViolationException ex) {
            String detail = ex.getConstraintViolations().stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .collect(Collectors.joining("; "));
            return defaultIfBlank(detail, "Validation failed");
        }
        if (e instanceof MethodArgumentTypeMismatchException ex) {
            String value = ex.getValue() == null ? "null" : ex.getValue().toString();
            return "Invalid value '" + value + "' for parameter '" + ex.getName() + "'";
        }
        if (e instanceof HttpMessageNotReadableException ex) {
            Throwable cause = ex.getMostSpecificCause();
            String detail = cause != null ? cause.getMessage() : ex.getMessage();
            return defaultIfBlank(detail, "Malformed JSON request");
        }
        if (e instanceof MissingServletRequestParameterException ex) {
            return "Missing required parameter '" + ex.getParameterName() + "'";
        }
        return defaultIfBlank(e.getMessage(), "Bad request");
    }

    private String formatBindingErrors(BindingResult bindingResult) {
        String detail = bindingResult.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return defaultIfBlank(detail, "Validation failed");
    }

    private ProblemDetail buildProblemDetail(org.springframework.http.HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}

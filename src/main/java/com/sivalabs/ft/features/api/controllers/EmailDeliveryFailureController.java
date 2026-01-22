package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.EmailDeliveryFailureService;
import com.sivalabs.ft.features.domain.dtos.EmailDeliveryFailureDto;
import com.sivalabs.ft.features.domain.entities.EmailDeliveryFailure;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.EmailDeliveryFailureMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/email-failures")
@Tag(name = "Email Delivery Failures Admin API")
class EmailDeliveryFailureController {
    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryFailureController.class);

    private final EmailDeliveryFailureService emailDeliveryFailureService;
    private final EmailDeliveryFailureMapper emailDeliveryFailureMapper;

    EmailDeliveryFailureController(
            EmailDeliveryFailureService emailDeliveryFailureService,
            EmailDeliveryFailureMapper emailDeliveryFailureMapper) {
        this.emailDeliveryFailureService = emailDeliveryFailureService;
        this.emailDeliveryFailureMapper = emailDeliveryFailureMapper;
    }

    @GetMapping("")
    @Operation(
            summary = "Get email delivery failures",
            description =
                    "Get all email delivery failures with pagination, sorted by date DESC (newest first). Optional date parameter for filtering (ISO 8601 format: 2026-01-12)",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array =
                                                @ArraySchema(
                                                        schema =
                                                                @Schema(
                                                                        implementation =
                                                                                EmailDeliveryFailureDto.class)))),
                @ApiResponse(responseCode = "401", description = "Unauthenticated"),
                @ApiResponse(responseCode = "403", description = "Non-ADMIN authenticated user"),
                @ApiResponse(responseCode = "400", description = "Invalid date format")
            })
    ResponseEntity<Page<EmailDeliveryFailureDto>> getEmailDeliveryFailures(
            @RequestParam(required = false) String date, Pageable pageable) {

        // Check authentication
        String username = SecurityUtils.getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }

        // Check admin role
        if (!SecurityUtils.hasAdminRole()) {
            return ResponseEntity.status(403).build();
        }

        try {
            Page<EmailDeliveryFailure> failures;

            if (date != null && !date.isBlank()) {
                try {
                    LocalDate filterDate = LocalDate.parse(date);
                    failures = emailDeliveryFailureService.getEmailDeliveryFailuresByDate(filterDate, pageable);
                } catch (DateTimeParseException e) {
                    log.warn("Invalid date format provided: {}", date);
                    return ResponseEntity.badRequest().build();
                }
            } else {
                failures = emailDeliveryFailureService.getAllEmailDeliveryFailures(pageable);
            }

            Page<EmailDeliveryFailureDto> failureDtos = failures.map(emailDeliveryFailureMapper::toDto);
            return ResponseEntity.ok(failureDtos);
        } catch (Exception e) {
            log.error("Error retrieving email delivery failures", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get email delivery failure by ID",
            description = "Get single failure record by ID, 404 if not found",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = EmailDeliveryFailureDto.class))),
                @ApiResponse(responseCode = "401", description = "Unauthenticated"),
                @ApiResponse(responseCode = "403", description = "Non-ADMIN authenticated user"),
                @ApiResponse(responseCode = "404", description = "Email delivery failure not found")
            })
    ResponseEntity<EmailDeliveryFailureDto> getEmailDeliveryFailureById(@PathVariable UUID id) {

        // Check authentication
        String username = SecurityUtils.getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }

        // Check admin role
        if (!SecurityUtils.hasAdminRole()) {
            return ResponseEntity.status(403).build();
        }

        try {
            EmailDeliveryFailure failure = emailDeliveryFailureService.getEmailDeliveryFailureById(id);
            EmailDeliveryFailureDto failureDto = emailDeliveryFailureMapper.toDto(failure);
            return ResponseEntity.ok(failureDto);
        } catch (ResourceNotFoundException e) {
            log.warn("Email delivery failure not found with id: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving email delivery failure with id: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/notification/{notificationId}")
    @Operation(
            summary = "Get email delivery failures by notification ID",
            description = "Get all failure records for specific notification, sorted by date DESC",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array =
                                                @ArraySchema(
                                                        schema =
                                                                @Schema(
                                                                        implementation =
                                                                                EmailDeliveryFailureDto.class)))),
                @ApiResponse(responseCode = "401", description = "Unauthenticated"),
                @ApiResponse(responseCode = "403", description = "Non-ADMIN authenticated user")
            })
    ResponseEntity<List<EmailDeliveryFailureDto>> getEmailDeliveryFailuresByNotificationId(
            @PathVariable UUID notificationId) {

        // Check authentication
        String username = SecurityUtils.getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }

        // Check admin role
        if (!SecurityUtils.hasAdminRole()) {
            return ResponseEntity.status(403).build();
        }

        try {
            List<EmailDeliveryFailure> failures =
                    emailDeliveryFailureService.getEmailDeliveryFailuresByNotificationId(notificationId);
            List<EmailDeliveryFailureDto> failureDtos =
                    failures.stream().map(emailDeliveryFailureMapper::toDto).toList();
            return ResponseEntity.ok(failureDtos);
        } catch (Exception e) {
            log.error("Error retrieving email delivery failures for notification: {}", notificationId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

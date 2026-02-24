package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.domain.EmailDeliveryFailureService;
import com.sivalabs.ft.features.domain.dtos.EmailDeliveryFailureDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/email-failures")
@Tag(name = "Admin Email Delivery Failures API")
@PreAuthorize("hasRole('ADMIN')")
class EmailDeliveryFailureController {

    private final EmailDeliveryFailureService emailDeliveryFailureService;

    EmailDeliveryFailureController(EmailDeliveryFailureService emailDeliveryFailureService) {
        this.emailDeliveryFailureService = emailDeliveryFailureService;
    }

    @GetMapping()
    @Operation(
            summary = "Get email delivery failures",
            description = "Get paginated list of email delivery failures, sorted by date DESC (newest first). "
                    + "Optionally filter by date (ISO 8601 format: 2026-01-12)",
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
                @ApiResponse(responseCode = "400", description = "Invalid date format"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required")
            })
    ResponseEntity<Page<EmailDeliveryFailureDto>> getEmailFailures(
            @RequestParam(required = false) String date,
            @PageableDefault(size = 20, sort = "failedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        if (date != null && !date.isBlank()) {
            try {
                LocalDate filterDate = LocalDate.parse(date);
                return ResponseEntity.ok(emailDeliveryFailureService.getFailuresByDate(filterDate, pageable));
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        return ResponseEntity.ok(emailDeliveryFailureService.getAllFailures(pageable));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get email delivery failure by ID",
            description = "Get a single email delivery failure record by ID",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = EmailDeliveryFailureDto.class))),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required"),
                @ApiResponse(responseCode = "404", description = "Not found")
            })
    ResponseEntity<EmailDeliveryFailureDto> getEmailFailureById(@PathVariable UUID id) {
        return emailDeliveryFailureService
                .getFailureById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/notification/{notificationId}")
    @Operation(
            summary = "Get email delivery failures by notification ID",
            description = "Get all email delivery failure records for a specific notification, sorted by date DESC",
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
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required")
            })
    ResponseEntity<List<EmailDeliveryFailureDto>> getEmailFailuresByNotificationId(@PathVariable UUID notificationId) {
        return ResponseEntity.ok(emailDeliveryFailureService.getFailuresByNotificationId(notificationId));
    }
}

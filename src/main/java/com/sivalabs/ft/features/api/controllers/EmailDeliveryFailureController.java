package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.domain.EmailDeliveryFailureService;
import com.sivalabs.ft.features.domain.dtos.EmailDeliveryFailureDto;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/email-failures")
@Tag(name = "Admin Email Failures API")
class EmailDeliveryFailureController {

    private final EmailDeliveryFailureService service;

    EmailDeliveryFailureController(EmailDeliveryFailureService service) {
        this.service = service;
    }

    @GetMapping("")
    @Operation(
            summary = "Get all email delivery failures",
            description = "Paginated list of email delivery failures, sorted by date DESC. Optionally filter by date.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required")
            })
    ResponseEntity<Page<EmailDeliveryFailureDto>> getFailures(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Pageable pageable) {
        return ResponseEntity.ok(service.getFailures(date, pageable));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get email delivery failure by ID",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required"),
                @ApiResponse(responseCode = "404", description = "Not found")
            })
    ResponseEntity<EmailDeliveryFailureDto> getFailureById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(service.getFailureById(id));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/notification/{notificationId}")
    @Operation(
            summary = "Get email delivery failures for a specific notification",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required")
            })
    ResponseEntity<List<EmailDeliveryFailureDto>> getFailuresByNotificationId(@PathVariable UUID notificationId) {
        return ResponseEntity.ok(service.getFailuresByNotificationId(notificationId));
    }
}

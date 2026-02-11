package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.domain.ErrorLoggingService;
import com.sivalabs.ft.features.domain.HealthMetricsService;
import com.sivalabs.ft.features.domain.ReprocessService;
import com.sivalabs.ft.features.domain.dtos.ErrorLogDto;
import com.sivalabs.ft.features.domain.dtos.HealthMetricsDto;
import com.sivalabs.ft.features.domain.models.ErrorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin API")
@PreAuthorize("hasRole('ADMIN')")
class AdminController {

    private final ErrorLoggingService errorLoggingService;
    private final HealthMetricsService healthMetricsService;
    private final ReprocessService reprocessService;

    AdminController(
            ErrorLoggingService errorLoggingService,
            HealthMetricsService healthMetricsService,
            ReprocessService reprocessService) {
        this.errorLoggingService = errorLoggingService;
        this.healthMetricsService = healthMetricsService;
        this.reprocessService = reprocessService;
    }

    @GetMapping("/health")
    @Operation(
            summary = "Get system health metrics",
            description = "Get system health metrics for specified time period (default: last 7 days)",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = HealthMetricsDto.class))),
                @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
            })
    ResponseEntity<HealthMetricsDto> getSystemHealth(
            @RequestParam(required = false) Instant startDate, @RequestParam(required = false) Instant endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().build();
        }
        HealthMetricsDto metrics = healthMetricsService.getSystemHealth(startDate, endDate);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/errors")
    @Operation(
            summary = "Get error logs with pagination and filtering",
            description = "Get paginated list of error logs with optional filtering by error type and date range",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
            })
    ResponseEntity<Page<ErrorLogDto>> getErrors(
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) ErrorType errorType,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().build();
        }
        Page<ErrorLogDto> errors = errorLoggingService.getErrors(pageable, errorType, startDate, endDate);
        return ResponseEntity.ok(errors);
    }

    @GetMapping("/errors/{id}")
    @Operation(
            summary = "Get error log by ID",
            description = "Get detailed error log information by ID",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorLogDto.class))),
                @ApiResponse(responseCode = "404", description = "Error log not found"),
                @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
            })
    ResponseEntity<ErrorLogDto> getErrorById(@PathVariable Long id) {
        return errorLoggingService
                .getErrorById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/errors/{id}/reprocess")
    @Operation(
            summary = "Reprocess failed error log",
            description = "Attempt to reprocess a failed event. Always returns 200 OK with ErrorLogDto. "
                    + "Success: original record updated to resolved=true. "
                    + "Failure: new error log record created with resolved=false",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Reprocessing attempted (check resolved field for result)",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorLogDto.class))),
                @ApiResponse(responseCode = "404", description = "Error log not found"),
                @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
            })
    ResponseEntity<ErrorLogDto> reprocessError(@PathVariable Long id) {
        ErrorLogDto result = reprocessService.reprocessSingleError(id);
        return ResponseEntity.ok(result);
    }
}

package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.ReprocessRequest;
import com.sivalabs.ft.features.domain.ErrorLoggingService;
import com.sivalabs.ft.features.domain.HealthMetricsService;
import com.sivalabs.ft.features.domain.ReprocessService;
import com.sivalabs.ft.features.domain.dtos.ErrorLogDto;
import com.sivalabs.ft.features.domain.dtos.HealthMetricsDto;
import com.sivalabs.ft.features.domain.dtos.ReprocessResultDto;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.models.ErrorType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@SecurityRequirement(name = "oauth2")
@Tag(name = "Admin Monitoring", description = "Admin tools for monitoring and data management")
public class AdminController {

    private final HealthMetricsService healthMetricsService;
    private final ErrorLoggingService errorLoggingService;
    private final ReprocessService reprocessService;

    public AdminController(
            HealthMetricsService healthMetricsService,
            ErrorLoggingService errorLoggingService,
            ReprocessService reprocessService) {
        this.healthMetricsService = healthMetricsService;
        this.errorLoggingService = errorLoggingService;
        this.reprocessService = reprocessService;
    }

    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HealthMetricsDto> getSystemHealth(
            @RequestParam(required = false) Instant startDate, @RequestParam(required = false) Instant endDate) {

        // Default to last 7 days if not provided
        if (startDate == null) {
            startDate = Instant.now().minus(Duration.ofDays(7));
        }
        if (endDate == null) {
            endDate = Instant.now();
        }

        validateDateRange(startDate, endDate);

        return ResponseEntity.ok(healthMetricsService.getSystemHealth(startDate, endDate));
    }

    @GetMapping("/errors")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ErrorLogDto>> getErrors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ErrorType errorType,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate) {

        return ResponseEntity.ok(errorLoggingService.getErrors(page, size, errorType, startDate, endDate));
    }

    @GetMapping("/errors/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ErrorLogDto> getError(@PathVariable Long id) {
        return ResponseEntity.ok(errorLoggingService.getErrorById(id));
    }

    @PostMapping("/reprocess")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReprocessResultDto> reprocessErrors(@RequestBody ReprocessRequest request) {
        // Validate date range if provided
        if (request.dateRange() != null) {
            validateDateRange(
                    request.dateRange().startDate(), request.dateRange().endDate());
        }

        ReprocessResultDto result = reprocessService.reprocessErrors(
                request.errorLogIds(),
                request.dateRange() != null ? request.dateRange().startDate() : null,
                request.dateRange() != null ? request.dateRange().endDate() : null,
                request.dryRun() != null ? request.dryRun() : false);

        return ResponseEntity.ok(result);
    }

    private void validateDateRange(Instant startDate, Instant endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate must be before endDate");
        }
    }
}

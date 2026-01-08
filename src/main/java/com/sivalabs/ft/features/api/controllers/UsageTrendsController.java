package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.domain.UsageTrendsService;
import com.sivalabs.ft.features.domain.dtos.TrendDataDto;
import com.sivalabs.ft.features.domain.models.ActionType;
import com.sivalabs.ft.features.domain.models.PeriodType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for usage trends analytics.
 * Provides endpoints for retrieving usage trends over time with various grouping options.
 */
@RestController
@RequestMapping("/api/usage/trends")
@Tag(name = "Usage Trends API", description = "Analytics for usage trends over time")
public class UsageTrendsController {
    private static final Logger log = LoggerFactory.getLogger(UsageTrendsController.class);

    private final UsageTrendsService usageTrendsService;

    public UsageTrendsController(UsageTrendsService usageTrendsService) {
        this.usageTrendsService = usageTrendsService;
    }

    @GetMapping
    @Operation(
            summary = "Get Usage Trends Over Time",
            description =
                    "Retrieve usage trends grouped by specified time periods (daily, weekly, monthly) with optional filtering",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Trends retrieved successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = TrendDataDto.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid parameters (invalid dates, unsupported period type)"),
                @ApiResponse(responseCode = "401", description = "Unauthorized - authentication required")
            })
    public ResponseEntity<TrendDataDto> getTrends(
            @RequestParam(required = true) String periodType,
            @RequestParam(required = false) String featureCode,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        try {
            // Validate and parse period type
            PeriodType parsedPeriodType;
            try {
                parsedPeriodType = PeriodType.valueOf(periodType.toUpperCase());
            } catch (IllegalArgumentException | NullPointerException e) {
                log.warn("Invalid period type: {}", periodType);
                return ResponseEntity.badRequest().build();
            }

            // Parse date parameters
            Instant start = startDate != null ? Instant.parse(startDate) : null;
            Instant end = endDate != null ? Instant.parse(endDate) : null;

            // Validate date range
            if (start != null && end != null && start.isAfter(end)) {
                log.warn("Invalid date range: startDate {} is after endDate {}", start, end);
                return ResponseEntity.badRequest().build();
            }

            log.debug(
                    "Processing trends request: periodType={}, featureCode={}, productCode={}",
                    periodType,
                    featureCode,
                    productCode);

            // Calculate trends
            TrendDataDto trends = usageTrendsService.calculateTrends(
                    parsedPeriodType, featureCode, productCode, actionType, start, end);

            log.debug("Calculated trends with {} data points", trends.trends().size());

            return ResponseEntity.ok(trends);

        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request parameters: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error calculating trends", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

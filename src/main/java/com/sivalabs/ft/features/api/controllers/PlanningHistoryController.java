package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.domain.PlanningHistoryService;
import com.sivalabs.ft.features.domain.dtos.PlanningHistoryDto;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.models.ChangeType;
import com.sivalabs.ft.features.domain.models.EntityType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Planning History", description = "Planning History Management")
@PreAuthorize("hasRole('USER')")
@Validated
public class PlanningHistoryController {

    private final PlanningHistoryService planningHistoryService;

    public PlanningHistoryController(PlanningHistoryService planningHistoryService) {
        this.planningHistoryService = planningHistoryService;
    }

    @GetMapping("/planning-history")
    @Operation(summary = "Query planning history", description = "Query planning history with filters and pagination")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Successfully retrieved planning history"),
                @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
                @ApiResponse(responseCode = "403", description = "Forbidden")
            })
    public ResponseEntity<Page<PlanningHistoryDto>> queryPlanningHistory(
            @Parameter(description = "Entity type filter (RELEASE or FEATURE)") @RequestParam(required = false)
                    EntityType entityType,
            @Parameter(description = "Entity code filter") @RequestParam(required = false) String entityCode,
            @Parameter(description = "Changed by user filter") @RequestParam(required = false) String changedBy,
            @Parameter(description = "Change type filter") @RequestParam(required = false) ChangeType changeType,
            @Parameter(description = "Date from filter (ISO-8601 format)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant dateFrom,
            @Parameter(description = "Date to filter (ISO-8601 format)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant dateTo,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Sort criteria (format: property,direction)")
                    @RequestParam(defaultValue = "changedAt,desc")
                    String sort) {

        try {
            Page<PlanningHistoryDto> history = planningHistoryService.queryPlanningHistory(
                    entityType, entityCode, changedBy, changeType, dateFrom, dateTo, page, size, sort);
            return ResponseEntity.ok(history);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid request parameters: " + e.getMessage());
        }
    }

    @GetMapping("/releases/{code}/history")
    @Operation(summary = "Get release history", description = "Get planning history for a specific release")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Successfully retrieved release history"),
                @ApiResponse(responseCode = "404", description = "Release not found"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden")
            })
    public ResponseEntity<Page<PlanningHistoryDto>> getReleaseHistory(
            @Parameter(description = "Release code", required = true) @PathVariable String code,
            @Parameter(description = "Change type filter") @RequestParam(required = false) ChangeType changeType,
            @Parameter(description = "Date from filter (ISO-8601 format)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant dateFrom,
            @Parameter(description = "Date to filter (ISO-8601 format)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant dateTo,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Sort criteria (format: property,direction)")
                    @RequestParam(defaultValue = "changedAt,desc")
                    String sort) {

        try {
            Page<PlanningHistoryDto> history =
                    planningHistoryService.getReleaseHistory(code, changeType, dateFrom, dateTo, page, size, sort);
            return ResponseEntity.ok(history);
        } catch (ResourceNotFoundException e) {
            throw e; // Re-throw as is
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid request parameters: " + e.getMessage());
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                throw new ResourceNotFoundException("Release not found: " + code);
            }
            throw new BadRequestException("Invalid request parameters: " + e.getMessage());
        }
    }

    @GetMapping("/features/{code}/history")
    @Operation(summary = "Get feature history", description = "Get planning history for a specific feature")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Successfully retrieved feature history"),
                @ApiResponse(responseCode = "404", description = "Feature not found"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden")
            })
    public ResponseEntity<Page<PlanningHistoryDto>> getFeatureHistory(
            @Parameter(description = "Feature code", required = true) @PathVariable String code,
            @Parameter(description = "Change type filter") @RequestParam(required = false) ChangeType changeType,
            @Parameter(description = "Date from filter (ISO-8601 format)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant dateFrom,
            @Parameter(description = "Date to filter (ISO-8601 format)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant dateTo,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Sort criteria (format: property,direction)")
                    @RequestParam(defaultValue = "changedAt,desc")
                    String sort) {

        try {
            Page<PlanningHistoryDto> history =
                    planningHistoryService.getFeatureHistory(code, changeType, dateFrom, dateTo, page, size, sort);
            return ResponseEntity.ok(history);
        } catch (ResourceNotFoundException e) {
            throw e; // Re-throw as is
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid request parameters: " + e.getMessage());
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                throw new ResourceNotFoundException("Feature not found: " + code);
            }
            throw new BadRequestException("Invalid request parameters: " + e.getMessage());
        }
    }
}

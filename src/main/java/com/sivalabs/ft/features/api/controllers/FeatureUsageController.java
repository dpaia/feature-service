package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.CreateUsageEventPayload;
import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.FeatureUsageService;
import com.sivalabs.ft.features.domain.dtos.FeatureStatsDto;
import com.sivalabs.ft.features.domain.dtos.FeatureUsageDto;
import com.sivalabs.ft.features.domain.dtos.ProductStatsDto;
import com.sivalabs.ft.features.domain.dtos.UsageStatsDto;
import com.sivalabs.ft.features.domain.models.ActionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/usage")
@Tag(name = "Usage Analytics API")
class FeatureUsageController {

    private final FeatureUsageService featureUsageService;

    FeatureUsageController(FeatureUsageService featureUsageService) {
        this.featureUsageService = featureUsageService;
    }

    @GetMapping("/events")
    @Operation(
            summary = "Get usage events",
            description = "Retrieve all usage events with optional filters",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content = @Content(mediaType = "application/json")),
                @ApiResponse(responseCode = "400", description = "Invalid date format or date range"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    ResponseEntity<List<FeatureUsageDto>> getUsageEvents(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String featureCode,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().build();
        }
        List<FeatureUsageDto> usageEvents = featureUsageService.findAllUsageEvents(
                userId, featureCode, productCode, actionType, startDate, endDate);
        return ResponseEntity.ok(usageEvents);
    }

    @GetMapping("/stats")
    @Operation(
            summary = "Get usage statistics",
            description = "Retrieve aggregated usage statistics",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = UsageStatsDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid date format or date range"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    ResponseEntity<UsageStatsDto> getUsageStats(
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().build();
        }
        UsageStatsDto stats = featureUsageService.getUsageStats(actionType, startDate, endDate);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/user/{userId}")
    @Operation(
            summary = "Get usage events by user",
            description = "Retrieve usage events for a specific user",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class)))
            })
    ResponseEntity<Page<FeatureUsageDto>> getUserUsage(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<FeatureUsageDto> usageEvents = featureUsageService.findByUserId(userId, pageable);
        return ResponseEntity.ok(usageEvents);
    }

    @GetMapping("/feature/{featureCode}")
    @Operation(
            summary = "Get usage events by feature",
            description = "Retrieve usage events for a specific feature",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class)))
            })
    ResponseEntity<Page<FeatureUsageDto>> getFeatureUsage(
            @PathVariable String featureCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<FeatureUsageDto> usageEvents = featureUsageService.findByFeatureCode(featureCode, pageable);
        return ResponseEntity.ok(usageEvents);
    }

    @GetMapping("/product/{productCode}")
    @Operation(
            summary = "Get usage events by product",
            description = "Retrieve usage events for a specific product",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class)))
            })
    ResponseEntity<Page<FeatureUsageDto>> getProductUsage(
            @PathVariable String productCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<FeatureUsageDto> usageEvents = featureUsageService.findByProductCode(productCode, pageable);
        return ResponseEntity.ok(usageEvents);
    }

    @GetMapping("/top-features")
    @Operation(
            summary = "Get top features",
            description = "Retrieve most accessed features",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content = @Content(mediaType = "application/json"))
            })
    ResponseEntity<Map<String, Long>> getTopFeatures(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "10") int limit) {
        Map<String, Long> topFeatures = featureUsageService.getTopFeatures(startDate, endDate, limit);
        return ResponseEntity.ok(topFeatures);
    }

    @GetMapping("/top-users")
    @Operation(
            summary = "Get top users",
            description = "Retrieve most active users",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content = @Content(mediaType = "application/json"))
            })
    ResponseEntity<Map<String, Long>> getTopUsers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "10") int limit) {
        Map<String, Long> topUsers = featureUsageService.getTopUsers(startDate, endDate, limit);
        return ResponseEntity.ok(topUsers);
    }

    @PostMapping("")
    @Operation(
            summary = "Post usage event",
            description = "Create a new feature usage event",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = CreateUsageEventPayload.class))),
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Usage event created successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = FeatureUsageDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid request data"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    ResponseEntity<FeatureUsageDto> postUsageEvent(
            @Valid @RequestBody CreateUsageEventPayload payload, HttpServletRequest httpRequest) {
        String username = SecurityUtils.getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        FeatureUsageDto createdEvent = featureUsageService.logUsage(
                username,
                payload.featureCode(),
                payload.productCode(),
                payload.releaseCode(),
                payload.actionType(),
                payload.context(),
                ipAddress,
                userAgent);

        if (createdEvent == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdEvent.id())
                .toUri();

        return ResponseEntity.created(location).body(createdEvent);
    }

    @GetMapping("/feature/{featureCode}/events")
    @Operation(
            summary = "Get feature usage events",
            description = "Get detailed usage events for a specific feature with optional filtering",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    ResponseEntity<List<FeatureUsageDto>> getFeatureUsageEvents(
            @PathVariable String featureCode,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        List<FeatureUsageDto> events =
                featureUsageService.getFeatureUsageEvents(featureCode, actionType, startDate, endDate);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/feature/{featureCode}/stats")
    @Operation(
            summary = "Get feature statistics",
            description = "Retrieve comprehensive statistics for a specific feature",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = FeatureStatsDto.class)))
            })
    ResponseEntity<FeatureStatsDto> getFeatureStats(
            @PathVariable String featureCode,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        FeatureStatsDto stats = featureUsageService.getFeatureStats(featureCode, actionType, startDate, endDate);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/product/{productCode}/events")
    @Operation(
            summary = "Get product usage events",
            description = "Get detailed usage events for a specific product with optional filtering",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    ResponseEntity<List<FeatureUsageDto>> getProductUsageEvents(
            @PathVariable String productCode,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        List<FeatureUsageDto> events =
                featureUsageService.getProductUsageEvents(productCode, actionType, startDate, endDate);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/product/{productCode}/stats")
    @Operation(
            summary = "Get product statistics",
            description = "Retrieve comprehensive statistics for a specific product",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProductStatsDto.class)))
            })
    ResponseEntity<ProductStatsDto> getProductStats(
            @PathVariable String productCode,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        ProductStatsDto stats = featureUsageService.getProductStats(productCode, actionType, startDate, endDate);
        return ResponseEntity.ok(stats);
    }
}

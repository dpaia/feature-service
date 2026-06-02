package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.CreateUsageEventPayload;
import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.FeatureUsageService;
import com.sivalabs.ft.features.domain.dtos.*;
import com.sivalabs.ft.features.domain.models.ActionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/usage")
@Tag(name = "Feature Usage API")
public class FeatureUsageController {
    private static final Logger log = LoggerFactory.getLogger(FeatureUsageController.class);

    private final FeatureUsageService featureUsageService;

    public FeatureUsageController(FeatureUsageService featureUsageService) {
        this.featureUsageService = featureUsageService;
    }

    @PostMapping("")
    @Operation(
            summary = "Create Usage Event",
            description = "Create a new feature usage event",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Usage event created successfully",
                        headers = @Header(name = "Location", description = "URI of the created usage event"),
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = FeatureUsageDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    public ResponseEntity<FeatureUsageDto> createUsageEvent(
            @RequestBody @Valid CreateUsageEventPayload payload, HttpServletRequest request) {

        String username = SecurityUtils.getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }

        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        FeatureUsageDto usageEvent = featureUsageService.createUsageEvent(
                username,
                payload.featureCode(),
                payload.productCode(),
                payload.releaseCode(),
                payload.actionType(),
                payload.context(),
                ipAddress,
                userAgent);

        if (usageEvent != null) {
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(usageEvent.id())
                    .toUri();
            return ResponseEntity.created(location).body(usageEvent);
        }

        return ResponseEntity.badRequest().build();
    }

    @GetMapping("/stats")
    @Operation(
            summary = "Overall Usage Statistics",
            description = "Get overall usage statistics with optional filters",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Statistics retrieved successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = UsageStatsDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid parameters"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    public ResponseEntity<UsageStatsDto> getOverallStats(
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        try {
            Instant start = startDate != null ? Instant.parse(startDate) : null;
            Instant end = endDate != null ? Instant.parse(endDate) : null;

            // Validate date range logic
            if (start != null && end != null && start.isAfter(end)) {
                log.warn("Invalid date range: startDate {} is after endDate {}", start, end);
                return ResponseEntity.badRequest().build();
            }

            UsageStatsDto stats = featureUsageService.getOverallStats(actionType, start, end);
            return ResponseEntity.ok(stats);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/feature/{featureCode}/stats")
    @Operation(
            summary = "Feature Statistics",
            description = "Get statistics for a specific feature",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Feature statistics retrieved successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = FeatureStatsDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid parameters"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    public ResponseEntity<FeatureStatsDto> getFeatureStats(
            @PathVariable String featureCode,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        try {
            Instant start = startDate != null ? Instant.parse(startDate) : null;
            Instant end = endDate != null ? Instant.parse(endDate) : null;

            // Validate date range logic
            if (start != null && end != null && start.isAfter(end)) {
                log.warn("Invalid date range: startDate {} is after endDate {}", start, end);
                return ResponseEntity.badRequest().build();
            }

            FeatureStatsDto stats = featureUsageService.getFeatureStats(featureCode, actionType, start, end);
            return ResponseEntity.ok(stats);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/product/{productCode}/stats")
    @Operation(
            summary = "Product Statistics",
            description = "Get statistics for a specific product",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Product statistics retrieved successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProductStatsDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid parameters"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    public ResponseEntity<ProductStatsDto> getProductStats(
            @PathVariable String productCode,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        try {
            Instant start = startDate != null ? Instant.parse(startDate) : null;
            Instant end = endDate != null ? Instant.parse(endDate) : null;

            // Validate date range logic
            if (start != null && end != null && start.isAfter(end)) {
                log.warn("Invalid date range: startDate {} is after endDate {}", start, end);
                return ResponseEntity.badRequest().build();
            }

            ProductStatsDto stats = featureUsageService.getProductStats(productCode, actionType, start, end);
            return ResponseEntity.ok(stats);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/events")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCT_MANAGER')")
    @Operation(
            summary = "All Usage Events List",
            description = "Get paginated usage events with optional filters (Admin/Product Manager only)",
            responses = {
                @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid parameters"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden - Admin/Product Manager role required")
            })
    public ResponseEntity<Page<FeatureUsageDto>> getAllEvents(
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String featureCode,
            @RequestParam(required = false) String productCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Instant start = startDate != null ? Instant.parse(startDate) : null;
            Instant end = endDate != null ? Instant.parse(endDate) : null;

            // Validate date range logic
            if (start != null && end != null && start.isAfter(end)) {
                log.warn("Invalid date range: startDate {} is after endDate {}", start, end);
                return ResponseEntity.badRequest().build();
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<FeatureUsageDto> events = featureUsageService.getAllEventsPaginated(
                    actionType, start, end, userId, featureCode, productCode, pageable);
            return ResponseEntity.ok(events);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/feature/{featureCode}/events")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCT_MANAGER')")
    @Operation(
            summary = "Feature Events List",
            description = "Get paginated events for a specific feature (Admin/Product Manager only)",
            responses = {
                @ApiResponse(responseCode = "200", description = "Feature events retrieved successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid parameters"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden - Admin/Product Manager role required")
            })
    public ResponseEntity<Page<FeatureUsageDto>> getFeatureEvents(
            @PathVariable String featureCode,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Instant start = startDate != null ? Instant.parse(startDate) : null;
            Instant end = endDate != null ? Instant.parse(endDate) : null;

            // Validate date range logic
            if (start != null && end != null && start.isAfter(end)) {
                log.warn("Invalid date range: startDate {} is after endDate {}", start, end);
                return ResponseEntity.badRequest().build();
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<FeatureUsageDto> events = featureUsageService.getFeatureEventsPaginatedWithFilters(
                    featureCode, actionType, start, end, pageable);
            return ResponseEntity.ok(events);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/product/{productCode}/events")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCT_MANAGER')")
    @Operation(
            summary = "Product Events List",
            description = "Get paginated events for a specific product (Admin/Product Manager only)",
            responses = {
                @ApiResponse(responseCode = "200", description = "Product events retrieved successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid parameters"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden - Admin/Product Manager role required")
            })
    public ResponseEntity<Page<FeatureUsageDto>> getProductEvents(
            @PathVariable String productCode,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Instant start = startDate != null ? Instant.parse(startDate) : null;
            Instant end = endDate != null ? Instant.parse(endDate) : null;

            // Validate date range logic
            if (start != null && end != null && start.isAfter(end)) {
                log.warn("Invalid date range: startDate {} is after endDate {}", start, end);
                return ResponseEntity.badRequest().build();
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<FeatureUsageDto> events = featureUsageService.getProductEventsPaginatedWithFilters(
                    productCode, actionType, start, end, pageable);
            return ResponseEntity.ok(events);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/top-features")
    @Operation(
            summary = "Most Accessed Features List",
            description = "Get list of most accessed features with usage counts",
            responses = {
                @ApiResponse(responseCode = "200", description = "Top features retrieved successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid parameters"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    public ResponseEntity<List<TopItemDto>> getTopFeatures(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "10") int limit) {

        try {
            Instant start = startDate != null ? Instant.parse(startDate) : null;
            Instant end = endDate != null ? Instant.parse(endDate) : null;

            // Validate date range logic
            if (start != null && end != null && start.isAfter(end)) {
                log.warn("Invalid date range: startDate {} is after endDate {}", start, end);
                return ResponseEntity.badRequest().build();
            }

            List<TopItemDto> topFeatures = featureUsageService.getTopFeatures(start, end, limit);
            return ResponseEntity.ok(topFeatures);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/top-users")
    @Operation(
            summary = "Most Active Users List",
            description = "Get list of most active users with activity counts",
            responses = {
                @ApiResponse(responseCode = "200", description = "Top users retrieved successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid parameters"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    public ResponseEntity<List<TopItemDto>> getTopUsers(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "10") int limit) {

        try {
            Instant start = startDate != null ? Instant.parse(startDate) : null;
            Instant end = endDate != null ? Instant.parse(endDate) : null;

            // Validate date range logic
            if (start != null && end != null && start.isAfter(end)) {
                log.warn("Invalid date range: startDate {} is after endDate {}", start, end);
                return ResponseEntity.badRequest().build();
            }

            List<TopItemDto> topUsers = featureUsageService.getTopUsers(start, end, limit);
            return ResponseEntity.ok(topUsers);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/user/{userId}")
    @Operation(
            summary = "User Usage Events",
            description = "Get paginated usage events for a specific user",
            responses = {
                @ApiResponse(responseCode = "200", description = "User events retrieved successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid parameters"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    public ResponseEntity<Page<FeatureUsageDto>> getUserEvents(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<FeatureUsageDto> events = featureUsageService.getUserEvents(userId, pageable);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/feature/{featureCode}")
    @Operation(
            summary = "Feature Usage Events",
            description = "Get paginated usage events for a specific feature",
            responses = {
                @ApiResponse(responseCode = "200", description = "Feature events retrieved successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid parameters"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    public ResponseEntity<Page<FeatureUsageDto>> getFeatureEventsPaginated(
            @PathVariable String featureCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<FeatureUsageDto> events = featureUsageService.getFeatureEventsPaginated(featureCode, pageable);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/product/{productCode}")
    @Operation(
            summary = "Product Usage Events",
            description = "Get paginated usage events for a specific product",
            responses = {
                @ApiResponse(responseCode = "200", description = "Product events retrieved successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid parameters"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    public ResponseEntity<Page<FeatureUsageDto>> getProductEventsPaginated(
            @PathVariable String productCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<FeatureUsageDto> events = featureUsageService.getProductEventsPaginated(productCode, pageable);
        return ResponseEntity.ok(events);
    }
}

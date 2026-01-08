package com.sivalabs.ft.features.api.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.domain.SegmentAnalyticsService;
import com.sivalabs.ft.features.domain.dtos.SegmentAnalyticsDto;
import com.sivalabs.ft.features.domain.dtos.UserSegmentDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user segment analytics.
 * Provides endpoints for analyzing usage patterns across user segments.
 * Requires ADMIN or PRODUCT_MANAGER role for access.
 */
@RestController
@RequestMapping("/api/usage/segments")
@Tag(name = "Segment Analytics API", description = "Analytics for user segments and behavior patterns")
public class SegmentAnalyticsController {
    private static final Logger log = LoggerFactory.getLogger(SegmentAnalyticsController.class);

    private final SegmentAnalyticsService segmentAnalyticsService;
    private final ObjectMapper objectMapper;

    public SegmentAnalyticsController(SegmentAnalyticsService segmentAnalyticsService, ObjectMapper objectMapper) {
        this.segmentAnalyticsService = segmentAnalyticsService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCT_MANAGER')")
    @Operation(
            summary = "Get Segment Analytics",
            description = "Analyze usage patterns for user segments. "
                    + "Supports both predefined segments and custom criteria via tags. "
                    + "Requires ADMIN or PRODUCT_MANAGER role.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Segment analytics retrieved successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = SegmentAnalyticsDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid parameters (malformed tags JSON)"),
                @ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
                @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN or PRODUCT_MANAGER role")
            })
    public ResponseEntity<List<SegmentAnalyticsDto>> getSegmentAnalytics(
            @Parameter(description = "Comma-separated list of predefined segment names", example = "mobile,desktop")
                    @RequestParam(required = false)
                    String segments,
            @Parameter(description = "Custom segment name for custom criteria", example = "US Mobile Users")
                    @RequestParam(required = false)
                    String segmentName,
            @Parameter(
                            description = "JSON string with custom tags for filtering",
                            example = "{\"device\":\"mobile\",\"region\":\"US\"}")
                    @RequestParam(required = false)
                    String tags,
            @Parameter(description = "Start date for filtering (ISO-8601)", example = "2024-01-01T00:00:00Z")
                    @RequestParam(required = false)
                    String startDate,
            @Parameter(description = "End date for filtering (ISO-8601)", example = "2024-12-31T23:59:59Z")
                    @RequestParam(required = false)
                    String endDate) {

        try {
            // Parse date parameters
            Instant start = startDate != null ? Instant.parse(startDate) : null;
            Instant end = endDate != null ? Instant.parse(endDate) : null;

            // Validate date range
            if (start != null && end != null && start.isAfter(end)) {
                log.warn("Invalid date range: start {} is after end {}", start, end);
                return ResponseEntity.badRequest().build();
            }

            List<SegmentAnalyticsDto> results;

            // Case 1: Custom segment with tags
            if (tags != null && !tags.isEmpty()) {
                Map<String, String> tagMap = parseTagsJson(tags);
                if (tagMap == null) {
                    log.warn("Failed to parse tags JSON: {}", tags);
                    return ResponseEntity.badRequest().build();
                }

                String customSegmentName = segmentName != null ? segmentName : "Custom Segment";
                log.debug("Analyzing custom segment '{}' with {} tags", customSegmentName, tagMap.size());

                SegmentAnalyticsDto customResult =
                        segmentAnalyticsService.analyzeCustomSegment(customSegmentName, tagMap, start, end);
                results = List.of(customResult);
            }
            // Case 2: Predefined segments
            else if (segments != null && !segments.isEmpty()) {
                List<String> segmentList =
                        Arrays.stream(segments.split(",")).map(String::trim).toList();

                log.debug("Analyzing {} predefined segments", segmentList.size());

                results = segmentAnalyticsService.analyzeSegments(segmentList, start, end);
            }
            // Case 3: All predefined segments
            else {
                log.debug("Analyzing all predefined segments");
                results = segmentAnalyticsService.analyzeSegments(null, start, end);
            }

            return ResponseEntity.ok(results);

        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error analyzing segments", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/predefined")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCT_MANAGER')")
    @Operation(
            summary = "Get Predefined Segments",
            description =
                    "Retrieve list of available predefined user segments. Requires ADMIN or PRODUCT_MANAGER role.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Predefined segments retrieved successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = UserSegmentDto.class))),
                @ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
                @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN or PRODUCT_MANAGER role")
            })
    public ResponseEntity<List<UserSegmentDto>> getPredefinedSegments() {
        log.debug("Retrieving predefined segments");
        List<UserSegmentDto> segments = segmentAnalyticsService.getPredefinedSegments();
        return ResponseEntity.ok(segments);
    }

    /**
     * Parse tags JSON string into Map.
     *
     * @param tagsJson JSON string
     * @return Map of tag key-value pairs, or null if parsing fails
     */
    private Map<String, String> parseTagsJson(String tagsJson) {
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse tags JSON: {}", tagsJson, e);
            return null;
        }
    }
}

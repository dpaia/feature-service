package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.ReportingService;
import com.sivalabs.ft.features.domain.RoadmapService;
import com.sivalabs.ft.features.domain.dtos.ByOwnerRoadmapResponseDto;
import com.sivalabs.ft.features.domain.dtos.MultiProductRoadmapResponseDto;
import com.sivalabs.ft.features.domain.dtos.RoadmapFilterDto;
import com.sivalabs.ft.features.domain.dtos.RoadmapResponseDto;
import com.sivalabs.ft.features.domain.models.GroupByOption;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/roadmap")
@Tag(name = "Roadmap API", description = "API for roadmap visualization and export")
@Validated
public class RoadmapController {
    private static final Logger log = LoggerFactory.getLogger(RoadmapController.class);

    private final RoadmapService roadmapService;
    private final ReportingService reportingService;

    public RoadmapController(RoadmapService roadmapService, ReportingService reportingService) {
        this.roadmapService = roadmapService;
        this.reportingService = reportingService;
    }

    @GetMapping("")
    @Operation(
            summary = "Get product roadmap",
            description = "Retrieve product roadmap with releases, features, progress metrics, and health indicators",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = RoadmapResponseDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid query parameters"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    public ResponseEntity<RoadmapResponseDto> getRoadmap(
            @Parameter(description = "Filter by single product code") @RequestParam(required = false)
                    String productCode,
            @Parameter(description = "Start date filter in format YYYY-MM-DD") @RequestParam(required = false)
                    String startDate,
            @Parameter(description = "End date filter in format YYYY-MM-DD") @RequestParam(required = false)
                    String endDate,
            @Parameter(description = "Include completed releases") @RequestParam(defaultValue = "true")
                    boolean includeCompleted,
            @Parameter(description = "Group results by field (PRODUCT, STATUS, ASSIGNEE)")
                    @RequestParam(required = false)
                    String groupBy) {

        log.info(
                "Getting roadmap - productCode: {}, startDate: {}, endDate: {}, includeCompleted: {}, groupBy: {}",
                productCode,
                startDate,
                endDate,
                includeCompleted,
                groupBy);

        try {
            String username = SecurityUtils.getCurrentUsername();
            RoadmapFilterDto filter =
                    createFilter(productCode, null, startDate, endDate, includeCompleted, groupBy, null);
            RoadmapResponseDto response = roadmapService.getRoadmap(username, filter);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request parameters: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error getting roadmap", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/multi-product")
    @Operation(
            summary = "Get multi-product roadmap",
            description = "Retrieve aggregated roadmap across multiple products grouped by product",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = MultiProductRoadmapResponseDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid or missing productCodes"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    public ResponseEntity<MultiProductRoadmapResponseDto> getMultiProductRoadmap(
            @Parameter(description = "Array of product codes", required = true) @RequestParam @NotEmpty List<String> productCodes,
            @Parameter(description = "Start date filter in format YYYY-MM-DD") @RequestParam(required = false)
                    String startDate,
            @Parameter(description = "End date filter in format YYYY-MM-DD") @RequestParam(required = false)
                    String endDate,
            @Parameter(description = "Include completed releases") @RequestParam(defaultValue = "true")
                    boolean includeCompleted,
            @Parameter(description = "Group results by field (PRODUCT, STATUS, ASSIGNEE)")
                    @RequestParam(required = false)
                    String groupBy) {

        log.info(
                "Getting multi-product roadmap - productCodes: {}, startDate: {}, endDate: {}, includeCompleted: {}, groupBy: {}",
                productCodes,
                startDate,
                endDate,
                includeCompleted,
                groupBy);

        try {
            String username = SecurityUtils.getCurrentUsername();
            RoadmapFilterDto filter =
                    createFilter(null, productCodes, startDate, endDate, includeCompleted, groupBy, null);
            MultiProductRoadmapResponseDto response =
                    roadmapService.getGroupedMultiProductRoadmap(username, productCodes, filter);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request parameters: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error getting multi-product roadmap", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/by-owner")
    @Operation(
            summary = "Get roadmap by owner",
            description = "Retrieve roadmap filtered by feature owner/assignee",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ByOwnerRoadmapResponseDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid or missing owner parameter"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
            })
    public ResponseEntity<ByOwnerRoadmapResponseDto> getRoadmapByOwner(
            @Parameter(description = "Feature assignee username", required = true) @RequestParam @NotEmpty String owner,
            @Parameter(description = "Filter by product code") @RequestParam(required = false) String productCode,
            @Parameter(description = "Start date filter in format YYYY-MM-DD") @RequestParam(required = false)
                    String startDate,
            @Parameter(description = "End date filter in format YYYY-MM-DD") @RequestParam(required = false)
                    String endDate,
            @Parameter(description = "Include completed releases") @RequestParam(defaultValue = "true")
                    boolean includeCompleted,
            @Parameter(description = "Group results by field (PRODUCT, STATUS, ASSIGNEE)")
                    @RequestParam(required = false)
                    String groupBy) {

        log.info(
                "Getting roadmap by owner: {} - productCode: {}, startDate: {}, endDate: {}, includeCompleted: {}, groupBy: {}",
                owner,
                productCode,
                startDate,
                endDate,
                includeCompleted,
                groupBy);

        try {
            String username = SecurityUtils.getCurrentUsername();
            RoadmapFilterDto filter =
                    createFilter(productCode, null, startDate, endDate, includeCompleted, groupBy, owner);
            ByOwnerRoadmapResponseDto response = roadmapService.getStructuredRoadmapByOwner(username, owner, filter);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request parameters: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error getting roadmap by owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/export")
    @Operation(
            summary = "Export roadmap",
            description = "Export roadmap in CSV or PDF format",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful file download",
                        content = @Content(mediaType = "application/octet-stream")),
                @ApiResponse(responseCode = "400", description = "Invalid format or parameters"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "500", description = "Export generation failed")
            })
    public ResponseEntity<byte[]> exportRoadmap(
            @Parameter(description = "Export format (CSV or PDF)", required = true)
                    @RequestParam
                    @Pattern(regexp = "^(CSV|PDF)$", message = "Format must be CSV or PDF")
                    String format,
            @Parameter(description = "Array of product codes") @RequestParam(required = false)
                    List<String> productCodes,
            @Parameter(description = "Start date filter in format YYYY-MM-DD") @RequestParam(required = false)
                    String startDate,
            @Parameter(description = "End date filter in format YYYY-MM-DD") @RequestParam(required = false)
                    String endDate,
            @Parameter(description = "Include completed releases") @RequestParam(defaultValue = "true")
                    boolean includeCompleted,
            @Parameter(description = "Group results by field (PRODUCT, STATUS, ASSIGNEE)")
                    @RequestParam(required = false)
                    String groupBy,
            @Parameter(description = "Filter by feature assignee") @RequestParam(required = false) String owner) {

        log.info(
                "Exporting roadmap - format: {}, productCodes: {}, startDate: {}, endDate: {}, includeCompleted: {}, groupBy: {}, owner: {}",
                format,
                productCodes,
                startDate,
                endDate,
                includeCompleted,
                groupBy,
                owner);

        try {
            // Get roadmap data
            String username = SecurityUtils.getCurrentUsername();
            RoadmapFilterDto filter =
                    createFilter(null, productCodes, startDate, endDate, includeCompleted, groupBy, owner);

            RoadmapResponseDto roadmapData;
            if (owner != null) {
                roadmapData = roadmapService.getRoadmapByOwner(username, owner, filter);
            } else if (productCodes != null && !productCodes.isEmpty()) {
                roadmapData = roadmapService.getMultiProductRoadmap(username, productCodes, filter);
            } else {
                roadmapData = roadmapService.getRoadmap(username, filter);
            }

            // Generate export
            byte[] exportData;
            String contentType;
            if ("CSV".equalsIgnoreCase(format)) {
                exportData = reportingService.exportRoadmapToCsv(roadmapData);
                contentType = "text/csv";
            } else {
                exportData = reportingService.exportRoadmapToPdf(roadmapData);
                contentType = "application/pdf";
            }

            // Generate filename
            String filename = reportingService.generateExportFilename(format);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(exportData.length);

            return ResponseEntity.ok().headers(headers).body(exportData);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request parameters for export: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error exporting roadmap", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private RoadmapFilterDto createFilter(
            String productCode,
            List<String> productCodes,
            String startDateStr,
            String endDateStr,
            boolean includeCompleted,
            String groupBy,
            String owner) {
        LocalDate startDate = null;
        LocalDate endDate = null;

        // Parse start date
        if (startDateStr != null && !startDateStr.trim().isEmpty()) {
            try {
                startDate = LocalDate.parse(startDateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid start date format. Expected: YYYY-MM-DD");
            }
        }

        // Parse end date
        if (endDateStr != null && !endDateStr.trim().isEmpty()) {
            try {
                endDate = LocalDate.parse(endDateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid end date format. Expected: YYYY-MM-DD");
            }
        }

        // Validate date range if both dates are provided
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }

        // Parse groupBy
        GroupByOption groupByOption = null;
        if (groupBy != null && !groupBy.trim().isEmpty()) {
            try {
                groupByOption = GroupByOption.valueOf(groupBy.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid groupBy option. Valid values: PRODUCT, STATUS, ASSIGNEE");
            }
        }

        // Convert empty productCode to null
        String normalizedProductCode =
                (productCode != null && productCode.trim().isEmpty()) ? null : productCode;

        return new RoadmapFilterDto(
                normalizedProductCode, productCodes, startDate, endDate, includeCompleted, groupByOption, owner);
    }
}

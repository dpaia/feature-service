package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.RoadmapResponse;
import com.sivalabs.ft.features.domain.ReportingService;
import com.sivalabs.ft.features.domain.RoadmapService;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/roadmap")
@Tag(name = "Roadmap API", description = "APIs for product roadmap visualization and export")
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
            description = "Retrieve product roadmap with releases, features, progress metrics and health indicators",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = RoadmapResponse.class))),
                @ApiResponse(responseCode = "400", description = "Invalid request parameters")
            })
    public ResponseEntity<RoadmapResponse> getRoadmap(
            @Parameter(description = "List of product codes to filter by")
                    @RequestParam(value = "productCodes", required = false)
                    String[] productCodes,
            @Parameter(description = "List of release statuses to filter by")
                    @RequestParam(value = "statuses", required = false)
                    String[] statuses,
            @Parameter(description = "Filter releases from this date (inclusive)")
                    @RequestParam(value = "dateFrom", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @Parameter(description = "Filter releases to this date (inclusive)")
                    @RequestParam(value = "dateTo", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo,
            @Parameter(description = "Group releases by field (productCode, status, owner)")
                    @RequestParam(value = "groupBy", required = false)
                    String groupBy,
            @Parameter(description = "Filter releases by owner") @RequestParam(value = "owner", required = false)
                    String owner) {
        try {
            log.info(
                    "Fetching roadmap with filters - productCodes: {}, statuses: {}, dateFrom: {}, dateTo: {}, groupBy: {}, owner: {}",
                    productCodes,
                    statuses,
                    dateFrom,
                    dateTo,
                    groupBy,
                    owner);

            RoadmapResponse roadmap =
                    roadmapService.getRoadmap(productCodes, statuses, dateFrom, dateTo, groupBy, owner);

            log.info(
                    "Successfully retrieved roadmap with {} items",
                    roadmap.roadmapItems().size());
            return ResponseEntity.ok(roadmap);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request parameters: {}", e.getMessage());
            throw new BadRequestException(e.getMessage());
        } catch (Exception e) {
            log.error("Error retrieving roadmap", e);
            throw new RuntimeException("Internal server error while retrieving roadmap", e);
        }
    }

    @GetMapping("/export")
    @Operation(
            summary = "Export roadmap",
            description = "Export roadmap data in CSV or PDF format with the same filtering options",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful export",
                        content = {@Content(mediaType = "text/csv"), @Content(mediaType = "application/pdf")}),
                @ApiResponse(responseCode = "400", description = "Invalid request parameters or format")
            })
    public ResponseEntity<ByteArrayResource> exportRoadmap(
            @Parameter(description = "Export format (CSV or PDF)", required = true) @RequestParam(value = "format")
                    String format,
            @Parameter(description = "List of product codes to filter by")
                    @RequestParam(value = "productCodes", required = false)
                    String[] productCodes,
            @Parameter(description = "List of release statuses to filter by")
                    @RequestParam(value = "statuses", required = false)
                    String[] statuses,
            @Parameter(description = "Filter releases from this date (inclusive)")
                    @RequestParam(value = "dateFrom", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @Parameter(description = "Filter releases to this date (inclusive)")
                    @RequestParam(value = "dateTo", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo,
            @Parameter(description = "Group releases by field (productCode, status, owner)")
                    @RequestParam(value = "groupBy", required = false)
                    String groupBy,
            @Parameter(description = "Filter releases by owner") @RequestParam(value = "owner", required = false)
                    String owner) {
        try {
            log.info(
                    "Exporting roadmap in {} format with filters - productCodes: {}, statuses: {}, dateFrom: {}, dateTo: {}, groupBy: {}, owner: {}",
                    format,
                    productCodes,
                    statuses,
                    dateFrom,
                    dateTo,
                    groupBy,
                    owner);

            ResponseEntity<ByteArrayResource> exportResponse =
                    reportingService.exportRoadmap(format, productCodes, statuses, dateFrom, dateTo, groupBy, owner);

            log.info("Successfully exported roadmap in {} format", format);
            return exportResponse;

        } catch (IllegalArgumentException e) {
            log.warn("Invalid export request parameters: {}", e.getMessage());
            throw new BadRequestException(e.getMessage());
        } catch (IOException e) {
            log.error("Error generating export file", e);
            throw new RuntimeException("Internal server error while generating export file", e);
        } catch (Exception e) {
            log.error("Error exporting roadmap", e);
            throw new RuntimeException("Internal server error while exporting roadmap", e);
        }
    }
}

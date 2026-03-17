package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.domain.ReportingService;
import com.sivalabs.ft.features.domain.RoadmapFilter;
import com.sivalabs.ft.features.domain.RoadmapService;
import com.sivalabs.ft.features.domain.dtos.RoadmapResponse;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roadmap")
@Tag(name = "Roadmap API")
class RoadmapController {

    private final RoadmapService roadmapService;
    private final ReportingService reportingService;

    RoadmapController(RoadmapService roadmapService, ReportingService reportingService) {
        this.roadmapService = roadmapService;
        this.reportingService = reportingService;
    }

    @GetMapping("")
    @Operation(
            summary = "Get product roadmap",
            description = "Get product roadmap with releases, features, progress metrics and health indicators")
    RoadmapResponse getRoadmap(
            @RequestParam(required = false) List<String> productCodes,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String owner) {
        List<ReleaseStatus> parsedStatuses = parseStatuses(statuses);
        RoadmapFilter filter = new RoadmapFilter(productCodes, parsedStatuses, dateFrom, dateTo, groupBy, owner);
        return roadmapService.getRoadmap(filter);
    }

    @GetMapping("/export")
    @Operation(summary = "Export roadmap", description = "Export roadmap as CSV or PDF file")
    ResponseEntity<byte[]> exportRoadmap(
            @RequestParam String format,
            @RequestParam(required = false) List<String> productCodes,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String owner) {
        List<ReleaseStatus> parsedStatuses = parseStatuses(statuses);
        RoadmapFilter filter = new RoadmapFilter(productCodes, parsedStatuses, dateFrom, dateTo, groupBy, owner);
        byte[] content = reportingService.export(filter, format);
        String filename = reportingService.generateFilename(format);
        MediaType mediaType =
                "pdf".equalsIgnoreCase(format) ? MediaType.APPLICATION_PDF : MediaType.parseMediaType("text/csv");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        headers.setContentType(mediaType);
        return ResponseEntity.ok().headers(headers).body(content);
    }

    private List<ReleaseStatus> parseStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        return statuses.stream()
                .map(s -> {
                    try {
                        return ReleaseStatus.valueOf(s.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Invalid status value: " + s + ". Allowed values: "
                                + Arrays.toString(ReleaseStatus.values()));
                    }
                })
                .toList();
    }
}

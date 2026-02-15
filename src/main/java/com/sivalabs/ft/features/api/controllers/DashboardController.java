package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.domain.DashboardService;
import com.sivalabs.ft.features.domain.dtos.ReleaseDashboardDto;
import com.sivalabs.ft.features.domain.dtos.ReleaseMetricsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/releases")
@Tag(name = "Release Dashboard API")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/{code}/dashboard")
    @Operation(
            summary = "Get release dashboard",
            description =
                    "Get comprehensive release dashboard with overview, feature breakdown, progress metrics, health indicators, and timeline data",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ReleaseDashboardDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid release code"),
                @ApiResponse(responseCode = "404", description = "Release not found")
            })
    public ReleaseDashboardDto getReleaseDashboard(@PathVariable String code) {
        return dashboardService.getReleaseDashboard(code);
    }

    @GetMapping("/{code}/metrics")
    @Operation(
            summary = "Get release metrics",
            description =
                    "Get detailed release metrics including completion rate, velocity, blocked time, and workload distribution",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ReleaseMetricsDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid release code"),
                @ApiResponse(responseCode = "404", description = "Release not found")
            })
    public ReleaseMetricsDto getReleaseMetrics(@PathVariable String code) {
        return dashboardService.getReleaseMetrics(code);
    }
}

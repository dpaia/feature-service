package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.domain.PlanningAnalyticsService;
import com.sivalabs.ft.features.domain.dtos.CapacityPlanningDto;
import com.sivalabs.ft.features.domain.dtos.PlanningHealthDto;
import com.sivalabs.ft.features.domain.dtos.PlanningTrendsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planning")
@Tag(name = "Planning Analytics API")
public class PlanningController {
    private final PlanningAnalyticsService planningAnalyticsService;

    public PlanningController(PlanningAnalyticsService planningAnalyticsService) {
        this.planningAnalyticsService = planningAnalyticsService;
    }

    @GetMapping("/health")
    @Operation(
            summary = "Get planning health report",
            description =
                    "Get overall planning health including releases by status, at-risk counts, and planning accuracy metrics",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlanningHealthDto.class)))
            })
    public PlanningHealthDto getPlanningHealth() {
        return planningAnalyticsService.getPlanningHealth();
    }

    @GetMapping("/trends")
    @Operation(
            summary = "Get planning trends",
            description =
                    "Get historical trend data for releases completed, average duration, and planning accuracy over time",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlanningTrendsDto.class)))
            })
    public PlanningTrendsDto getPlanningTrends() {
        return planningAnalyticsService.getPlanningTrends();
    }

    @GetMapping("/capacity")
    @Operation(
            summary = "Get capacity planning data",
            description =
                    "Get capacity planning insights including workload by owner, commitments, and overallocation warnings",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CapacityPlanningDto.class)))
            })
    public CapacityPlanningDto getCapacityPlanning() {
        return planningAnalyticsService.getCapacityPlanning();
    }
}

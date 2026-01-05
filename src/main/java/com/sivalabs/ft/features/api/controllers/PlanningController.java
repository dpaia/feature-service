package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.domain.PlanningAnalyticsService;
import com.sivalabs.ft.features.domain.dtos.CapacityPlanningResponseDto;
import com.sivalabs.ft.features.domain.dtos.PlanningHealthResponseDto;
import com.sivalabs.ft.features.domain.dtos.PlanningTrendsResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planning")
@Tag(name = "Planning Analytics API")
class PlanningController {

    private final PlanningAnalyticsService planningAnalyticsService;

    PlanningController(PlanningAnalyticsService planningAnalyticsService) {
        this.planningAnalyticsService = planningAnalyticsService;
    }

    @GetMapping("/health")
    @Operation(
            summary = "Get planning health report",
            description =
                    "Get comprehensive planning health report including releases by status, overdue/at-risk counts, and planning accuracy",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlanningHealthResponseDto.class)))
            })
    ResponseEntity<PlanningHealthResponseDto> getPlanningHealth() {
        PlanningHealthResponseDto health = planningAnalyticsService.getPlanningHealth();
        return ResponseEntity.ok(health);
    }

    @GetMapping("/trends")
    @Operation(
            summary = "Get planning trends data",
            description =
                    "Get trend data including releases completed over time, average duration, and planning accuracy trends",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlanningTrendsResponseDto.class)))
            })
    ResponseEntity<PlanningTrendsResponseDto> getPlanningTrends() {
        PlanningTrendsResponseDto trends = planningAnalyticsService.getPlanningTrends();
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/capacity")
    @Operation(
            summary = "Get capacity planning data",
            description =
                    "Get capacity planning data including workload by owner, commitments, and overallocation warnings",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CapacityPlanningResponseDto.class)))
            })
    ResponseEntity<CapacityPlanningResponseDto> getCapacityPlanning() {
        CapacityPlanningResponseDto capacity = planningAnalyticsService.getCapacityPlanning();
        return ResponseEntity.ok(capacity);
    }
}

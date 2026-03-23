package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.CapacityPlanningResponse;
import com.sivalabs.ft.features.api.models.PlanningHealthResponse;
import com.sivalabs.ft.features.api.models.PlanningTrendsResponse;
import com.sivalabs.ft.features.domain.PlanningAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planning")
@Tag(name = "Planning Analytics API")
class PlanningController {
    private static final Logger log = LoggerFactory.getLogger(PlanningController.class);
    private final PlanningAnalyticsService planningAnalyticsService;

    PlanningController(PlanningAnalyticsService planningAnalyticsService) {
        this.planningAnalyticsService = planningAnalyticsService;
    }

    @GetMapping("/health")
    @Operation(
            summary = "Get planning health report",
            description =
                    "Get planning health report including releases by status, at-risk counts, and planning accuracy",
            responses = {
                @ApiResponse(responseCode = "200", description = "Planning health report retrieved successfully")
            })
    ResponseEntity<PlanningHealthResponse> getPlanningHealth() {
        PlanningHealthResponse health = planningAnalyticsService.getPlanningHealth();
        return ResponseEntity.ok(health);
    }

    @GetMapping("/trends")
    @Operation(
            summary = "Get planning trends",
            description =
                    "Get trend data including releases completed over time, average duration, and planning accuracy trends",
            responses = {@ApiResponse(responseCode = "200", description = "Planning trends retrieved successfully")})
    ResponseEntity<PlanningTrendsResponse> getPlanningTrends() {
        PlanningTrendsResponse trends = planningAnalyticsService.getPlanningTrends();
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/capacity")
    @Operation(
            summary = "Get capacity planning data",
            description =
                    "Get capacity planning data including workload by owner, commitments, and overallocation warnings",
            responses = {
                @ApiResponse(responseCode = "200", description = "Capacity planning data retrieved successfully")
            })
    ResponseEntity<CapacityPlanningResponse> getCapacityPlanning() {
        CapacityPlanningResponse capacity = planningAnalyticsService.getCapacityPlanning();
        return ResponseEntity.ok(capacity);
    }
}

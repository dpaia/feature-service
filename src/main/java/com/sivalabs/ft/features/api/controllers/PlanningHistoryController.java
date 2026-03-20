package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.PagedResult;
import com.sivalabs.ft.features.domain.FeatureService;
import com.sivalabs.ft.features.domain.PlanningHistoryService;
import com.sivalabs.ft.features.domain.ReleaseService;
import com.sivalabs.ft.features.domain.dtos.PlanningHistoryDto;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.models.ChangeType;
import com.sivalabs.ft.features.domain.models.EntityType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Planning History API")
class PlanningHistoryController {
    private final PlanningHistoryService planningHistoryService;
    private final FeatureService featureService;
    private final ReleaseService releaseService;

    PlanningHistoryController(
            PlanningHistoryService planningHistoryService,
            FeatureService featureService,
            ReleaseService releaseService) {
        this.planningHistoryService = planningHistoryService;
        this.featureService = featureService;
        this.releaseService = releaseService;
    }

    @GetMapping("/api/planning-history")
    @Operation(summary = "Query planning history with filters, pagination and sorting")
    PagedResult<PlanningHistoryDto> getPlanningHistory(
            @RequestParam(required = false) EntityType entityType,
            @RequestParam(required = false) String entityCode,
            @RequestParam(required = false) String changedBy,
            @RequestParam(required = false) ChangeType changeType,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return PagedResult.from(planningHistoryService.findHistory(
                entityType, entityCode, changedBy, changeType, dateFrom, dateTo, page, size, sort));
    }

    @GetMapping("/api/releases/{code}/history")
    @Operation(summary = "Get planning history for a specific release")
    PagedResult<PlanningHistoryDto> getReleaseHistory(
            @PathVariable String code,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        if (!releaseService.isReleaseExists(code)) {
            throw new ResourceNotFoundException("Release with code " + code + " not found");
        }
        return PagedResult.from(
                planningHistoryService.findHistory(EntityType.RELEASE, code, null, null, null, null, page, size, sort));
    }

    @GetMapping("/api/features/{code}/history")
    @Operation(summary = "Get planning history for a specific feature")
    PagedResult<PlanningHistoryDto> getFeatureHistory(
            @PathVariable String code,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        if (!featureService.isFeatureExists(code)) {
            throw new ResourceNotFoundException("Feature with code " + code + " not found");
        }
        return PagedResult.from(
                planningHistoryService.findHistory(EntityType.FEATURE, code, null, null, null, null, page, size, sort));
    }
}

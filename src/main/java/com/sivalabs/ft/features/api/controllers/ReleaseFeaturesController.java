package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.AssignFeatureToReleasePayload;
import com.sivalabs.ft.features.api.models.MoveFeaturePayload;
import com.sivalabs.ft.features.api.models.RemoveFeatureFromReleasePayload;
import com.sivalabs.ft.features.api.models.UpdateFeaturePlanningPayload;
import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.Commands.AssignFeatureToReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.MoveFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.RemoveFeatureFromReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateFeaturePlanningCommand;
import com.sivalabs.ft.features.domain.FeatureService;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/releases")
@Tag(name = "Release Features API")
class ReleaseFeaturesController {
    private static final Logger log = LoggerFactory.getLogger(ReleaseFeaturesController.class);
    private final FeatureService featureService;

    ReleaseFeaturesController(FeatureService featureService) {
        this.featureService = featureService;
    }

    @GetMapping("/{releaseCode}/features")
    @Operation(
            summary = "List features with planning details for a release",
            description =
                    "List features with planning details, supports filtering by planningStatus, owner, overdue, blocked",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array = @ArraySchema(schema = @Schema(implementation = FeatureDto.class))))
            })
    List<FeatureDto> getReleaseFeatures(
            @PathVariable String releaseCode,
            @RequestParam(required = false) FeaturePlanningStatus planningStatus,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false, defaultValue = "false") boolean overdue,
            @RequestParam(required = false, defaultValue = "false") boolean blocked) {
        String username = SecurityUtils.getCurrentUsername();
        return featureService.findReleaseFeaturesWithFilters(
                username, releaseCode, planningStatus, owner, overdue, blocked);
    }

    @PostMapping("/{releaseCode}/features")
    @Operation(
            summary = "Assign a feature to a release with planning details",
            description = "Assign a feature to a release with planning details",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "404", description = "Release or feature not found"),
            })
    ResponseEntity<Void> assignFeatureToRelease(
            @PathVariable String releaseCode, @RequestBody @Valid AssignFeatureToReleasePayload payload) {
        String username = SecurityUtils.getCurrentUsername();
        var cmd = new AssignFeatureToReleaseCommand(
                releaseCode,
                payload.featureCode(),
                payload.plannedCompletionDate(),
                payload.featureOwner(),
                payload.notes(),
                username);
        featureService.assignFeatureToRelease(cmd);
        log.info("User {} assigned feature {} to release {}", username, payload.featureCode(), releaseCode);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{releaseCode}/features/{featureCode}/planning")
    @Operation(
            summary = "Update feature planning details",
            description = "Update feature planning details (dates, planningStatus, owner, blockageReason)",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request or invalid status transition"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "404", description = "Release or feature not found"),
            })
    ResponseEntity<Void> updateFeaturePlanning(
            @PathVariable String releaseCode,
            @PathVariable String featureCode,
            @RequestBody UpdateFeaturePlanningPayload payload) {
        String username = SecurityUtils.getCurrentUsername();
        var cmd = new UpdateFeaturePlanningCommand(
                releaseCode,
                featureCode,
                payload.plannedCompletionDate(),
                payload.planningStatus(),
                payload.featureOwner(),
                payload.blockageReason(),
                payload.notes(),
                username);
        featureService.updateFeaturePlanning(cmd);
        log.info("User {} updated planning for feature {} in release {}", username, featureCode, releaseCode);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{targetReleaseCode}/features/{featureCode}/move")
    @Operation(
            summary = "Move a feature to a different release",
            description = "Move a feature to a different release; rationale is recorded in the feature's notes",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "404", description = "Feature or target release not found"),
            })
    ResponseEntity<Void> moveFeature(
            @PathVariable String targetReleaseCode,
            @PathVariable String featureCode,
            @RequestBody MoveFeaturePayload payload) {
        String username = SecurityUtils.getCurrentUsername();
        var cmd = new MoveFeatureCommand(targetReleaseCode, featureCode, payload.rationale(), username);
        featureService.moveFeature(cmd);
        log.info("User {} moved feature {} to release {}", username, featureCode, targetReleaseCode);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{releaseCode}/features/{featureCode}")
    @Operation(
            summary = "Remove a feature from a release",
            description = "Remove a feature from a release; rationale is recorded in the feature's notes",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "404", description = "Feature not found"),
            })
    ResponseEntity<Void> removeFeatureFromRelease(
            @PathVariable String releaseCode,
            @PathVariable String featureCode,
            @RequestBody(required = false) RemoveFeatureFromReleasePayload payload) {
        String username = SecurityUtils.getCurrentUsername();
        String rationale = payload != null ? payload.rationale() : null;
        var cmd = new RemoveFeatureFromReleaseCommand(releaseCode, featureCode, rationale, username);
        featureService.removeFeatureFromRelease(cmd);
        log.info("User {} removed feature {} from release {}", username, featureCode, releaseCode);
        return ResponseEntity.ok().build();
    }
}

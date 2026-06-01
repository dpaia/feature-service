package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.CreateReactionPayload;
import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.FeatureReactionService;
import com.sivalabs.ft.features.domain.FeatureReactionService.AddOrUpdateReactionCommand;
import com.sivalabs.ft.features.domain.FeatureReactionService.RemoveReactionCommand;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.dtos.FeatureReactionDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/feature-reactions")
@Tag(name = "Feature Reactions API")
class FeatureReactionController {
    private static final Logger log = LoggerFactory.getLogger(FeatureReactionController.class);
    private final FeatureReactionService featureReactionService;

    FeatureReactionController(FeatureReactionService featureReactionService) {
        this.featureReactionService = featureReactionService;
    }

    @PostMapping("")
    @Operation(
            summary = "Add or update a reaction to a feature",
            description = "Add a new reaction or update an existing reaction to a feature",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Successful response",
                        headers =
                                @Header(
                                        name = "Location",
                                        required = true,
                                        description = "URI of the created reaction")),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
                @ApiResponse(responseCode = "404", description = "Feature not found")
            })
    ResponseEntity<Void> addOrUpdateReaction(@RequestBody @Valid CreateReactionPayload payload) {
        var username = SecurityUtils.getCurrentUsername();
        var cmd = new AddOrUpdateReactionCommand(payload.featureCode(), username, payload.reactionType());
        try {
            Long id = featureReactionService.addOrUpdateReaction(cmd);
            log.info("Added/updated reaction with id {} for feature {}", id, payload.featureCode());
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(id)
                    .toUri();
            return ResponseEntity.created(location).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{featureCode}")
    @Operation(
            summary = "Remove a reaction from a feature",
            description = "Remove the current user's reaction from a feature",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
                @ApiResponse(responseCode = "404", description = "Feature not found")
            })
    ResponseEntity<Void> removeReaction(@PathVariable String featureCode) {
        var username = SecurityUtils.getCurrentUsername();
        var cmd = new RemoveReactionCommand(featureCode, username);
        try {
            featureReactionService.removeReaction(cmd);
            log.info("Removed reaction for feature {} by user {}", featureCode, username);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{featureCode}")
    @Operation(
            summary = "Get reactions for a feature",
            description = "Get all reactions for a feature",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array =
                                                @ArraySchema(
                                                        schema = @Schema(implementation = FeatureReactionDto.class))))
            })
    List<FeatureReactionDto> getReactionsByFeature(@PathVariable String featureCode) {
        return featureReactionService.getReactionsByFeature(featureCode);
    }

    @GetMapping("/{featureCode}/user")
    @Operation(
            summary = "Get user's reaction for a feature",
            description = "Get the current user's reaction for a feature",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = FeatureReactionDto.class))),
                @ApiResponse(responseCode = "404", description = "Reaction not found")
            })
    ResponseEntity<FeatureReactionDto> getUserReactionForFeature(@PathVariable String featureCode) {
        var username = SecurityUtils.getCurrentUsername();
        Optional<FeatureReactionDto> reactionOpt =
                featureReactionService.getUserReactionForFeature(featureCode, username);
        return reactionOpt
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/most-liked")
    @Operation(
            summary = "Get most liked features",
            description = "Get a list of features sorted by like score (likes - dislikes)",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array = @ArraySchema(schema = @Schema(implementation = FeatureDto.class))))
            })
    List<FeatureDto> getMostLikedFeatures(@RequestParam(defaultValue = "10") int limit) {
        return featureReactionService.getMostLikedFeatures(limit);
    }
}

package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.FavoriteFeatureService;
import com.sivalabs.ft.features.domain.FeatureUsageService;
import com.sivalabs.ft.features.domain.models.ActionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/features/{featureCode}/favorites")
@Tag(name = "Favorite Features API")
class FavoriteFeatureController {

    private final FavoriteFeatureService favoriteFeatureService;
    private final FeatureUsageService featureUsageService;

    FavoriteFeatureController(FavoriteFeatureService favoriteFeatureService, FeatureUsageService featureUsageService) {
        this.favoriteFeatureService = favoriteFeatureService;
        this.featureUsageService = featureUsageService;
    }

    @PostMapping
    @Operation(
            summary = "Add a feature to favorites",
            description = "Add a feature to the user's favorites list",
            responses = {
                @ApiResponse(responseCode = "201", description = "Feature added to favorites successfully"),
                @ApiResponse(responseCode = "404", description = "Feature not found"),
                @ApiResponse(responseCode = "400", description = "Feature already favorited")
            })
    ResponseEntity<Void> addFavoriteFeature(@PathVariable String featureCode) {
        var username = SecurityUtils.getCurrentUsername();
        favoriteFeatureService.addFavoriteFeature(username, featureCode);

        if (username != null) {
            featureUsageService.logUsage(username, featureCode, null, ActionType.FAVORITE_ADDED);
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping
    @Operation(
            summary = "Remove a feature from favorites",
            description = "Remove a feature from the user's favorites list",
            responses = {
                @ApiResponse(responseCode = "204", description = "Feature removed from favorites successfully"),
                @ApiResponse(responseCode = "404", description = "Feature not found")
            })
    ResponseEntity<Void> removeFavoriteFeature(@PathVariable String featureCode) {
        var username = SecurityUtils.getCurrentUsername();
        favoriteFeatureService.removeFavoriteFeature(username, featureCode);

        if (username != null) {
            featureUsageService.logUsage(username, featureCode, null, ActionType.FAVORITE_REMOVED);
        }

        return ResponseEntity.noContent().build();
    }
}

package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.domain.AdoptionRateService;
import com.sivalabs.ft.features.domain.ReleaseService;
import com.sivalabs.ft.features.domain.dtos.AdoptionMetricsDto;
import com.sivalabs.ft.features.domain.dtos.AdoptionRateDto;
import com.sivalabs.ft.features.domain.dtos.ReleaseStatsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for feature adoption rate analytics.
 * Provides endpoints for tracking adoption metrics and release statistics.
 */
@RestController
@RequestMapping("/api/usage")
@Tag(name = "Adoption Rate API", description = "Analytics for feature adoption rates and release statistics")
public class AdoptionRateController {
    private static final Logger log = LoggerFactory.getLogger(AdoptionRateController.class);

    private final AdoptionRateService adoptionRateService;
    private final ReleaseService releaseService;

    public AdoptionRateController(AdoptionRateService adoptionRateService, ReleaseService releaseService) {
        this.adoptionRateService = adoptionRateService;
        this.releaseService = releaseService;
    }

    @GetMapping("/adoption-rate/{featureCode}")
    @Operation(
            summary = "Get Feature Adoption Rate",
            description = "Retrieve adoption rate metrics for a feature post-release. "
                    + "Includes metrics for 7, 30, and 90 day windows with growth rates and overall adoption score.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Adoption rate retrieved successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AdoptionRateDto.class))),
                @ApiResponse(responseCode = "400", description = "Feature has no release date"),
                @ApiResponse(responseCode = "404", description = "Feature not found"),
                @ApiResponse(responseCode = "401", description = "Unauthorized - authentication required")
            })
    public ResponseEntity<AdoptionRateDto> getAdoptionRate(
            @Parameter(description = "Feature code to analyze", required = true) @PathVariable String featureCode) {
        try {
            log.debug("Getting adoption rate for feature: {}", featureCode);
            AdoptionRateDto adoptionRate = adoptionRateService.calculateAdoptionRate(featureCode);
            return ResponseEntity.ok(adoptionRate);
        } catch (Exception e) {
            log.error("Error getting adoption rate for feature {}: {}", featureCode, e.getMessage());
            throw e;
        }
    }

    @GetMapping("/adoption-rate/compare")
    @Operation(
            summary = "Compare Feature Adoption Rates",
            description = "Compare adoption metrics between multiple features for a specific time window. "
                    + "Features are ranked by adoption score.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Comparison completed successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AdoptionMetricsDto.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid parameters (missing feature codes, invalid window)"),
                @ApiResponse(responseCode = "401", description = "Unauthorized - authentication required")
            })
    public ResponseEntity<List<AdoptionMetricsDto>> compareAdoptionRates(
            @Parameter(description = "Comma-separated list of feature codes to compare", required = true) @RequestParam
                    String featureCodes,
            @Parameter(description = "Time window in days (default: 30)", example = "30")
                    @RequestParam(required = false)
                    Integer windowDays) {
        try {
            // Parse feature codes
            List<String> featureCodeList = Arrays.stream(featureCodes.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            if (featureCodeList.isEmpty()) {
                log.warn("No feature codes provided for comparison");
                return ResponseEntity.badRequest().build();
            }

            if (featureCodeList.size() < 2) {
                log.warn("At least 2 feature codes required for comparison");
                return ResponseEntity.badRequest().build();
            }

            if (windowDays != null && windowDays <= 0) {
                log.warn("Invalid window days: {}", windowDays);
                return ResponseEntity.badRequest().build();
            }

            log.debug(
                    "Comparing adoption rates for {} features with {} day window", featureCodeList.size(), windowDays);

            List<AdoptionMetricsDto> comparison = adoptionRateService.compareFeatures(featureCodeList, windowDays);

            return ResponseEntity.ok(comparison);
        } catch (Exception e) {
            log.error("Error comparing adoption rates: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/release/{releaseCode}/stats")
    @Operation(
            summary = "Get Release Statistics",
            description = "Retrieve aggregated usage statistics for all features in a release. "
                    + "Includes adoption metrics, total usage, and feature rankings.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Release statistics retrieved successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ReleaseStatsDto.class))),
                @ApiResponse(responseCode = "404", description = "Release not found"),
                @ApiResponse(responseCode = "401", description = "Unauthorized - authentication required")
            })
    public ResponseEntity<ReleaseStatsDto> getReleaseStats(
            @Parameter(description = "Release code to analyze", required = true) @PathVariable String releaseCode) {
        try {
            log.debug("Getting release statistics for: {}", releaseCode);
            ReleaseStatsDto stats = releaseService.getReleaseStats(releaseCode);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting release stats for {}: {}", releaseCode, e.getMessage());
            throw e;
        }
    }
}

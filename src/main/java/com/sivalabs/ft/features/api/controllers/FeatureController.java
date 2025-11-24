package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import com.sivalabs.ft.features.api.models.UpdateFeaturePayload;
import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.*;
import com.sivalabs.ft.features.domain.Commands.CreateFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.DeleteFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateFeatureCommand;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.models.ActionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/features")
@Tag(name = "Features API")
class FeatureController {
    private static final Logger log = LoggerFactory.getLogger(FeatureController.class);
    private final FeatureService featureService;
    private final FavoriteFeatureService favoriteFeatureService;
    private final FeatureUsageService featureUsageService;

    FeatureController(
            FeatureService featureService,
            FavoriteFeatureService favoriteFeatureService,
            FeatureUsageService featureUsageService) {
        this.featureService = featureService;
        this.favoriteFeatureService = favoriteFeatureService;
        this.featureUsageService = featureUsageService;
    }

    @GetMapping("")
    @Operation(
            summary = "Find features by product or release",
            description = "Find features by product or release",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array = @ArraySchema(schema = @Schema(implementation = FeatureDto.class))))
            })
    List<FeatureDto> getFeatures(
            @RequestParam(value = "productCode", required = false) String productCode,
            @RequestParam(value = "releaseCode", required = false) String releaseCode,
            HttpServletRequest request) {
        // Only one of productCode or releaseCode should be provided
        if ((StringUtils.isBlank(productCode) && StringUtils.isBlank(releaseCode))
                || (StringUtils.isNotBlank(productCode) && StringUtils.isNotBlank(releaseCode))) {
            // TODO: Return 400 Bad Request
            return List.of();
        }
        String username = SecurityUtils.getCurrentUsername();
        String userId = SecurityUtils.getCurrentUserId();
        List<FeatureDto> featureDtos;
        if (StringUtils.isNotBlank(productCode)) {
            featureDtos = featureService.findFeaturesByProduct(username, productCode);
            // Create context for anonymous users (GDPR compliance)
            Map<String, Object> context = null;
            if (username == null) {
                context = SecurityUtils.createAnonymousContext(request);
            }
            featureUsageService.logUsage(
                    userId,
                    null, // featureCode
                    productCode,
                    null, // releaseCode
                    ActionType.FEATURES_LISTED,
                    context,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"));
        } else {
            featureDtos = featureService.findFeaturesByRelease(username, releaseCode);
            // Create context for anonymous users (GDPR compliance)
            Map<String, Object> context = null;
            if (username == null) {
                context = SecurityUtils.createAnonymousContext(request);
            }
            featureUsageService.logUsage(
                    userId,
                    null, // featureCode
                    null, // productCode
                    releaseCode,
                    ActionType.FEATURES_LISTED,
                    context,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"));
        }

        if (username != null && !featureDtos.isEmpty()) {
            Set<String> featureCodes =
                    featureDtos.stream().map(FeatureDto::code).collect(Collectors.toSet());
            Map<String, Boolean> favoriteFeatures = favoriteFeatureService.getFavoriteFeatures(username, featureCodes);
            featureDtos = featureDtos.stream()
                    .map(featureDto -> featureDto.makeFavorite(favoriteFeatures.get(featureDto.code())))
                    .toList();
        }
        return featureDtos;
    }

    @GetMapping("/{code}")
    @Operation(
            summary = "Find feature by code",
            description = "Find feature by code",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = FeatureDto.class))),
                @ApiResponse(responseCode = "404", description = "Feature not found")
            })
    ResponseEntity<FeatureDto> getFeature(@PathVariable String code, HttpServletRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        String userId = SecurityUtils.getCurrentUserId();
        Optional<FeatureDto> featureDtoOptional = featureService.findFeatureByCode(username, code);
        if (featureDtoOptional.isPresent()) {
            FeatureDto featureDto = featureDtoOptional.get();
            if (username != null) {
                Set<String> featureCodes = Set.of(featureDto.code());
                Map<String, Boolean> favoriteFeatures =
                        favoriteFeatureService.getFavoriteFeatures(username, featureCodes);
                featureDto = featureDto.makeFavorite(favoriteFeatures.get(featureDto.code()));
                featureDtoOptional = Optional.of(featureDto);
            }
        }

        if (featureDtoOptional.isPresent()) {
            FeatureDto dto = featureDtoOptional.get();
            // Create context for anonymous users (GDPR compliance)
            Map<String, Object> context = null;
            if (username == null) {
                context = SecurityUtils.createAnonymousContext(request);
            }
            featureUsageService.logUsage(
                    userId,
                    dto.code(),
                    null, // productCode
                    null, // releaseCode
                    ActionType.FEATURE_VIEWED,
                    context,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"));
        }

        return featureDtoOptional
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("")
    @Operation(
            summary = "Create a new feature",
            description = "Create a new feature",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Successful response",
                        headers =
                                @Header(
                                        name = "Location",
                                        required = true,
                                        description = "URI of the created feature")),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
            })
    ResponseEntity<Void> createFeature(@RequestBody @Valid CreateFeaturePayload payload) {
        String username = SecurityUtils.getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var cmd = new CreateFeatureCommand(
                payload.productCode(),
                payload.releaseCode(),
                payload.title(),
                payload.description(),
                payload.assignedTo(),
                username);
        String code = featureService.createFeature(cmd);
        log.info("Created feature with code {}", code);

        featureUsageService.logUsage(username, code, payload.productCode(), ActionType.FEATURE_CREATED);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{code}")
                .buildAndExpand(code)
                .toUri();
        return ResponseEntity.created(location).build();
    }

    @PutMapping("/{code}")
    @Operation(
            summary = "Update an existing feature",
            description = "Update an existing feature",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
            })
    void updateFeature(@PathVariable String code, @RequestBody UpdateFeaturePayload payload) {
        var username = SecurityUtils.getCurrentUsername();
        var cmd = new UpdateFeatureCommand(
                code,
                payload.title(),
                payload.description(),
                payload.status(),
                payload.releaseCode(),
                payload.assignedTo(),
                username);
        featureService.updateFeature(cmd);

        if (username != null) {
            featureUsageService.logUsage(username, code, null, ActionType.FEATURE_UPDATED);
        }
    }

    @DeleteMapping("/{code}")
    @Operation(
            summary = "Delete an existing feature",
            description = "Delete an existing feature",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
            })
    ResponseEntity<Void> deleteFeature(@PathVariable String code) {
        var username = SecurityUtils.getCurrentUsername();
        if (!featureService.isFeatureExists(code)) {
            return ResponseEntity.notFound().build();
        }

        var cmd = new DeleteFeatureCommand(code, username);
        featureService.deleteFeature(cmd);

        if (username != null) {
            featureUsageService.logUsage(username, code, null, ActionType.FEATURE_DELETED);
        }

        return ResponseEntity.ok().build();
    }
}

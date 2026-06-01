package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.CreateFeatureDependencyPayload;
import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import com.sivalabs.ft.features.api.models.UpdateFeatureDependencyPayload;
import com.sivalabs.ft.features.api.models.UpdateFeaturePayload;
import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.*;
import com.sivalabs.ft.features.domain.Commands.CreateFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.CreateFeatureDependencyCommand;
import com.sivalabs.ft.features.domain.Commands.DeleteFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.DeleteFeatureDependencyCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateFeatureDependencyCommand;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final FeatureDependencyService featureDependencyService;

    FeatureController(
            FeatureService featureService,
            FavoriteFeatureService favoriteFeatureService,
            FeatureDependencyService featureDependencyService) {
        this.featureService = featureService;
        this.favoriteFeatureService = favoriteFeatureService;
        this.featureDependencyService = featureDependencyService;
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
            @RequestParam(value = "releaseCode", required = false) String releaseCode) {
        // Only one of productCode or releaseCode should be provided
        if ((StringUtils.isBlank(productCode) && StringUtils.isBlank(releaseCode))
                || (StringUtils.isNotBlank(productCode) && StringUtils.isNotBlank(releaseCode))) {
            // TODO: Return 400 Bad Request
            return List.of();
        }
        String username = SecurityUtils.getCurrentUsername();
        List<FeatureDto> featureDtos;
        if (StringUtils.isNotBlank(productCode)) {
            featureDtos = featureService.findFeaturesByProduct(username, productCode);
        } else {
            featureDtos = featureService.findFeaturesByRelease(username, releaseCode);
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
    ResponseEntity<FeatureDto> getFeature(@PathVariable String code) {
        String username = SecurityUtils.getCurrentUsername();
        Optional<FeatureDto> featureDtoOptional = featureService.findFeatureByCode(username, code);
        if (username != null && featureDtoOptional.isPresent()) {
            FeatureDto featureDto = featureDtoOptional.get();
            Set<String> featureCodes = Set.of(featureDto.code());
            Map<String, Boolean> favoriteFeatures = favoriteFeatureService.getFavoriteFeatures(username, featureCodes);
            featureDto = featureDto.makeFavorite(favoriteFeatures.get(featureDto.code()));
            featureDtoOptional = Optional.of(featureDto);
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
        var username = SecurityUtils.getCurrentUsername();
        var cmd = new CreateFeatureCommand(
                payload.productCode(),
                payload.releaseCode(),
                payload.title(),
                payload.description(),
                payload.assignedTo(),
                username);
        String code = featureService.createFeature(cmd);
        log.info("Created feature with code {}", code);
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
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{featureCode}/dependencies")
    @Operation(
            summary = "Create a new dependency for a feature",
            description = "Create a new dependency for a feature",
            responses = {
                @ApiResponse(responseCode = "201", description = "Dependency created successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
                @ApiResponse(responseCode = "404", description = "Feature not found"),
            })
    ResponseEntity<Void> createFeatureDependency(
            @PathVariable String featureCode, @RequestBody @Valid CreateFeatureDependencyPayload payload) {
        var username = SecurityUtils.getCurrentUsername();
        if (!featureService.isFeatureExists(featureCode)
                || !featureService.isFeatureExists(payload.dependsOnFeatureCode())) {
            return ResponseEntity.notFound().build();
        }
        var cmd = new CreateFeatureDependencyCommand(
                featureCode, payload.dependsOnFeatureCode(), payload.dependencyType(), payload.notes(), username);
        featureDependencyService.createFeatureDependency(cmd);
        log.info("Created dependency for feature {} on {}", featureCode, payload.dependsOnFeatureCode());
        return ResponseEntity.created(null).build(); // Could return location if needed
    }

    @PutMapping("/{featureCode}/dependencies/{dependsOnFeatureCode}")
    @Operation(
            summary = "Update an existing dependency",
            description = "Update an existing dependency",
            responses = {
                @ApiResponse(responseCode = "200", description = "Dependency updated successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
                @ApiResponse(responseCode = "404", description = "Dependency not found"),
            })
    ResponseEntity<Void> updateFeatureDependency(
            @PathVariable String featureCode,
            @PathVariable String dependsOnFeatureCode,
            @RequestBody @Valid UpdateFeatureDependencyPayload payload) {
        var username = SecurityUtils.getCurrentUsername();
        // Check if dependency exists
        if (!featureService.isFeatureExists(featureCode) || !featureService.isFeatureExists(dependsOnFeatureCode)) {
            return ResponseEntity.notFound().build();
        }
        var cmd = new UpdateFeatureDependencyCommand(
                featureCode, dependsOnFeatureCode, payload.dependencyType(), payload.notes(), username);
        try {
            featureDependencyService.updateFeatureDependency(cmd);
            log.info("Updated dependency for feature {} on {}", featureCode, dependsOnFeatureCode);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{featureCode}/dependencies/{dependsOnFeatureCode}")
    @Operation(
            summary = "Delete an existing dependency",
            description = "Delete an existing dependency",
            responses = {
                @ApiResponse(responseCode = "200", description = "Dependency deleted successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
                @ApiResponse(responseCode = "404", description = "Dependency not found"),
            })
    ResponseEntity<Void> deleteFeatureDependency(
            @PathVariable String featureCode, @PathVariable String dependsOnFeatureCode) {
        var username = SecurityUtils.getCurrentUsername();
        if (!featureService.isFeatureExists(featureCode) || !featureService.isFeatureExists(dependsOnFeatureCode)) {
            return ResponseEntity.notFound().build();
        }
        var cmd = new DeleteFeatureDependencyCommand(featureCode, dependsOnFeatureCode, username);
        try {
            featureDependencyService.deleteFeatureDependency(cmd);
            log.info("Deleted dependency for feature {} on {}", featureCode, dependsOnFeatureCode);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}

package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.*;
import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.*;
import com.sivalabs.ft.features.domain.Commands.CreateFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.DeleteFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.RemoveCategoryFromFeaturesCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateFeatureCommand;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
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

    FeatureController(FeatureService featureService, FavoriteFeatureService favoriteFeatureService) {
        this.featureService = featureService;
        this.favoriteFeatureService = favoriteFeatureService;
    }

    @GetMapping("")
    @Operation(
            summary = "Find features by product, release, or tags",
            description =
                    "Find features by product, release, or tags. If tagIds is provided, returns all features matching any of the given tags. Otherwise, returns features by product or release.",
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
            @RequestParam(value = "tagIds", required = false) List<Long> tagIds) {
        String username = SecurityUtils.getCurrentUsername();

        // Only one of productCode or releaseCode or tagIds should be provided
        if ((StringUtils.isBlank(productCode)
                        && StringUtils.isBlank(releaseCode)
                        && (tagIds == null || tagIds.isEmpty()))
                || (StringUtils.isNotBlank(productCode)
                        && StringUtils.isNotBlank(releaseCode)
                        && tagIds != null
                        && !tagIds.isEmpty())) {
            // TODO: Return 400 Bad Request
            return List.of();
        }
        List<FeatureDto> featureDtos = List.of();
        if (StringUtils.isNotBlank(productCode)) {
            featureDtos = featureService.findFeaturesByProduct(username, productCode);
        }
        if (StringUtils.isNotBlank(releaseCode)) {
            featureDtos = featureService.findFeaturesByRelease(username, releaseCode);
        }
        if (tagIds != null && !tagIds.isEmpty()) {
            featureDtos = featureService.findFeaturesByTags(username, tagIds);
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

    @PostMapping("/tags")
    @Operation(
            summary = "Assign tags to multiple features",
            description = "Assign multiple tags to multiple features",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
                @ApiResponse(responseCode = "404", description = "Feature or Tag not found")
            })
    ResponseEntity<Void> assignTagsToFeatures(@RequestBody @Valid AssignTagsToFeaturesPayload payload) {
        var username = SecurityUtils.getCurrentUsername();
        var cmd = new Commands.AssignTagsToFeaturesCommand(payload.featureCodes(), payload.tagIds(), username);
        try {
            featureService.assignTagsToFeatures(cmd);
            return ResponseEntity.ok().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/tags")
    @Operation(
            summary = "Remove tags from multiple features",
            description = "Remove given tags from multiple features",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
                @ApiResponse(responseCode = "404", description = "Feature or Tag not found")
            })
    ResponseEntity<Void> removeTagsToFeatures(@RequestBody @Valid RemoveTagsFromFeaturesPayload payload) {
        var username = SecurityUtils.getCurrentUsername();
        var cmd = new Commands.RemoveTagsFromFeaturesCommand(payload.featureCodes(), payload.tagIds(), username);
        try {
            featureService.removeTagsFromFeatures(cmd);
            return ResponseEntity.ok().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/category")
    @Operation(
            summary = "Assign category to multiple features",
            description = "Assign a category to multiple features",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
                @ApiResponse(responseCode = "404", description = "Category or Feature not found")
            })
    ResponseEntity<Void> assignCategoryToFeatures(@RequestBody @Valid AssignCategoryToFeaturesPayload payload) {
        var username = SecurityUtils.getCurrentUsername();
        var cmd = new Commands.AssignCategoryToFeaturesCommand(payload.featureCodes(), payload.categoryId(), username);
        try {
            featureService.assignCategoryToFeatures(cmd);
            return ResponseEntity.ok().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/category")
    @Operation(
            summary = "Remove category from multiple features",
            description = "Remove category from multiple features",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
                @ApiResponse(responseCode = "404", description = "Feature not found")
            })
    ResponseEntity<Void> removeCategoryFromFeatures(@RequestBody @Valid RemoveCategoryFromFeaturesPayload payload) {
        var username = SecurityUtils.getCurrentUsername();
        var cmd = new RemoveCategoryFromFeaturesCommand(payload.featureCodes(), username);
        try {
            featureService.removeCategoryFromFeatures(cmd);
            return ResponseEntity.ok().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

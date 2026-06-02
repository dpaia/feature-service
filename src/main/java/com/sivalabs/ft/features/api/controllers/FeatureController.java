package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.dtos.FeatureDto;
import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.*;
import com.sivalabs.ft.features.mappers.FeatureMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
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
    private final FeatureMapper featureMapper;

    FeatureController(FeatureService featureService, FeatureMapper featureMapper) {
        this.featureService = featureService;
        this.featureMapper = featureMapper;
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
        if (StringUtils.isNotBlank(productCode)) {
            return featureService.findFeaturesByProduct(productCode).stream()
                    .map(featureMapper::toDto)
                    .toList();
        } else {
            return featureService.findFeaturesByRelease(releaseCode).stream()
                    .map(featureMapper::toDto)
                    .toList();
        }
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
        return featureService
                .findFeatureByCode(code)
                .map(featureMapper::toDto)
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
                payload.code(),
                payload.title(),
                payload.description(),
                payload.assignedTo(),
                username);
        Long id = featureService.createFeature(cmd);
        log.info("Created feature with id {}", id);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{code}")
                .buildAndExpand(payload.code())
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
                code, payload.title(), payload.description(), payload.status(), payload.assignedTo(), username);
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

    record CreateFeaturePayload(
            @NotEmpty(message = "Product code is required") String productCode,
            @NotEmpty(message = "Release code is required") String releaseCode,
            @NotEmpty(message = "Code is required") @Size(max = 50, message = "Code cannot exceed 50 characters") String code,
            @NotEmpty(message = "Title is required") @Size(max = 500, message = "Title cannot exceed 500 characters") String title,
            String description,
            String assignedTo) {}

    record UpdateFeaturePayload(
            @NotEmpty(message = "Title is required") @Size(max = 500, message = "Title cannot exceed 500 characters") String title,
            String description,
            String assignedTo,
            FeatureStatus status) {}
}

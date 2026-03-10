package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.CreateReleasePayload;
import com.sivalabs.ft.features.api.models.PagedResult;
import com.sivalabs.ft.features.api.models.UpdateReleasePayload;
import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.Commands.CreateReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand;
import com.sivalabs.ft.features.domain.ReleaseService;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
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
@RequestMapping("/api/releases")
@Tag(name = "Releases API")
class ReleaseController {
    private static final Logger log = LoggerFactory.getLogger(ReleaseController.class);
    private final ReleaseService releaseService;

    ReleaseController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping("")
    @Operation(
            summary = "Find releases with optional filters and pagination",
            description = "Find releases with optional filters (productCode, status, owner, date range) and pagination",
            responses = {@ApiResponse(responseCode = "200", description = "Successful response")})
    PagedResult<ReleaseDto> getReleases(
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) ReleaseStatus status,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return releaseService.findReleases(productCode, status, owner, startDate, endDate, page, size);
    }

    @GetMapping("/overdue")
    @Operation(
            summary = "Find overdue releases",
            description = "Returns releases past plannedReleaseDate that are not completed",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array = @ArraySchema(schema = @Schema(implementation = ReleaseDto.class))))
            })
    List<ReleaseDto> getOverdueReleases() {
        return releaseService.findOverdueReleases();
    }

    @GetMapping("/at-risk")
    @Operation(
            summary = "Find at-risk releases",
            description = "Returns releases approaching their deadline within the given days threshold",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array = @ArraySchema(schema = @Schema(implementation = ReleaseDto.class))))
            })
    List<ReleaseDto> getAtRiskReleases(@RequestParam(defaultValue = "7") int daysThreshold) {
        return releaseService.findAtRiskReleases(daysThreshold);
    }

    @GetMapping("/by-status")
    @Operation(
            summary = "Find releases by status",
            description = "Returns releases filtered by the given status",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array = @ArraySchema(schema = @Schema(implementation = ReleaseDto.class))))
            })
    List<ReleaseDto> getReleasesByStatus(@RequestParam ReleaseStatus status) {
        return releaseService.findReleasesByStatus(status);
    }

    @GetMapping("/by-owner")
    @Operation(
            summary = "Find releases by owner",
            description = "Returns releases filtered by the given owner",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array = @ArraySchema(schema = @Schema(implementation = ReleaseDto.class))))
            })
    List<ReleaseDto> getReleasesByOwner(@RequestParam String owner) {
        return releaseService.findReleasesByOwner(owner);
    }

    @GetMapping("/by-date-range")
    @Operation(
            summary = "Find releases by date range",
            description = "Returns releases with plannedReleaseDate within the given range",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array = @ArraySchema(schema = @Schema(implementation = ReleaseDto.class))))
            })
    List<ReleaseDto> getReleasesByDateRange(@RequestParam Instant startDate, @RequestParam Instant endDate) {
        return releaseService.findReleasesByDateRange(startDate, endDate);
    }

    @GetMapping("/{code}")
    @Operation(
            summary = "Find release by code",
            description = "Find release by code",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ReleaseDto.class))),
                @ApiResponse(responseCode = "404", description = "Release not found")
            })
    ResponseEntity<ReleaseDto> getRelease(@PathVariable String code) {
        return releaseService
                .findReleaseByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("")
    @Operation(
            summary = "Create a new release",
            description = "Create a new release",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Successful response",
                        headers =
                                @Header(
                                        name = "Location",
                                        required = true,
                                        description = "URI of the created release")),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
            })
    ResponseEntity<Void> createRelease(@RequestBody @Valid CreateReleasePayload payload) {
        var username = SecurityUtils.getCurrentUsername();
        var cmd = new CreateReleaseCommand(
                payload.productCode(),
                payload.code(),
                payload.description(),
                payload.plannedStartDate(),
                payload.plannedReleaseDate(),
                payload.owner(),
                payload.notes(),
                username);
        String code = releaseService.createRelease(cmd);
        log.info("Created release with code {}", code);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{code}")
                .buildAndExpand(code)
                .toUri();
        return ResponseEntity.created(location).build();
    }

    @PutMapping("/{code}")
    @Operation(
            summary = "Update an existing release",
            description = "Update an existing release",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
            })
    void updateRelease(@PathVariable String code, @RequestBody UpdateReleasePayload payload) {
        var username = SecurityUtils.getCurrentUsername();
        var cmd = new UpdateReleaseCommand(
                code,
                payload.description(),
                payload.status(),
                payload.releasedAt(),
                payload.plannedStartDate(),
                payload.plannedReleaseDate(),
                payload.actualReleaseDate(),
                payload.owner(),
                payload.notes(),
                username);
        releaseService.updateRelease(cmd);
    }

    @DeleteMapping("/{code}")
    @Operation(
            summary = "Delete an existing release",
            description = "Delete an existing release",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
            })
    ResponseEntity<Void> deleteRelease(@PathVariable String code) {
        if (!releaseService.isReleaseExists(code)) {
            return ResponseEntity.notFound().build();
        }
        releaseService.deleteRelease(code);
        return ResponseEntity.ok().build();
    }
}

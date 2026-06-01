package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.CreateReleasePayload;
import com.sivalabs.ft.features.api.models.UpdateReleasePayload;
import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.Commands.CreateReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand;
import com.sivalabs.ft.features.domain.ReleaseService;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
@Validated
class ReleaseController {
    private static final Logger log = LoggerFactory.getLogger(ReleaseController.class);
    private final ReleaseService releaseService;

    ReleaseController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    private Pageable createPageable(int page, int size, String sort, String direction) {
        Sort.Direction sortDirection = direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(sortDirection, sort));
    }

    @GetMapping("")
    @Operation(
            summary = "Find releases with filters",
            description = "Find releases by product code and other optional filters with pagination",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class)))
            })
    Page<ReleaseDto> getProductReleases(
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) ReleaseStatus status,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        // Validate page size
        if (size > 100) {
            size = 100;
        }
        Pageable pageable = createPageable(page, size, sort, direction);
        return releaseService.findWithFilters(productCode, status, owner, startDate, endDate, pageable);
    }

    @GetMapping("/overdue")
    @Operation(
            summary = "Find overdue releases",
            description = "Find releases that are overdue (past planned release date but not completed/cancelled)",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class)))
            })
    Page<ReleaseDto> getOverdueReleases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "plannedReleaseDate") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        if (size > 100) {
            size = 100;
        }
        Pageable pageable = createPageable(page, size, sort, direction);
        return releaseService.findOverdueReleases(pageable);
    }

    @GetMapping("/at-risk")
    @Operation(
            summary = "Find at-risk releases",
            description = "Find releases approaching deadline within specified threshold days",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = Page.class))),
                @ApiResponse(responseCode = "400", description = "Invalid daysThreshold")
            })
    Page<ReleaseDto> getAtRiskReleases(
            @RequestParam(defaultValue = "7") @Min(1) int daysThreshold,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "plannedReleaseDate") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        if (size > 100) {
            size = 100;
        }
        Pageable pageable = createPageable(page, size, sort, direction);
        return releaseService.findAtRiskReleases(daysThreshold, pageable);
    }

    @GetMapping("/by-status")
    @Operation(
            summary = "Find releases by status",
            description = "Filter releases by specific status",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class)))
            })
    Page<ReleaseDto> getReleasesByStatus(
            @RequestParam ReleaseStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        if (size > 100) {
            size = 100;
        }
        Pageable pageable = createPageable(page, size, sort, direction);
        return releaseService.findByStatus(status, pageable);
    }

    @GetMapping("/by-owner")
    @Operation(
            summary = "Find releases by owner",
            description = "Find releases owned by a specific user",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class)))
            })
    Page<ReleaseDto> getReleasesByOwner(
            @RequestParam String owner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "plannedReleaseDate") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        if (size > 100) {
            size = 100;
        }
        Pageable pageable = createPageable(page, size, sort, direction);
        return releaseService.findByOwner(owner, pageable);
    }

    @GetMapping("/by-date-range")
    @Operation(
            summary = "Find releases by date range",
            description = "Find releases within a specific date range",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = Page.class))),
                @ApiResponse(responseCode = "400", description = "Invalid date range")
            })
    Page<ReleaseDto> getReleasesByDateRange(
            @RequestParam Instant startDate,
            @RequestParam Instant endDate,
            @RequestParam(required = false) ReleaseStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "plannedReleaseDate") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        if (size > 100) {
            size = 100;
        }
        Pageable pageable = createPageable(page, size, sort, direction);
        return releaseService.findByDateRange(startDate, endDate, status, pageable);
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
                payload.plannedReleaseDate(),
                payload.releaseOwner(),
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
                payload.plannedReleaseDate(),
                payload.releaseOwner(),
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

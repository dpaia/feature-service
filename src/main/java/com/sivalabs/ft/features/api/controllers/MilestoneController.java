package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.CreateMilestonePayload;
import com.sivalabs.ft.features.api.models.UpdateMilestonePayload;
import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.Commands.CreateMilestoneCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateMilestoneCommand;
import com.sivalabs.ft.features.domain.MilestoneService;
import com.sivalabs.ft.features.domain.dtos.MilestoneDto;
import com.sivalabs.ft.features.domain.dtos.MilestoneSummaryDto;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
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
@RequestMapping("/api/milestones")
@Tag(name = "Milestones API")
class MilestoneController {
    private static final Logger log = LoggerFactory.getLogger(MilestoneController.class);
    private final MilestoneService milestoneService;

    MilestoneController(MilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    @GetMapping("")
    @Operation(
            summary = "Find milestones by product code",
            description = "Find milestones by product code with optional filters",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array =
                                                @ArraySchema(
                                                        schema = @Schema(implementation = MilestoneSummaryDto.class)))),
                @ApiResponse(responseCode = "400", description = "Missing required parameter"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    List<MilestoneSummaryDto> getMilestones(
            @RequestParam(value = "productCode", required = false) String productCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "owner", required = false) String owner) {
        if (StringUtils.isBlank(productCode)) {
            throw new BadRequestException("productCode is required");
        }
        return milestoneService.findMilestonesByProductCode(productCode, status, owner);
    }

    @GetMapping("/{code}")
    @Operation(
            summary = "Find milestone by code",
            description = "Find milestone by code with associated releases",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = MilestoneDto.class))),
                @ApiResponse(responseCode = "404", description = "Milestone not found"),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    ResponseEntity<MilestoneDto> getMilestone(@PathVariable String code) {
        return milestoneService
                .findMilestoneByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("")
    @Operation(
            summary = "Create a new milestone",
            description = "Create a new milestone",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Successful response",
                        headers =
                                @Header(
                                        name = "Location",
                                        required = true,
                                        description = "URI of the created milestone")),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
            })
    ResponseEntity<Void> createMilestone(@RequestBody @Valid CreateMilestonePayload payload) {
        SecurityUtils.requireAnyRole("PRODUCT_MANAGER", "ADMIN");
        var username = SecurityUtils.getCurrentUsername();
        var cmd = new CreateMilestoneCommand(
                payload.productCode(),
                payload.code(),
                payload.name(),
                payload.description(),
                payload.targetDate(),
                payload.status(),
                payload.owner(),
                payload.notes(),
                username);
        String code = milestoneService.createMilestone(cmd);
        log.info("Created milestone with code {}", code);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{code}")
                .buildAndExpand(code)
                .toUri();
        return ResponseEntity.created(location).build();
    }

    @PutMapping("/{code}")
    @Operation(
            summary = "Update an existing milestone",
            description = "Update an existing milestone",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
                @ApiResponse(responseCode = "404", description = "Milestone not found")
            })
    void updateMilestone(@PathVariable String code, @RequestBody @Valid UpdateMilestonePayload payload) {
        SecurityUtils.requireAnyRole("PRODUCT_MANAGER", "ADMIN");
        var username = SecurityUtils.getCurrentUsername();
        var cmd = new UpdateMilestoneCommand(
                code,
                payload.name(),
                payload.description(),
                payload.targetDate(),
                payload.actualDate(),
                payload.status(),
                payload.owner(),
                payload.notes(),
                username);
        milestoneService.updateMilestone(cmd);
    }

    @DeleteMapping("/{code}")
    @Operation(
            summary = "Delete an existing milestone",
            description = "Delete an existing milestone",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
                @ApiResponse(responseCode = "404", description = "Milestone not found")
            })
    ResponseEntity<Void> deleteMilestone(@PathVariable String code) {
        SecurityUtils.requireRole("ADMIN");
        if (!milestoneService.isMilestoneExists(code)) {
            return ResponseEntity.notFound().build();
        }
        milestoneService.deleteMilestone(code);
        return ResponseEntity.ok().build();
    }
}

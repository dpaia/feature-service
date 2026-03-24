package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.CreateMilestonePayload;
import com.sivalabs.ft.features.api.models.UpdateMilestonePayload;
import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.Commands.CreateMilestoneCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateMilestoneCommand;
import com.sivalabs.ft.features.domain.MilestoneService;
import com.sivalabs.ft.features.domain.dtos.MilestoneDto;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.models.MilestoneStatus;
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

    @PostMapping
    @Operation(
            summary = "Create a new milestone",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Milestone created successfully",
                        headers = @Header(name = "Location", description = "URL of the created milestone")),
                @ApiResponse(responseCode = "400", description = "Bad Request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden")
            })
    ResponseEntity<Void> createMilestone(@Valid @RequestBody CreateMilestonePayload payload) {
        log.info("Creating milestone with code: {}", payload.code());
        String currentUser = SecurityUtils.getCurrentUsername();

        CreateMilestoneCommand command = new CreateMilestoneCommand(
                payload.productCode(),
                payload.code(),
                payload.name(),
                payload.description(),
                payload.targetDate(),
                payload.status(),
                payload.owner(),
                payload.notes(),
                currentUser);

        milestoneService.createMilestone(command);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{code}")
                .buildAndExpand(payload.code())
                .toUri();

        return ResponseEntity.created(location).build();
    }

    @PutMapping("/{code}")
    @Operation(
            summary = "Update an existing milestone",
            responses = {
                @ApiResponse(responseCode = "200", description = "Milestone updated successfully"),
                @ApiResponse(responseCode = "400", description = "Bad Request"),
                @ApiResponse(responseCode = "404", description = "Milestone not found"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden")
            })
    ResponseEntity<Void> updateMilestone(
            @PathVariable String code, @Valid @RequestBody UpdateMilestonePayload payload) {
        log.info("Updating milestone with code: {}", code);
        String currentUser = SecurityUtils.getCurrentUsername();

        UpdateMilestoneCommand command = new UpdateMilestoneCommand(
                code,
                payload.name(),
                payload.description(),
                payload.targetDate(),
                payload.actualDate(),
                payload.status(),
                payload.owner(),
                payload.notes(),
                currentUser);

        milestoneService.updateMilestone(command);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{code}")
    @Operation(
            summary = "Delete a milestone",
            responses = {
                @ApiResponse(responseCode = "200", description = "Milestone deleted successfully"),
                @ApiResponse(responseCode = "404", description = "Milestone not found"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden")
            })
    ResponseEntity<Void> deleteMilestone(@PathVariable String code) {
        log.info("Deleting milestone with code: {}", code);
        milestoneService.deleteMilestone(code);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{code}")
    @Operation(
            summary = "Get milestone details including associated releases",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Milestone details retrieved successfully",
                        content = @Content(schema = @Schema(implementation = MilestoneDto.class))),
                @ApiResponse(responseCode = "404", description = "Milestone not found")
            })
    ResponseEntity<MilestoneDto> getMilestone(@PathVariable String code) {
        log.info("Getting milestone with code: {}", code);
        return milestoneService
                .findMilestoneByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(
            summary = "List milestones with filters",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Milestones retrieved successfully",
                        content =
                                @Content(array = @ArraySchema(schema = @Schema(implementation = MilestoneDto.class)))),
                @ApiResponse(responseCode = "400", description = "Bad Request - productCode is required")
            })
    ResponseEntity<List<MilestoneDto>> getMilestones(
            @RequestParam String productCode,
            @RequestParam(required = false) MilestoneStatus status,
            @RequestParam(required = false) String owner) {
        log.info("Getting milestones for product: {}, status: {}, owner: {}", productCode, status, owner);

        if (productCode == null || productCode.trim().isEmpty()) {
            throw new BadRequestException("productCode parameter is required");
        }

        List<MilestoneDto> milestones;

        if (status != null && owner != null) {
            milestones = milestoneService.findMilestonesByProductCodeAndStatusAndOwner(productCode, status, owner);
        } else if (status != null) {
            milestones = milestoneService.findMilestonesByProductCodeAndStatus(productCode, status);
        } else if (owner != null) {
            milestones = milestoneService.findMilestonesByProductCodeAndOwner(productCode, owner);
        } else {
            milestones = milestoneService.findMilestonesByProductCode(productCode);
        }

        return ResponseEntity.ok(milestones);
    }
}

package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.CreateTagPayload;
import com.sivalabs.ft.features.api.models.UpdateTagPayload;
import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.Commands.CreateTagCommand;
import com.sivalabs.ft.features.domain.Commands.DeleteTagCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateTagCommand;
import com.sivalabs.ft.features.domain.TagService;
import com.sivalabs.ft.features.domain.dtos.TagDto;
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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/tags")
@Tag(name = "Tags API")
class TagController {
    private static final Logger log = LoggerFactory.getLogger(TagController.class);
    private final TagService tagService;

    TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("")
    @Operation(
            summary = "Get all tags",
            description = "Get all tags",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array = @ArraySchema(schema = @Schema(implementation = TagDto.class))))
            })
    List<TagDto> getAllTags() {
        return tagService.getAllTags();
    }

    @GetMapping("/search")
    @Operation(
            summary = "Search tags by name",
            description = "Search tags by name",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array = @ArraySchema(schema = @Schema(implementation = TagDto.class))))
            })
    List<TagDto> searchTags(@RequestParam String name) {
        return tagService.searchTags(name);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Find tag by ID",
            description = "Find tag by ID",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = TagDto.class))),
                @ApiResponse(responseCode = "404", description = "Tag not found")
            })
    ResponseEntity<TagDto> getTag(@PathVariable Long id) {
        Optional<TagDto> tagDtoOptional = tagService.getTagById(id);
        return tagDtoOptional
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("")
    @Operation(
            summary = "Create a new tag",
            description = "Create a new tag",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Successful response",
                        headers = @Header(name = "Location", required = true, description = "URI of the created tag")),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
            })
    ResponseEntity<Void> createTag(@RequestBody @Valid CreateTagPayload payload) {
        var username = SecurityUtils.getCurrentUsername();
        var cmd = new CreateTagCommand(payload.name(), payload.description(), username);
        Long id = tagService.createTag(cmd);
        log.info("Created tag with id {}", id);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).build();
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update an existing tag",
            description = "Update an existing tag",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
                @ApiResponse(responseCode = "404", description = "Tag not found")
            })
    ResponseEntity<Void> updateTag(@PathVariable Long id, @RequestBody @Valid UpdateTagPayload payload) {
        var username = SecurityUtils.getCurrentUsername();
        if (!tagService.isTagExists(id)) {
            return ResponseEntity.notFound().build();
        }
        var cmd = new UpdateTagCommand(id, payload.name(), payload.description(), username);
        tagService.updateTag(cmd);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete an existing tag",
            description = "Delete an existing tag",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
                @ApiResponse(responseCode = "404", description = "Tag not found")
            })
    ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        var username = SecurityUtils.getCurrentUsername();
        if (!tagService.isTagExists(id)) {
            return ResponseEntity.notFound().build();
        }
        var cmd = new DeleteTagCommand(id, username);
        tagService.deleteTag(cmd);
        return ResponseEntity.ok().build();
    }
}

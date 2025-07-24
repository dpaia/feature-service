package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.models.CreateCategoryPayload;
import com.sivalabs.ft.features.api.models.UpdateCategoryPayload;
import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.CategoryService;
import com.sivalabs.ft.features.domain.Commands.CreateCategoryCommand;
import com.sivalabs.ft.features.domain.Commands.DeleteCategoryCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateCategoryCommand;
import com.sivalabs.ft.features.domain.dtos.CategoryDto;
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
@RequestMapping("/api/categories")
@Tag(name = "Categories API")
class CategoryController {
    private static final Logger log = LoggerFactory.getLogger(CategoryController.class);
    private final CategoryService categoryService;

    CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("")
    @Operation(
            summary = "Get all categories",
            description = "Get all categories",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array = @ArraySchema(schema = @Schema(implementation = CategoryDto.class))))
            })
    List<CategoryDto> getAllCategories() {
        return categoryService.getAllCategories();
    }

    @GetMapping("/search")
    @Operation(
            summary = "Search categories by name",
            description = "Search categories by name",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array = @ArraySchema(schema = @Schema(implementation = CategoryDto.class))))
            })
    List<CategoryDto> searchCategories(@RequestParam String name) {
        return categoryService.searchCategories(name);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Find category by ID",
            description = "Find category by ID",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CategoryDto.class))),
                @ApiResponse(responseCode = "404", description = "Category not found")
            })
    ResponseEntity<CategoryDto> getCategory(@PathVariable Long id) {
        Optional<CategoryDto> categoryDtoOptional = categoryService.getCategoryById(id);
        return categoryDtoOptional
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("")
    @Operation(
            summary = "Create a new category",
            description = "Create a new category",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Successful response",
                        headers =
                                @Header(
                                        name = "Location",
                                        required = true,
                                        description = "URI of the created category")),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
            })
    ResponseEntity<Void> createCategory(@RequestBody @Valid CreateCategoryPayload payload) {
        var username = SecurityUtils.getCurrentUsername();
        var cmd =
                new CreateCategoryCommand(payload.name(), payload.description(), payload.parentCategoryId(), username);
        Long id = categoryService.createCategory(cmd);
        log.info("Created category with id {}", id);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).build();
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update an existing category",
            description = "Update an existing category",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
                @ApiResponse(responseCode = "404", description = "Category not found")
            })
    ResponseEntity<Void> updateCategory(@PathVariable Long id, @RequestBody @Valid UpdateCategoryPayload payload) {
        var username = SecurityUtils.getCurrentUsername();
        if (!categoryService.isCategoryExists(id)) {
            return ResponseEntity.notFound().build();
        }
        var cmd = new UpdateCategoryCommand(
                id, payload.name(), payload.description(), payload.parentCategoryId(), username);
        categoryService.updateCategory(cmd);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete an existing category",
            description = "Delete an existing category",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful response"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "403", description = "Forbidden"),
                @ApiResponse(responseCode = "404", description = "Category not found")
            })
    ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        var username = SecurityUtils.getCurrentUsername();
        if (!categoryService.isCategoryExists(id)) {
            return ResponseEntity.notFound().build();
        }
        var cmd = new DeleteCategoryCommand(id, username);
        categoryService.deleteCategory(cmd);
        return ResponseEntity.ok().build();
    }
}

package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.dtos.CategoryDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class CategoryControllerTests extends AbstractIT {

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldGetAllCategories() {
        var result = mvc.get().uri("/api/categories").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(10);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldSearchCategoriesByName() {
        var result = mvc.get().uri("/api/categories/search?name=ci").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(1);

        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].name")
                .asString()
                .isEqualTo("CICD");
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldGetCategoryById() {
        var result = mvc.get().uri("/api/categories/{id}", 7).exchange();
        assertThat(result).hasStatusOk().bodyJson().convertTo(CategoryDto.class).satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(7);
            assertThat(dto.name()).isEqualTo("SpringBoot");
            assertThat(dto.description()).isEqualTo("Spring Boot framework");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenCategoryNotFound() {
        var result = mvc.get().uri("/api/categories/{id}", 999L).exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateNewCategory() {
        var payload =
                """
            {
                "name": "New Category",
                "description": "New Category Description",
                "parentCategoryId": null
            }
            """;

        var result = mvc.post()
                .uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String location = result.getMvcResult().getResponse().getHeader("Location");

        // Verify creation
        assertThat(location).isNotNull();
        var id = location.substring(location.lastIndexOf("/") + 1);

        var getResult = mvc.get().uri(location).exchange();
        assertThat(getResult)
                .hasStatusOk()
                .bodyJson()
                .convertTo(CategoryDto.class)
                .satisfies(dto -> {
                    assertThat(dto.name()).isEqualTo("New Category");
                    assertThat(dto.description()).isEqualTo("New Category Description");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateCategoryWithParent() {
        // Create parent category
        Long parentId = 7L;

        var payload = String.format(
                """
            {
                "name": "Child Category",
                "description": "Child Category Description",
                "parentCategoryId": %d
            }
            """,
                parentId);

        var result = mvc.post()
                .uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String location = result.getMvcResult().getResponse().getHeader("Location");

        // Verify creation with parent
        assertThat(location).isNotNull();
        var getResult = mvc.get().uri(location).exchange();
        assertThat(getResult)
                .hasStatusOk()
                .bodyJson()
                .convertTo(CategoryDto.class)
                .satisfies(dto -> {
                    assertThat(dto.name()).isEqualTo("Child Category");
                    assertThat(dto.description()).isEqualTo("Child Category Description");
                    assertThat(dto.parentCategory()).isNotNull();
                    assertThat(dto.parentCategory().id()).isEqualTo(parentId);
                    assertThat(dto.parentCategory().name()).isEqualTo("SpringBoot");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldUpdateCategory() {
        Long id = 7L;

        var payload =
                """
            {
                "name": "Updated Category",
                "description": "Updated Category Description",
                "parentCategoryId": null
            }
            """;

        var result = mvc.put()
                .uri("/api/categories/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify the update
        var updatedCategory = mvc.get().uri("/api/categories/{id}", id).exchange();
        assertThat(updatedCategory)
                .hasStatusOk()
                .bodyJson()
                .convertTo(CategoryDto.class)
                .satisfies(dto -> {
                    assertThat(dto.name()).isEqualTo("Updated Category");
                    assertThat(dto.description()).isEqualTo("Updated Category Description");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldDeleteCategory() {
        var result = mvc.delete().uri("/api/categories/{id}", 7).exchange();
        assertThat(result).hasStatusOk();

        // Verify deletion
        var getResult = mvc.get().uri("/api/categories/{id}", 7).exchange();
        assertThat(getResult).hasStatus(HttpStatus.NOT_FOUND);
    }
}

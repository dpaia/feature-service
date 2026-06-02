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
                .isEqualTo(6);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldSearchCategoriesByName() {
        var result = mvc.get().uri("/api/categories/search?name=bug").exchange();
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
                .isEqualTo("Bug Fix");
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldGetCategoryById() {
        var result = mvc.get().uri("/api/categories/{id}", 1).exchange();
        assertThat(result).hasStatusOk().bodyJson().convertTo(CategoryDto.class).satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(1);
            assertThat(dto.name()).isEqualTo("New Feature");
            assertThat(dto.description()).isEqualTo("New feature added to the product");
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
                "name": "Security",
                "description": "Security related work",
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
        assertThat(location).isNotNull();

        var getResult = mvc.get().uri(location).exchange();
        assertThat(getResult)
                .hasStatusOk()
                .bodyJson()
                .convertTo(CategoryDto.class)
                .satisfies(dto -> {
                    assertThat(dto.name()).isEqualTo("Security");
                    assertThat(dto.description()).isEqualTo("Security related work");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateCategoryWithParent() {
        var payload =
                """
            {
                "name": "Regression",
                "description": "Regression fixes",
                "parentCategoryId": 3
            }
            """;

        var result = mvc.post()
                .uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String location = result.getMvcResult().getResponse().getHeader("Location");
        assertThat(location).isNotNull();

        var getResult = mvc.get().uri(location).exchange();
        assertThat(getResult)
                .hasStatusOk()
                .bodyJson()
                .convertTo(CategoryDto.class)
                .satisfies(dto -> {
                    assertThat(dto.name()).isEqualTo("Regression");
                    assertThat(dto.parentCategory()).isNotNull();
                    assertThat(dto.parentCategory().id()).isEqualTo(3L);
                    assertThat(dto.parentCategory().name()).isEqualTo("Bug Fix");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldUpdateCategory() {
        var payload =
                """
            {
                "name": "Updated Category",
                "description": "Updated Category Description",
                "parentCategoryId": null
            }
            """;

        var result = mvc.put()
                .uri("/api/categories/{id}", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        var updatedCategory = mvc.get().uri("/api/categories/{id}", 1).exchange();
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
        var result = mvc.delete().uri("/api/categories/{id}", 1).exchange();
        assertThat(result).hasStatusOk();

        var getResult = mvc.get().uri("/api/categories/{id}", 1).exchange();
        assertThat(getResult).hasStatus(HttpStatus.NOT_FOUND);
    }
}

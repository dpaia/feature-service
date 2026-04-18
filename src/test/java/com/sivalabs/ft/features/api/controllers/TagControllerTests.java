package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.dtos.TagDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class TagControllerTests extends AbstractIT {

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldGetAllTags() {
        var result = mvc.get().uri("/api/tags").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(4);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldSearchTagsByName() {
        var result = mvc.get().uri("/api/tags/search?name=bug").exchange();
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
                .isEqualTo("bug");
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldGetTagById() {
        var result = mvc.get().uri("/api/tags/{id}", 1).exchange();
        assertThat(result).hasStatusOk().bodyJson().convertTo(TagDto.class).satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(1);
            assertThat(dto.name()).isEqualTo("bug");
            assertThat(dto.description()).isEqualTo("Bug fix");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenTagNotFound() {
        var result = mvc.get().uri("/api/tags/{id}", 999L).exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateNewTag() {
        var payload =
                """
            {
                "name": "New Tag",
                "description": "New Tag Description"
            }
            """;

        var result = mvc.post()
                .uri("/api/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String location = result.getMvcResult().getResponse().getHeader("Location");

        // Verify creation
        assertThat(location).isNotNull();
        var id = location.substring(location.lastIndexOf("/") + 1);

        var getResult = mvc.get().uri(location).exchange();
        assertThat(getResult).hasStatusOk().bodyJson().convertTo(TagDto.class).satisfies(dto -> {
            assertThat(dto.name()).isEqualTo("New Tag");
            assertThat(dto.description()).isEqualTo("New Tag Description");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldUpdateTag() {
        var payload =
                """
            {
                "name": "Updated Tag",
                "description": "Updated Tag Description"
            }
            """;

        var result = mvc.put()
                .uri("/api/tags/{id}", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify the update
        var updatedTag = mvc.get().uri("/api/tags/{id}", 1).exchange();
        assertThat(updatedTag).hasStatusOk().bodyJson().convertTo(TagDto.class).satisfies(dto -> {
            assertThat(dto.name()).isEqualTo("Updated Tag");
            assertThat(dto.description()).isEqualTo("Updated Tag Description");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldDeleteTag() {
        var result = mvc.delete().uri("/api/tags/{id}", 1).exchange();
        assertThat(result).hasStatusOk();

        // Verify deletion
        var getResult = mvc.get().uri("/api/tags/{id}", 1).exchange();
        assertThat(getResult).hasStatus(HttpStatus.NOT_FOUND);
    }
}

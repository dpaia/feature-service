package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class ReleaseControllerTests extends AbstractIT {

    @Test
    void shouldGetReleasesByProductCode() {
        var result =
                mvc.get().uri("/api/releases?productCode={code}", "intellij").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(2);
    }

    @Test
    void shouldGetReleaseByCode() {
        String code = "IDEA-2023.3.8";
        var result = mvc.get().uri("/api/releases/{code}", code).exchange();
        assertThat(result).hasStatusOk().bodyJson().convertTo(ReleaseDto.class).satisfies(dto -> {
            assertThat(dto.code()).isEqualTo(code);
        });
    }

    @Test
    void shouldReturn404WhenReleaseNotFound() {
        var result = mvc.get().uri("/api/releases/{code}", "INVALID_CODE").exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateNewRelease() {
        var payload =
                """
            {
                "productCode": "intellij",
                "code": "IDEA-2025.1",
                "description": "IntelliJ IDEA 2025.1",
                "plannedStartDate": "2025-01-01T00:00:00Z",
                "plannedReleaseDate": "2025-04-01T00:00:00Z",
                "owner": "teamlead",
                "notes": "Spring 2025 release"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateNewReleaseWithPlanningFields() {
        var payload =
                """
            {
                "productCode": "intellij",
                "code": "IDEA-2025.3",
                "description": "IntelliJ IDEA 2025.3",
                "plannedStartDate": "2025-06-01T00:00:00Z",
                "plannedReleaseDate": "2025-09-01T00:00:00Z",
                "owner": "releasemanager",
                "notes": "Autumn 2025 release"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);

        // Extract the created code from Location header and verify planning fields
        var location = result.getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        String createdCode = location.substring(location.lastIndexOf('/') + 1);
        var getResult = mvc.get().uri("/api/releases/{code}", createdCode).exchange();
        assertThat(getResult)
                .hasStatusOk()
                .bodyJson()
                .convertTo(ReleaseDto.class)
                .satisfies(dto -> {
                    assertThat(dto.owner()).isEqualTo("releasemanager");
                    assertThat(dto.notes()).isEqualTo("Autumn 2025 release");
                    assertThat(dto.plannedStartDate()).isNotNull();
                    assertThat(dto.plannedReleaseDate()).isNotNull();
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldUpdateRelease() {
        var payload =
                """
            {
                "description": "Updated description",
                "status": "RELEASED",
                "releasedAt": "2023-12-01T10:00:00Z"
            }
            """;

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify the update
        var updatedRelease =
                mvc.get().uri("/api/releases/{code}", "IDEA-2023.3.8").exchange();
        assertThat(updatedRelease)
                .hasStatusOk()
                .bodyJson()
                .convertTo(ReleaseDto.class)
                .satisfies(dto -> {
                    assertThat(dto.description()).isEqualTo("Updated description");
                    assertThat(dto.status()).isEqualTo(ReleaseStatus.RELEASED);
                    assertThat(dto.releasedAt()).isNotNull();
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldUpdateReleasePlanningFields() {
        var payload =
                """
            {
                "description": "Planning updated",
                "status": "IN_PROGRESS",
                "plannedStartDate": "2024-01-01T00:00:00Z",
                "plannedReleaseDate": "2024-06-01T00:00:00Z",
                "actualReleaseDate": "2024-06-15T00:00:00Z",
                "owner": "newowner",
                "notes": "Updated notes"
            }
            """;

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-2024.2.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify planning fields are updated
        var updatedRelease =
                mvc.get().uri("/api/releases/{code}", "IDEA-2024.2.3").exchange();
        assertThat(updatedRelease)
                .hasStatusOk()
                .bodyJson()
                .convertTo(ReleaseDto.class)
                .satisfies(dto -> {
                    assertThat(dto.status()).isEqualTo(ReleaseStatus.IN_PROGRESS);
                    assertThat(dto.plannedStartDate()).isNotNull();
                    assertThat(dto.plannedReleaseDate()).isNotNull();
                    assertThat(dto.actualReleaseDate()).isNotNull();
                    assertThat(dto.owner()).isEqualTo("newowner");
                    assertThat(dto.notes()).isEqualTo("Updated notes");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenUpdatingNonExistentRelease() {
        var payload =
                """
            {
                "description": "Updated description",
                "status": "IN_PROGRESS"
            }
            """;

        var result = mvc.put()
                .uri("/api/releases/{code}", "NONEXISTENT-CODE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldDeleteRelease() {
        var result = mvc.delete().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
        assertThat(result).hasStatusOk();

        // Verify deletion
        var getResult = mvc.get().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
        assertThat(getResult).hasStatus(HttpStatus.NOT_FOUND);
    }
}

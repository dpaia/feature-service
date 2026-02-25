package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class ReleaseFeaturesControllerTests extends AbstractIT {

    // GET /api/releases/{releaseCode}/features

    @Test
    void shouldGetReleaseFeaturesWithoutFilters() {
        var result = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(2);
    }

    @Test
    void shouldGetReleaseFeaturesFilteredByPlanningStatus() {
        var result = mvc.get()
                .uri("/api/releases/{releaseCode}/features?planningStatus={status}", "IDEA-2023.3.8", "NOT_STARTED")
                .exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(0);
    }

    @Test
    void shouldGetReleaseFeaturesFilteredByOwner() {
        var result = mvc.get()
                .uri("/api/releases/{releaseCode}/features?owner={owner}", "IDEA-2023.3.8", "unknown-owner")
                .exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(0);
    }

    @Test
    void shouldGetReleaseFeaturesFilteredByOverdue() {
        var result = mvc.get()
                .uri("/api/releases/{releaseCode}/features?overdue=true", "IDEA-2023.3.8")
                .exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    void shouldGetReleaseFeaturesFilteredByBlocked() {
        var result = mvc.get()
                .uri("/api/releases/{releaseCode}/features?blocked=true", "IDEA-2023.3.8")
                .exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(0);
    }

    // POST /api/releases/{releaseCode}/features

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldAssignFeatureToRelease() {
        var payload =
                """
            {
                "featureCode": "GO-3",
                "plannedCompletionDate": "2025-12-31",
                "featureOwner": "john.doe",
                "notes": "Initial planning notes"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify the assignment via planning details endpoint
        var featureResult = mvc.get().uri("/api/features/{code}", "GO-3").exchange();
        assertThat(featureResult)
                .hasStatusOk()
                .bodyJson()
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.releaseCode()).isEqualTo("IDEA-2023.3.8");
                    assertThat(dto.featureOwner()).isEqualTo("john.doe");
                    assertThat(dto.notes()).isEqualTo("Initial planning notes");
                });
    }

    @Test
    void shouldReturn401WhenAssigningFeatureWithoutAuthentication() {
        var payload = """
            {
                "featureCode": "GO-3"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenAssigningToNonExistentRelease() {
        var payload = """
            {
                "featureCode": "IDEA-1"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "NON-EXISTENT-RELEASE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenAssigningNonExistentFeature() {
        var payload = """
            {
                "featureCode": "INVALID-CODE"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    // PATCH /api/releases/{releaseCode}/features/{featureCode}/planning

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldUpdateFeaturePlanning() {
        var payload =
                """
            {
                "planningStatus": "NOT_STARTED",
                "featureOwner": "jane.doe",
                "notes": "Updated notes"
            }
            """;

        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        var featureResult = mvc.get().uri("/api/features/{code}", "IDEA-1").exchange();
        assertThat(featureResult)
                .hasStatusOk()
                .bodyJson()
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.planningStatus()).isEqualTo(FeaturePlanningStatus.NOT_STARTED);
                    assertThat(dto.featureOwner()).isEqualTo("jane.doe");
                    assertThat(dto.notes()).isEqualTo("Updated notes");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400ForInvalidPlanningStatusTransition() {
        // First set to NOT_STARTED
        var setPayload =
                """
            {
                "planningStatus": "NOT_STARTED"
            }
            """;
        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(setPayload)
                .exchange();

        // Try invalid transition NOT_STARTED -> DONE
        var invalidPayload = """
            {
                "planningStatus": "DONE"
            }
            """;
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn401WhenUpdatingPlanningWithoutAuthentication() {
        var payload = """
            {
                "planningStatus": "IN_PROGRESS"
            }
            """;

        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    // POST /api/releases/{targetReleaseCode}/features/{featureCode}/move

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldMoveFeatureBetweenReleases() {
        var payload =
                """
            {
                "rationale": "Feature is more relevant to the newer release"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases/{targetReleaseCode}/features/{featureCode}/move", "IDEA-2024.2.3", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify the feature is now in the target release
        var featureResult = mvc.get().uri("/api/features/{code}", "IDEA-1").exchange();
        assertThat(featureResult)
                .hasStatusOk()
                .bodyJson()
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.releaseCode()).isEqualTo("IDEA-2024.2.3");
                    assertThat(dto.notes()).contains("Moved to release IDEA-2024.2.3");
                    assertThat(dto.notes()).contains("Feature is more relevant to the newer release");
                });
    }

    @Test
    void shouldReturn401WhenMovingFeatureWithoutAuthentication() {
        var payload = """
            {
                "rationale": "Rationale"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases/{targetReleaseCode}/features/{featureCode}/move", "IDEA-2024.2.3", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    // DELETE /api/releases/{releaseCode}/features/{featureCode}

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldRemoveFeatureFromRelease() {
        var payload =
                """
            {
                "rationale": "Feature is no longer needed in this release"
            }
            """;

        var result = mvc.delete()
                .uri("/api/releases/{releaseCode}/features/{featureCode}", "IDEA-2023.3.8", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify the feature no longer belongs to the release
        var featureResult = mvc.get().uri("/api/features/{code}", "IDEA-2").exchange();
        assertThat(featureResult)
                .hasStatusOk()
                .bodyJson()
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.releaseCode()).isNull();
                    assertThat(dto.notes()).contains("Removed from release IDEA-2023.3.8");
                    assertThat(dto.notes()).contains("Feature is no longer needed in this release");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400WhenRemovingFeatureNotInSpecifiedRelease() {
        var payload = """
            {
                "rationale": "Some rationale"
            }
            """;

        // GO-3 is not in IDEA-2023.3.8
        var result = mvc.delete()
                .uri("/api/releases/{releaseCode}/features/{featureCode}", "IDEA-2023.3.8", "GO-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn401WhenRemovingFeatureWithoutAuthentication() {
        var result = mvc.delete()
                .uri("/api/releases/{releaseCode}/features/{featureCode}", "IDEA-2023.3.8", "IDEA-1")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}

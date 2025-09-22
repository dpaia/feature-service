package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class FeatureControllerTests extends AbstractIT {

    @Test
    void shouldGetFeaturesByReleaseCode() {
        var result = mvc.get()
                .uri("/api/features?releaseCode={code}", "IDEA-2023.3.8")
                .exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(2);
    }

    @Test
    void shouldGetFeatureByCode() {
        String code = "IDEA-1";
        var result = mvc.get().uri("/api/features/{code}", code).exchange();
        assertThat(result).hasStatusOk().bodyJson().convertTo(FeatureDto.class).satisfies(dto -> {
            assertThat(dto.code()).isEqualTo(code);
        });
    }

    @Test
    void shouldReturn404WhenFeatureNotFound() {
        var result = mvc.get().uri("/api/features/{code}", "INVALID_CODE").exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateNewFeature() {
        var payload =
                """
            {
                "productCode": "intellij",
                "releaseCode": "IDEA-2023.3.8",
                "title": "New Feature",
                "description": "New feature description",
                "assignedTo": "john.doe"
            }
            """;

        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String location = result.getMvcResult().getResponse().getHeader("Location");

        // Verify creation
        assertThat(location).isNotNull();
        var code = location.substring(location.lastIndexOf("/") + 1);

        var getResult = mvc.get().uri(location).exchange();
        assertThat(getResult)
                .hasStatusOk()
                .bodyJson()
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.code()).isEqualTo(code);
                    assertThat(dto.title()).isEqualTo("New Feature");
                    assertThat(dto.description()).isEqualTo("New feature description");
                    assertThat(dto.assignedTo()).isEqualTo("john.doe");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldUpdateFeature() {
        var payload =
                """
            {
                "title": "Updated Feature",
                "description": "Updated description",
                "assignedTo": "jane.doe",
                "status": "IN_PROGRESS"
            }
            """;

        var result = mvc.put()
                .uri("/api/features/{code}", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify the update
        var updatedFeature = mvc.get().uri("/api/features/{code}", "IDEA-1").exchange();
        assertThat(updatedFeature)
                .hasStatusOk()
                .bodyJson()
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.title()).isEqualTo("Updated Feature");
                    assertThat(dto.description()).isEqualTo("Updated description");
                    assertThat(dto.assignedTo()).isEqualTo("jane.doe");
                    assertThat(dto.status()).isEqualTo(FeatureStatus.IN_PROGRESS);
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldDeleteFeature() {
        var result = mvc.delete().uri("/api/features/{code}", "IDEA-2").exchange();
        assertThat(result).hasStatusOk();

        // Verify deletion
        var getResult = mvc.get().uri("/api/features/{code}", "IDEA-2").exchange();
        assertThat(getResult).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateFeatureDependency() {
        var payload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "HARD",
                "notes": "Critical dependency"
            }
            """;

        var result = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldUpdateFeatureDependency() {
        // First create a dependency
        var createPayload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "SOFT",
                "notes": "Initial notes"
            }
            """;
        mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();

        // Then update it
        var updatePayload =
                """
            {
                "dependencyType": "HARD",
                "notes": "Updated notes"
            }
            """;

        var result = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldDeleteFeatureDependency() {
        // First create a dependency
        var createPayload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "HARD",
                "notes": "Test dependency"
            }
            """;
        mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();

        // Then delete it
        var result = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .exchange();
        assertThat(result).hasStatusOk();
    }
}

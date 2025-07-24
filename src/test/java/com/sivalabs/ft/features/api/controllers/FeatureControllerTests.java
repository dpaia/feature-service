package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class FeatureControllerTests extends AbstractIT {

    @Autowired
    private ObjectMapper objectMapper;

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
    void shouldGetFeaturesByTags() {
        var result = mvc.get().uri("/api/features?tagIds={tagIds}", "1,2").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(InstanceOfAssertFactories.list(FeatureDto.class))
                .satisfies(features -> {
                    assertThat(features.size()).isEqualTo(3);
                    assertThat(features.stream().map(FeatureDto::code).toList())
                            .containsExactly("IDEA-1", "IDEA-2", "GO-3");
                });
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
    void shouldAssignTagsToMultipleFeatures() {
        var payload =
                """
            {
                "featureCodes": ["IDEA-1", "IDEA-2"],
                "tagIds": [1, 3]
            }
            """;

        var result = mvc.post()
                .uri("/api/features/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify tags are assigned to IDEA-1
        var feature1 = getFeature("IDEA-1");
        assertThat(feature1.tags().stream().filter(tagDto -> tagDto.id() == 1).count())
                .isEqualTo(1);
        // Verify tag 3 is assigned
        assertThat(feature1.tags().stream().filter(tagDto -> tagDto.id() == 3).count())
                .isEqualTo(1);

        // Verify tags are assigned to IDEA-2
        var feature2 = getFeature("IDEA-2");
        assertThat(feature2.tags().stream().filter(tagDto -> tagDto.id() == 1).count())
                .isEqualTo(1);
        // Verify tag 3 is assigned
        assertThat(feature2.tags().stream().filter(tagDto -> tagDto.id() == 3).count())
                .isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenAssigningTagsToNonExistentFeatures() {
        var payload =
                """
            {
                "featureCodes": ["IDEA-1", "NON-EXISTENT"],
                "tagIds": [1, 3]
            }
            """;

        var result = mvc.post()
                .uri("/api/features/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldRemoveTagsFromMultipleFeatures() {
        var payload =
                """
            {
                "featureCodes": ["IDEA-1", "IDEA-2"],
                "tagIds": [1, 2]
            }
            """;

        var result = mvc.delete()
                .uri("/api/features/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify tags are removed from IDEA-1
        var feature1 = getFeature("IDEA-1");
        assertThat(feature1.tags().stream()
                        .filter(tagDto -> tagDto.id() == 1 || tagDto.id() == 2)
                        .count())
                .isEqualTo(0);

        // Verify tags are removed to IDEA-2
        var feature2 = getFeature("IDEA-2");
        assertThat(feature2.tags().stream()
                        .filter(tagDto -> tagDto.id() == 1 || tagDto.id() == 2)
                        .count())
                .isEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldAssignCategoryToFeatures() {
        var payload =
                """
            {
                "featureCodes": ["IDEA-1", "GO-3"],
                "categoryId": 4
            }
            """;

        var result = mvc.post()
                .uri("/api/features/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        var feature1 = getFeature("IDEA-1");
        assertThat(feature1.category().id()).isEqualTo(4);

        var feature2 = getFeature("GO-3");
        assertThat(feature2.category().id()).isEqualTo(4);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenAssigningNonExistentCategory() {
        var payload =
                """
            {
                "featureCodes": ["IDEA-1", "GO-3"],
                "categoryId": 999
            }
            """;

        var result = mvc.post()
                .uri("/api/features/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenAssigningCategoryToNonExistentFeature() {
        var payload =
                """
            {
                "featureCodes": ["IDEA-999"],
                "categoryId": 4
            }
            """;

        var result = mvc.post()
                .uri("/api/features/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldRemoveCategoryFromFeatures() {
        // Verify category is assigned
        var feature1Before = getFeature("IDEA-1");
        assertThat(feature1Before.category()).isNotNull();
        var feature2Before = getFeature("GO-3");
        assertThat(feature2Before.category()).isNotNull();

        // Now remove the category
        var removePayload =
                """
            {
                "featureCodes": ["IDEA-1", "GO-3"]
            }
            """;

        var result = mvc.delete()
                .uri("/api/features/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content(removePayload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify category is removed from features
        var feature1After = getFeature("IDEA-1");
        assertThat(feature1After.category()).isNull();

        var feature2After = getFeature("GO-3");
        assertThat(feature2After.category()).isNull();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenRemovingCategoryFromNonExistentFeature() {
        var payload = """
            {
                "featureCodes": ["IDEA-999"]
            }
            """;

        var result = mvc.delete()
                .uri("/api/features/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    private FeatureDto getFeature(String featureCode) {
        try {
            var json = mvc.get()
                    .uri("/api/features/{code}", featureCode)
                    .exchange()
                    .getResponse()
                    .getContentAsString();
            return objectMapper.readValue(json, FeatureDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

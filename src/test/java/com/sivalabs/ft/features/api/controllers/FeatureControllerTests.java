package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
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
    void shouldUpdateFeatureWithPlanningFields() {
        var payload =
                """
            {
                "title": "Feature With Planning",
                "description": "Updated with planning data",
                "assignedTo": "planner@example.com",
                "status": "IN_PROGRESS",
                "plannedCompletionDate": "2025-12-31",
                "featurePlanningStatus": "IN_PROGRESS",
                "featureOwner": "owner@example.com"
            }
            """;

        var result = mvc.put()
                .uri("/api/features/{code}", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify the planning fields are saved
        var updatedFeature = mvc.get().uri("/api/features/{code}", "IDEA-1").exchange();
        assertThat(updatedFeature)
                .hasStatusOk()
                .bodyJson()
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.title()).isEqualTo("Feature With Planning");
                    assertThat(dto.plannedCompletionDate()).hasToString("2025-12-31");
                    assertThat(dto.featurePlanningStatus()).isEqualTo(FeaturePlanningStatus.IN_PROGRESS);
                    assertThat(dto.featureOwner()).isEqualTo("owner@example.com");
                    assertThat(dto.blockageReason()).isNull();
                    assertThat(dto.actualCompletionDate()).isNull();
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldUpdateFeatureWithBlockedStatus() {
        var payload =
                """
            {
                "title": "Blocked Feature",
                "description": "Feature blocked by dependencies",
                "status": "ON_HOLD",
                "featurePlanningStatus": "BLOCKED",
                "blockageReason": "Waiting for API approval",
                "featureOwner": "blocked.owner@example.com",
                "plannedCompletionDate": "2025-11-30"
            }
            """;

        var result = mvc.put()
                .uri("/api/features/{code}", "IDEA-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify blocked status and reason
        var updatedFeature = mvc.get().uri("/api/features/{code}", "IDEA-3").exchange();
        assertThat(updatedFeature)
                .hasStatusOk()
                .bodyJson()
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.featurePlanningStatus()).isEqualTo(FeaturePlanningStatus.BLOCKED);
                    assertThat(dto.blockageReason()).isEqualTo("Waiting for API approval");
                    assertThat(dto.featureOwner()).isEqualTo("blocked.owner@example.com");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldUpdateFeatureWithDoneStatusAndActualDate() {
        var payload =
                """
            {
                "title": "Completed Feature",
                "description": "Feature completed successfully",
                "status": "RELEASED",
                "featurePlanningStatus": "DONE",
                "plannedCompletionDate": "2025-10-31",
                "actualCompletionDate": "2025-10-11",
                "featureOwner": "done.owner@example.com"
            }
            """;

        var result = mvc.put()
                .uri("/api/features/{code}", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify completion status and dates
        var updatedFeature = mvc.get().uri("/api/features/{code}", "IDEA-1").exchange();
        assertThat(updatedFeature)
                .hasStatusOk()
                .bodyJson()
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.featurePlanningStatus()).isEqualTo(FeaturePlanningStatus.DONE);
                    assertThat(dto.actualCompletionDate()).hasToString("2025-10-11");
                    assertThat(dto.plannedCompletionDate()).hasToString("2025-10-31");
                    assertThat(dto.featureOwner()).isEqualTo("done.owner@example.com");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldUpdateFeatureWithNotStartedStatus() {
        var payload =
                """
            {
                "title": "Future Feature",
                "description": "Feature not yet started",
                "status": "NEW",
                "featurePlanningStatus": "NOT_STARTED",
                "plannedCompletionDate": "2026-01-15",
                "featureOwner": "future.owner@example.com"
            }
            """;

        var result = mvc.put()
                .uri("/api/features/{code}", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify not started status
        var updatedFeature = mvc.get().uri("/api/features/{code}", "IDEA-2").exchange();
        assertThat(updatedFeature)
                .hasStatusOk()
                .bodyJson()
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.featurePlanningStatus()).isEqualTo(FeaturePlanningStatus.NOT_STARTED);
                    assertThat(dto.plannedCompletionDate()).hasToString("2026-01-15");
                    assertThat(dto.actualCompletionDate()).isNull();
                    assertThat(dto.featureOwner()).isEqualTo("future.owner@example.com");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldAllowNullPlanningFieldsInUpdate() {
        var payload =
                """
            {
                "title": "Feature Without Planning",
                "description": "Feature without planning data",
                "status": "NEW"
            }
            """;

        var result = mvc.put()
                .uri("/api/features/{code}", "IDEA-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify planning fields remain null
        var updatedFeature = mvc.get().uri("/api/features/{code}", "IDEA-3").exchange();
        assertThat(updatedFeature)
                .hasStatusOk()
                .bodyJson()
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.title()).isEqualTo("Feature Without Planning");
                    assertThat(dto.plannedCompletionDate()).isNull();
                    assertThat(dto.actualCompletionDate()).isNull();
                    assertThat(dto.featurePlanningStatus()).isNull();
                    assertThat(dto.featureOwner()).isNull();
                    assertThat(dto.blockageReason()).isNull();
                });
    }

    @Test
    void shouldGetFeatureWithPlanningFieldsInResponse() {
        // Assuming IDEA-1 has been updated with planning data in previous tests
        var result = mvc.get().uri("/api/features/{code}", "IDEA-1").exchange();
        assertThat(result).hasStatusOk().bodyJson().convertTo(FeatureDto.class).satisfies(dto -> {
            assertThat(dto.code()).isEqualTo("IDEA-1");
            // Verify DTO structure includes planning fields (they may be null)
            assertThat(dto).hasFieldOrProperty("plannedCompletionDate");
            assertThat(dto).hasFieldOrProperty("actualCompletionDate");
            assertThat(dto).hasFieldOrProperty("featurePlanningStatus");
            assertThat(dto).hasFieldOrProperty("featureOwner");
            assertThat(dto).hasFieldOrProperty("blockageReason");
        });
    }
}

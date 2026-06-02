package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.FeatureStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class FeatureControllerTests extends AbstractIT {

    @Test
    void shouldGetFeaturesByReleaseCode() {
        var result = mvc.get().uri("/api/features?releaseCode={code}", "IJ-2023.3.8");
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(2);
    }

    @Test
    void shouldGetFeatureByCode() {
        String code = "IJ-10001";
        var result = mvc.get().uri("/api/features/{code}", code);
        assertThat(result).hasStatusOk().bodyJson().convertTo(FeatureDto.class).satisfies(dto -> {
            assertThat(dto.code()).isEqualTo(code);
        });
    }

    @Test
    void shouldReturn404WhenFeatureNotFound() {
        var result = mvc.get().uri("/api/features/{code}", "INVALID_CODE");
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateNewFeature() {
        var payload =
                """
            {
                "productCode": "intellij",
                "releaseCode": "IJ-2023.3.8",
                "code": "IJ-999999",
                "title": "New Feature",
                "description": "New feature description",
                "assignedTo": "john.doe"
            }
            """;

        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload);
        assertThat(result).hasStatus(HttpStatus.CREATED);

        // Verify creation
        var getResult = mvc.get().uri("/api/features/{code}", "IJ-999999");
        assertThat(getResult)
                .hasStatusOk()
                .bodyJson()
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.code()).isEqualTo("IJ-999999");
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
                .uri("/api/features/{code}", "IJ-10001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload);
        assertThat(result).hasStatusOk();

        // Verify the update
        var updatedFeature = mvc.get().uri("/api/features/{code}", "IJ-10001");
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
        var result = mvc.delete().uri("/api/features/{code}", "IJ-10002");
        assertThat(result).hasStatusOk();

        // Verify deletion
        var getResult = mvc.get().uri("/api/features/{code}", "IJ-10002");
        assertThat(getResult).hasStatus(HttpStatus.NOT_FOUND);
    }
}

package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ReleaseControllerTests extends AbstractIT {

    @Test
    void shouldGetReleasesByProductCode() {
        var result =
                mvc.get().uri("/api/releases?productCode={code}", "intellij").exchange();
        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content")
                .asArray()
                .anySatisfy(item -> assertThat(item.toString()).contains("code", "IDEA"));
    }

    @Test
    void shouldGetReleaseByCode() {
        String code = "IDEA-2023.3.8";
        var result = mvc.get().uri("/api/releases/{code}", code).exchange();
        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(ReleaseDto.class)
                .satisfies(dto -> {
                    assertThat(dto.code()).isEqualTo(code);
                });
    }

    @Test
    void shouldReturn404WhenReleaseNotFound() {
        var result = mvc.get().uri("/api/releases/{code}", "INVALID_CODE").exchange();
        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateNewRelease() {
        var payload =
                """
            {
                "productCode": "intellij",
                "code": "IDEA-2025.1",
                "description": "IntelliJ IDEA 2025.1"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldUpdateRelease() {
        var payload =
                """
            {
                "description": "Updated description",
                "status": "PLANNED",
                "releasedAt": "2023-12-01T10:00:00Z"
            }
            """;

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Verify the update
        var updatedRelease =
                mvc.get().uri("/api/releases/{code}", "IDEA-2023.3.8").exchange();
        assertThat(updatedRelease)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(ReleaseDto.class)
                .satisfies(dto -> {
                    assertThat(dto.description()).isEqualTo("Updated description");
                    assertThat(dto.status()).isEqualTo(ReleaseStatus.PLANNED);
                    assertThat(dto.releasedAt()).isEqualTo(java.time.Instant.parse("2023-12-01T10:00:00Z"));
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldDeleteRelease() {
        var result = mvc.delete().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Verify deletion
        var getResult = mvc.get().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
        assertThat(getResult).hasStatus4xxClientError();
    }

    // Tests for new advanced query endpoints
    @Test
    void shouldGetOverdueReleases() {
        var result = mvc.get().uri("/api/releases/overdue").exchange();
        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content")
                .asArray()
                .anySatisfy(item -> assertThat(item.toString()).contains("code", "status"));
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldGetAtRiskReleases() {
        // Create releases with different risk levels
        // Release 1: At risk (30 days away, within 365 threshold)
        var atRiskPayload =
                """
            {
                "productCode": "intellij",
                "code": "ATRISK-30-DAYS",
                "description": "At-risk release in 30 days",
                "plannedReleaseDate": "2025-12-31T00:00:00Z",
                "releaseOwner": "risk.manager"
            }
            """;

        // Release 2: Not at risk (400 days away, beyond 365 threshold)
        var safePayload =
                """
            {
                "productCode": "intellij",
                "code": "SAFE-400-DAYS",
                "description": "Safe release in 400+ days",
                "plannedReleaseDate": "2027-01-05T00:00:00Z",
                "releaseOwner": "safe.manager"
            }
            """;

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(atRiskPayload)
                .exchange();

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(safePayload)
                .exchange();

        // Query at-risk releases with 365-day threshold
        var result = mvc.get().uri("/api/releases/at-risk?daysThreshold=365").exchange();
        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content")
                .asArray()
                .anySatisfy(item -> assertThat(item.toString())
                        .contains("code=IDEA-ATRISK-30-DAYS")
                        .contains("plannedReleaseDate=2025-12-31T00:00:00Z"))
                .noneSatisfy(item -> assertThat(item.toString())
                        .contains("IDEA-SAFE-400-DAYS")); // Should not include the 400-day release
    }

    @Test
    void shouldGetReleasesByStatus() {
        var result = mvc.get().uri("/api/releases/by-status?status=DRAFT").exchange();
        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content")
                .asArray()
                .allMatch(item -> item.toString().contains("DRAFT"));
    }

    @Test
    void shouldRejectInvalidStatus() {
        var result =
                mvc.get().uri("/api/releases/by-status?status=INVALID_STATUS").exchange();
        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    void shouldGetReleasesByOwner() {
        var result = mvc.get().uri("/api/releases/by-owner?owner=john.doe").exchange();
        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content")
                .asArray()
                .allMatch(item -> item.toString().contains("john.doe"));
    }

    @Test
    void shouldGetReleasesByDateRange() {
        var startDate = "2024-01-01T00:00:00Z";
        var endDate = "2024-12-31T23:59:59Z";
        var result = mvc.get()
                .uri("/api/releases/by-date-range?startDate={start}&endDate={end}", startDate, endDate)
                .exchange();
        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content")
                .asArray()
                .anySatisfy(item -> assertThat(item.toString()).contains("code", "plannedReleaseDate"));
    }

    @Test
    void shouldRejectInvalidDateRange() {
        var startDate = "2024-12-31T23:59:59Z";
        var endDate = "2024-01-01T00:00:00Z";
        var result = mvc.get()
                .uri("/api/releases/by-date-range?startDate={start}&endDate={end}", startDate, endDate)
                .exchange();
        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    void shouldSupportEnhancedFiltersWithPagination() {
        var result = mvc.get()
                .uri("/api/releases?productCode=intellij&status=DRAFT&page=0&size=10")
                .exchange();
        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content")
                .asArray()
                .allMatch(item ->
                        item.toString().contains("IDEA") && item.toString().contains("DRAFT"));
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldRejectInvalidStatusTransition() {
        // Try to transition from DRAFT to COMPLETED directly (invalid)
        var payload =
                """
            {
                "description": "Updated description",
                "status": "COMPLETED"
            }
            """;

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus4xxClientError();
    }
}

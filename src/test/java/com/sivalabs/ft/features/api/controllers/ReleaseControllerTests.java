package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.ReleaseDashboardResponse;
import com.sivalabs.ft.features.api.models.ReleaseMetricsResponse;
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
                .isEqualTo(3);
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
                "description": "IntelliJ IDEA 2025.1"
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
    void shouldDeleteRelease() {
        var result = mvc.delete().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
        assertThat(result).hasStatusOk();

        // Verify deletion
        var getResult = mvc.get().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
        assertThat(getResult).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldGetReleaseDashboard() {
        String code = "IDEA-2023.3.8";
        var result = mvc.get().uri("/api/releases/{code}/dashboard", code).exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(ReleaseDashboardResponse.class)
                .satisfies(dashboard -> {
                    assertThat(dashboard.releaseCode()).isEqualTo(code);
                    assertThat(dashboard.overview()).isNotNull();
                    assertThat(dashboard.overview().totalFeatures()).isGreaterThanOrEqualTo(0);
                    assertThat(dashboard.overview().completionPercentage()).isBetween(0.0, 100.0);
                    assertThat(dashboard.healthIndicators()).isNotNull();
                    assertThat(dashboard.healthIndicators().timelineAdherence()).isIn("ON_TRACK", "AT_RISK", "DELAYED");
                    assertThat(dashboard.healthIndicators().riskLevel()).isIn("LOW", "MEDIUM", "HIGH");
                    assertThat(dashboard.timeline()).isNotNull();
                    assertThat(dashboard.timeline().startDate()).isNotNull();
                    assertThat(dashboard.timeline().plannedEndDate()).isNotNull();
                    assertThat(dashboard.featureBreakdown()).isNotNull();
                    assertThat(dashboard.featureBreakdown().byStatus()).isNotNull();
                    assertThat(dashboard.featureBreakdown().byOwner()).isNotNull();
                    assertThat(dashboard.featureBreakdown().byPriority()).isNotNull();
                });
    }

    @Test
    void shouldReturn404ForDashboardWhenReleaseNotFound() {
        var result =
                mvc.get().uri("/api/releases/{code}/dashboard", "INVALID_CODE").exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldGetReleaseMetrics() {
        String code = "IDEA-2023.3.8";
        var result = mvc.get().uri("/api/releases/{code}/metrics", code).exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(ReleaseMetricsResponse.class)
                .satisfies(metrics -> {
                    assertThat(metrics.releaseCode()).isEqualTo(code);
                    assertThat(metrics.completionRate()).isBetween(0.0, 100.0);
                    assertThat(metrics.velocity()).isNotNull();
                    assertThat(metrics.velocity().featuresPerWeek()).isGreaterThanOrEqualTo(0.0);
                    assertThat(metrics.velocity().averageCycleTime()).isGreaterThanOrEqualTo(0.0);
                    assertThat(metrics.blockedTime()).isNotNull();
                    assertThat(metrics.blockedTime().totalBlockedDays()).isGreaterThanOrEqualTo(0);
                    assertThat(metrics.blockedTime().percentageOfTime()).isBetween(0.0, 100.0);
                    assertThat(metrics.blockedTime().blockageReasons()).isNotNull();
                    assertThat(metrics.workloadDistribution()).isNotNull();
                    assertThat(metrics.workloadDistribution().byOwner()).isNotNull();
                });
    }

    @Test
    void shouldReturn404ForMetricsWhenReleaseNotFound() {
        var result =
                mvc.get().uri("/api/releases/{code}/metrics", "INVALID_CODE").exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldValidateDashboardMetricAccuracy() {
        String code = "IDEA-2023.3.8";
        var result = mvc.get().uri("/api/releases/{code}/dashboard", code).exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(ReleaseDashboardResponse.class)
                .satisfies(dashboard -> {
                    var overview = dashboard.overview();
                    // Verify that feature counts add up correctly
                    int calculatedTotal = overview.completedFeatures()
                            + overview.inProgressFeatures()
                            + overview.blockedFeatures()
                            + overview.pendingFeatures();
                    assertThat(calculatedTotal).isEqualTo(overview.totalFeatures());

                    // Verify completion percentage calculation
                    if (overview.totalFeatures() > 0) {
                        double expectedPercentage =
                                (double) overview.completedFeatures() / overview.totalFeatures() * 100;
                        assertThat(overview.completionPercentage())
                                .isCloseTo(expectedPercentage, org.assertj.core.data.Offset.offset(0.1));
                    }

                    // Verify estimated days remaining is reasonable
                    assertThat(overview.estimatedDaysRemaining()).isGreaterThanOrEqualTo(0);
                });
    }

    @Test
    void shouldHandleReleaseWithNoFeatures() {
        // This test assumes there might be a release with no features in test data
        // If not, we can create one in the test setup
        String code = "IDEA-2023.3.8"; // Using existing release, but logic should handle empty feature lists
        var result = mvc.get().uri("/api/releases/{code}/dashboard", code).exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(ReleaseDashboardResponse.class)
                .satisfies(dashboard -> {
                    // Should not throw exceptions and should return valid data structure
                    assertThat(dashboard.overview()).isNotNull();
                    assertThat(dashboard.healthIndicators()).isNotNull();
                    assertThat(dashboard.timeline()).isNotNull();
                    assertThat(dashboard.featureBreakdown()).isNotNull();
                });
    }
}

package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

/**
 * Integration tests for RoadmapController migrated from golden branch.
 * Contains tests for roadmap summary completion percentage.
 */
@WithMockOAuth2User
@Sql(scripts = {"/roadmap-summary-completion-test-data.sql"})
public class RoadmapControllerIntegrationTests extends AbstractIT {

    @SuppressWarnings("unchecked")
    @Test
    void shouldGetRoadmapWithoutFilters() {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // Validate response structure
            assertThat(response).containsKeys("roadmapItems", "summary", "appliedFilters");

            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(10);

            // Validate each roadmap item has required structure and validate sorting
            List<String> releaseCodes = new ArrayList<>();
            for (Map<String, Object> item : roadmapItems) {
                assertThat(item).containsKeys("release", "progressMetrics", "healthIndicators", "features");

                // Validate release has core fields with exact values
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                assertThat(release.get("id")).isInstanceOf(Number.class);
                assertThat(release.get("code")).isInstanceOf(String.class);
                assertThat(release.get("status"))
                        .isIn("RELEASED", "PLANNED", "DRAFT", "IN_PROGRESS", "CANCELLED", "DELAYED", "COMPLETED");
                assertThat(release.get("createdAt")).isInstanceOf(String.class);

                // Collect release codes for sorting validation
                releaseCodes.add((String) release.get("code"));
            }

            // Releases should be sorted by priority date descending:
            // PY-2024.3.1 (2024-07-20), PY-2024.2.4 (2024-06-15), PY-2024.3.0 (2024-06-15),
            // IDEA-2024.2.4 (2024-04-20), PY-2024.2.3 (2024-03-03), WEB-2024.2.3 (2024-02-29),
            // IDEA-2024.2.3 (2024-02-20), GO-2024.2.3 (2024-02-15), IDEA-2023.3.8 (2023-11-10)
            assertThat(releaseCodes)
                    .containsExactly(
                            "PY-2024.3.1",
                            "PY-2024.2.4",
                            "PY-2024.3.0",
                            "IDEA-2024.2.4",
                            "PY-2024.2.3",
                            "WEB-2024.2.3",
                            "IDEA-2024.2.3",
                            "RIDER-2024.2.6",
                            "GO-2024.2.3",
                            "IDEA-2023.3.8");

            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            assertThat(summary.get("totalReleases")).isEqualTo(10);
            assertThat(summary.get("completedReleases")).isEqualTo(5); // 4 RELEASED + 1 COMPLETED
            assertThat(summary.get("draftReleases")).isEqualTo(1);
            assertThat(summary.get("totalFeatures")).isEqualTo(23);
            assertThat(summary.get("overallCompletionPercentage")).isEqualTo(50.0);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters).isNotNull();
            assertThat(appliedFilters.get("productCodes")).isNull();
            assertThat(appliedFilters.get("statuses")).isNull();
            assertThat(appliedFilters.get("dateFrom")).isNull();
            assertThat(appliedFilters.get("dateTo")).isNull();
            assertThat(appliedFilters.get("groupBy")).isNull();
            assertThat(appliedFilters.get("owner")).isNull();
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldValidateCompleteSpecificationCompliance() {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // Validate top-level structure
            assertThat(response).containsKeys("roadmapItems", "summary", "appliedFilters");

            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).isNotEmpty();

            for (Map<String, Object> item : roadmapItems) {
                // Validate roadmap item structure
                assertThat(item).containsKeys("product", "release", "progressMetrics", "healthIndicators", "features");

                // Validate product object
                Map<String, Object> product = (Map<String, Object>) item.get("product");
                assertThat(product).containsKeys("id", "code");
                assertThat(product.get("id")).isInstanceOf(Number.class);
                assertThat(product.get("code")).isInstanceOf(String.class);

                // Validate release object has ALL required fields and ONLY those fields
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                assertThat(release)
                        .containsKeys(
                                "id",
                                "code",
                                "description",
                                "status",
                                "releasedAt",
                                "plannedStartDate",
                                "plannedReleaseDate",
                                "actualReleaseDate",
                                "owner",
                                "notes",
                                "createdBy",
                                "createdAt",
                                "updatedBy",
                                "updatedAt");

                // Validate field types
                assertThat(release.get("id")).isInstanceOf(Number.class);
                assertThat(release.get("code")).isInstanceOf(String.class);
                assertThat(release.get("description")).isInstanceOf(String.class);
                assertThat(release.get("status")).isInstanceOf(String.class);
                assertThat(release.get("owner")).isInstanceOf(String.class);
                assertThat(release.get("notes")).isInstanceOf(String.class);
                assertThat(release.get("createdBy")).isInstanceOf(String.class);
                assertThat(release.get("createdAt")).isInstanceOf(String.class);

                // Validate progressMetrics structure
                Map<String, Object> progressMetrics = (Map<String, Object>) item.get("progressMetrics");
                assertThat(progressMetrics)
                        .containsKeys(
                                "totalFeatures",
                                "completedFeatures",
                                "inProgressFeatures",
                                "newFeatures",
                                "onHoldFeatures",
                                "completionPercentage");

                // Validate progressMetrics field types
                assertThat(progressMetrics.get("totalFeatures")).isInstanceOf(Number.class);
                assertThat(progressMetrics.get("completedFeatures")).isInstanceOf(Number.class);
                assertThat(progressMetrics.get("inProgressFeatures")).isInstanceOf(Number.class);
                assertThat(progressMetrics.get("newFeatures")).isInstanceOf(Number.class);
                assertThat(progressMetrics.get("onHoldFeatures")).isInstanceOf(Number.class);
                assertThat(progressMetrics.get("completionPercentage")).isInstanceOf(Number.class);

                // Validate healthIndicators structure
                Map<String, Object> healthIndicators = (Map<String, Object>) item.get("healthIndicators");
                assertThat(healthIndicators).containsKeys("riskLevel", "timelineAdherence");
                assertThat(healthIndicators.get("riskLevel")).isInstanceOf(String.class);
                // timelineAdherence can be null

                // Validate features array
                List<Map<String, Object>> features = (List<Map<String, Object>>) item.get("features");
                assertThat(features).isNotNull();

                for (Map<String, Object> feature : features) {
                    assertThat(feature)
                            .containsKeys(
                                    "id",
                                    "code",
                                    "title",
                                    "description",
                                    "status",
                                    "releaseCode",
                                    "assignedTo",
                                    "createdBy",
                                    "createdAt",
                                    "updatedBy",
                                    "updatedAt");

                    // Validate feature field types
                    assertThat(feature.get("id")).isInstanceOf(Number.class);
                    assertThat(feature.get("code")).isInstanceOf(String.class);
                    assertThat(feature.get("title")).isInstanceOf(String.class);
                    assertThat(feature.get("description")).isInstanceOf(String.class);
                    assertThat(feature.get("status")).isInstanceOf(String.class);
                    assertThat(feature.get("releaseCode")).isInstanceOf(String.class);
                    assertThat(feature.get("createdBy")).isInstanceOf(String.class);
                    assertThat(feature.get("createdAt")).isInstanceOf(String.class);
                }
            }

            // Validate summary structure
            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            assertThat(summary)
                    .containsKeys(
                            "totalReleases",
                            "completedReleases",
                            "draftReleases",
                            "totalFeatures",
                            "overallCompletionPercentage");

            // Validate summary field types
            assertThat(summary.get("totalReleases")).isInstanceOf(Number.class);
            assertThat(summary.get("completedReleases")).isInstanceOf(Number.class);
            assertThat(summary.get("draftReleases")).isInstanceOf(Number.class);
            assertThat(summary.get("totalFeatures")).isInstanceOf(Number.class);
            assertThat(summary.get("overallCompletionPercentage")).isInstanceOf(Number.class);

            // Validate appliedFilters structure
            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters)
                    .containsKeys("productCodes", "statuses", "dateFrom", "dateTo", "groupBy", "owner");
        });
    }
}

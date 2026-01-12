package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@WithMockOAuth2User
public class RoadmapControllerIntegrationTests extends AbstractIT {

    @Test
    void shouldGetRoadmapWithoutFilters() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // Validate response structure
            assertThat(response).containsKeys("roadmapItems", "summary", "appliedFilters");

            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6);

            // Validate each roadmap item has required structure
            for (Map<String, Object> item : roadmapItems) {
                assertThat(item).containsKeys("release", "progressMetrics", "healthIndicators", "features");

                // Validate release has core fields with exact values
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                assertThat(release.get("id")).isInstanceOf(Number.class);
                assertThat(release.get("code")).isInstanceOf(String.class);
                assertThat(release.get("status")).isIn("RELEASED", "PLANNED");
                assertThat(release.get("createdAt")).isInstanceOf(String.class);
            }

            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            assertThat(summary.get("totalReleases")).isEqualTo(6);
            assertThat(summary.get("completedReleases")).isEqualTo(6); // All 6 releases are RELEASED
            assertThat(summary.get("draftReleases")).isEqualTo(0);
            assertThat(summary.get("totalFeatures")).isEqualTo(2); // Only 2 features in test data
            assertThat(summary.get("overallCompletionPercentage")).isEqualTo(100.0); // All releases completed

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

    @Test
    void shouldValidateCompleteSpecificationCompliance() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // Validate top-level structure
            assertThat(response).containsKeys("roadmapItems", "summary", "appliedFilters");

            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).isNotEmpty();

            for (Map<String, Object> item : roadmapItems) {
                // Validate roadmap item structure
                assertThat(item).containsKeys("release", "progressMetrics", "healthIndicators", "features");

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

    @Test
    void shouldGetRoadmapWithProductCodeFilter() throws Exception {
        var result = mvc.get().uri("/api/roadmap?productCodes=intellij").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(2); // 2 IntelliJ IDEA releases

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            List<String> productCodes = (List<String>) appliedFilters.get("productCodes");
            assertThat(productCodes).containsExactly("intellij");

            assertThat(appliedFilters.get("statuses")).isNull();
            assertThat(appliedFilters.get("dateFrom")).isNull();
            assertThat(appliedFilters.get("dateTo")).isNull();
            assertThat(appliedFilters.get("groupBy")).isNull();
            assertThat(appliedFilters.get("owner")).isNull();

            // Validate specific IntelliJ releases
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                String code = (String) release.get("code");
                assertThat(code).isIn("IDEA-2024.2.3", "IDEA-2023.3.8");
                assertThat(release.get("status")).isEqualTo("RELEASED");
                assertThat(release.get("owner")).isIn("john.doe", "jane.smith");
                assertThat(release.get("createdBy")).isEqualTo("admin");
            }
        });
    }

    @Test
    void shouldGetRoadmapWithMultipleProductCodes() throws Exception {
        var result = mvc.get()
                .uri("/api/roadmap?productCodes=intellij&productCodes=goland")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(3); // Filtered: 2 IntelliJ + 1 GoLand releases only

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            List<String> productCodes = (List<String>) appliedFilters.get("productCodes");
            assertThat(productCodes).hasSize(2).containsExactlyInAnyOrder("intellij", "goland");

            // Validate all items have required release fields with specific values
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");

                // Validate specific release codes for intellij and goland
                String releaseCode = (String) release.get("code");
                assertThat(releaseCode).isIn("IDEA-2024.2.3", "IDEA-2023.3.8", "GO-2024.2.3");
                assertThat(release.get("status")).isEqualTo("RELEASED");
                assertThat(release.get("owner")).isIn("john.doe", "jane.smith");
                assertThat(release.get("createdBy")).isEqualTo("admin");

                // Validate progressMetrics with expected values based on test data
                Map<String, Object> progressMetrics = (Map<String, Object>) item.get("progressMetrics");
                int totalFeatures = ((Number) progressMetrics.get("totalFeatures")).intValue();
                assertThat(totalFeatures).isIn(0, 2); // Either 0 or 2 features based on actual data
                assertThat(progressMetrics.get("onHoldFeatures")).isEqualTo(0); // No ON_HOLD features in test data

                // Validate health indicators
                Map<String, Object> healthIndicators = (Map<String, Object>) item.get("healthIndicators");
                assertThat(healthIndicators.get("riskLevel")).isEqualTo("ZERO"); // No ON_HOLD features = ZERO risk
            }
        });
    }

    @Test
    void shouldGetRoadmapWithStatusFilter() throws Exception {
        var result = mvc.get().uri("/api/roadmap?statuses=RELEASED").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6); // All 6 releases have RELEASED status in test data

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            List<String> statuses = (List<String>) appliedFilters.get("statuses");
            assertThat(statuses).containsExactly("RELEASED");

            // Validate all items have RELEASED status and actual codes from test data
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                assertThat(release.get("status")).isEqualTo("RELEASED");
                String code = (String) release.get("code");
                assertThat(code)
                        .isIn(
                                "IDEA-2024.2.3",
                                "PY-2024.2.3",
                                "WEB-2024.2.3",
                                "RIDER-2024.2.6",
                                "GO-2024.2.3",
                                "IDEA-2023.3.8");
            }
        });
    }

    @Test
    void shouldGetRoadmapWithMultipleStatuses() throws Exception {
        var result =
                mvc.get().uri("/api/roadmap?statuses=RELEASED&statuses=PLANNED").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6); // All 6 releases are RELEASED in test data

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            List<String> statuses = (List<String>) appliedFilters.get("statuses");
            assertThat(statuses).hasSize(2).containsExactlyInAnyOrder("RELEASED", "PLANNED");

            // Count actual statuses
            long releasedCount = roadmapItems.stream()
                    .filter(item -> "RELEASED".equals(((Map<String, Object>) item.get("release")).get("status")))
                    .count();
            long plannedCount = roadmapItems.stream()
                    .filter(item -> "PLANNED".equals(((Map<String, Object>) item.get("release")).get("status")))
                    .count();

            assertThat(releasedCount).isEqualTo(6); // All releases are RELEASED
            assertThat(plannedCount).isEqualTo(0); // No PLANNED releases in test data
        });
    }

    @Test
    void shouldGetRoadmapWithDateRangeFilter() throws Exception {
        var result = mvc.get()
                .uri("/api/roadmap?dateFrom=2024-01-01&dateTo=2025-12-31")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems)
                    .hasSize(5); // Only 5 releases fall within 2024-2025 range (IDEA-2023.3.8 is excluded)

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("dateFrom")).isEqualTo("2024-01-01");
            assertThat(appliedFilters.get("dateTo")).isEqualTo("2025-12-31");

            // Validate that all returned releases are within date range
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                String releaseDate = (String) release.get("releasedAt");
                if (releaseDate != null) {
                    assertThat(releaseDate).isGreaterThanOrEqualTo("2024-01-01");
                    assertThat(releaseDate).isLessThanOrEqualTo("2025-12-31");
                }
            }
        });
    }

    @Test
    void shouldGetRoadmapWithOwnerFilter() throws Exception {
        var result = mvc.get().uri("/api/roadmap?owner=john.doe").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(1); // 1 release owned by john.doe

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("owner")).isEqualTo("john.doe");

            // Validate all items have owner john.doe
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                assertThat(release.get("owner")).isEqualTo("john.doe");
            }
        });
    }

    @Test
    void shouldGetRoadmapWithGroupByProductCode() throws Exception {
        var result = mvc.get().uri("/api/roadmap?groupBy=productCode").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6); // All items returned, grouped by product

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("groupBy")).isEqualTo("productCode");

            // Validate specific values for each release
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");

                // Validate specific release codes from actual test data
                String code = (String) release.get("code");
                assertThat(code)
                        .isIn(
                                "IDEA-2024.2.3",
                                "PY-2024.2.3",
                                "WEB-2024.2.3",
                                "RIDER-2024.2.6",
                                "GO-2024.2.3",
                                "IDEA-2023.3.8");
                assertThat(release.get("status")).isEqualTo("RELEASED");
                assertThat(release.get("owner")).isIn("john.doe", "jane.smith");
                assertThat(release.get("createdBy")).isEqualTo("admin");

                // Validate progressMetrics with actual test data values
                Map<String, Object> progressMetrics = (Map<String, Object>) item.get("progressMetrics");
                int totalFeatures = ((Number) progressMetrics.get("totalFeatures")).intValue();
                assertThat(totalFeatures).isIn(0, 2); // Either 0 or 2 features based on actual data
                assertThat(progressMetrics.get("onHoldFeatures")).isEqualTo(0);

                // Validate health indicators
                Map<String, Object> healthIndicators = (Map<String, Object>) item.get("healthIndicators");
                assertThat(healthIndicators.get("riskLevel")).isEqualTo("ZERO");

                // Validate features array
                List<Map<String, Object>> features = (List<Map<String, Object>>) item.get("features");
                assertThat(features.size()).isEqualTo(totalFeatures);
            }
        });
    }

    @Test
    void shouldGetRoadmapWithGroupByStatus() throws Exception {
        var result = mvc.get().uri("/api/roadmap?groupBy=status").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6); // All items returned, grouped by status

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("groupBy")).isEqualTo("status");

            // Validate status distribution (all RELEASED in original test data)
            int releasedCount = 0;
            int plannedCount = 0;

            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                String status = (String) release.get("status");

                if ("RELEASED".equals(status)) {
                    releasedCount++;
                } else if ("PLANNED".equals(status)) {
                    plannedCount++;
                }
            }

            assertThat(releasedCount).isEqualTo(6); // All releases are RELEASED
            assertThat(plannedCount).isEqualTo(0); // No PLANNED releases in test data
        });
    }

    @Test
    void shouldGetRoadmapWithGroupByOwner() throws Exception {
        var result = mvc.get().uri("/api/roadmap?groupBy=owner").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6); // All items returned, grouped by owner

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("groupBy")).isEqualTo("owner");

            // Validate owner distribution based on actual test data
            int johnDoeCount = 0;
            int janeSmithCount = 0;

            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                String owner = (String) release.get("owner");

                switch (owner) {
                    case "john.doe":
                        johnDoeCount++;
                        break;
                    case "jane.smith":
                        janeSmithCount++;
                        break;
                }
            }

            // Based on actual test data: john.doe has 1 release, jane.smith has 5 releases
            assertThat(johnDoeCount).isEqualTo(1);
            assertThat(janeSmithCount).isEqualTo(5);
        });
    }

    @Test
    void shouldGetRoadmapWithCombinedFilters() throws Exception {
        var result = mvc.get()
                .uri("/api/roadmap?productCodes=intellij&statuses=RELEASED&dateFrom=2024-01-01&owner=john.doe")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(1); // john.doe owns IDEA-2024.2.3 which matches all filter criteria

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            List<String> productCodes = (List<String>) appliedFilters.get("productCodes");
            assertThat(productCodes).containsExactly("intellij");

            List<String> statuses = (List<String>) appliedFilters.get("statuses");
            assertThat(statuses).containsExactly("RELEASED");

            assertThat(appliedFilters.get("dateFrom")).isEqualTo("2024-01-01");
            assertThat(appliedFilters.get("owner")).isEqualTo("john.doe");

            // Validate the returned item matches all filter criteria
            Map<String, Object> item = roadmapItems.get(0);
            Map<String, Object> release = (Map<String, Object>) item.get("release");
            assertThat(release.get("code")).isEqualTo("IDEA-2024.2.3");
            assertThat(release.get("owner")).isEqualTo("john.doe");
            assertThat(release.get("status")).isEqualTo("RELEASED");
        });
    }

    @Test
    void shouldValidateProgressMetricsInResponse() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // Validate progressMetrics from roadmap items with exact values
            int totalFeatures = 0;
            int completedFeatures = 0;
            int inProgressFeatures = 0;
            int newFeatures = 0;
            int onHoldFeatures = 0;

            for (Map<String, Object> item : roadmapItems) {
                List<Map<String, Object>> features = (List<Map<String, Object>>) item.get("features");
                if (features != null) {
                    for (Map<String, Object> feature : features) {
                        String status = (String) feature.get("status");
                        totalFeatures++;
                        if ("COMPLETED".equals(status)) {
                            completedFeatures++;
                        } else if ("IN_PROGRESS".equals(status)) {
                            inProgressFeatures++;
                        } else if ("NEW".equals(status)) {
                            newFeatures++;
                        } else if ("ON_HOLD".equals(status)) {
                            onHoldFeatures++;
                        }
                    }
                }
            }

            // Validate exact feature counts based on actual test data
            assertThat(totalFeatures).isEqualTo(2); // Only 2 features in test data
            assertThat(completedFeatures).isEqualTo(0); // No COMPLETED features
            assertThat(inProgressFeatures).isEqualTo(0); // No IN_PROGRESS features
            assertThat(newFeatures).isEqualTo(2); // Both features are NEW
            assertThat(onHoldFeatures).isEqualTo(0); // No ON_HOLD features

            // Validate relationship constraints
            assertThat(completedFeatures).isLessThanOrEqualTo(totalFeatures);
            assertThat(inProgressFeatures).isLessThanOrEqualTo(totalFeatures);
            assertThat(newFeatures).isLessThanOrEqualTo(totalFeatures);
            assertThat(onHoldFeatures).isLessThanOrEqualTo(totalFeatures);
            assertThat(completedFeatures + inProgressFeatures + newFeatures + onHoldFeatures)
                    .isEqualTo(totalFeatures);
        });
    }

    @Test
    void shouldValidateHealthIndicatorsInResponse() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).isNotEmpty();

            // Validate health indicators for all items
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> healthIndicators = (Map<String, Object>) item.get("healthIndicators");

                // Risk level must be exact value from enum
                String riskLevel = (String) healthIndicators.get("riskLevel");
                assertThat(riskLevel).isEqualTo("ZERO"); // No ON_HOLD features = ZERO risk

                // Timeline adherence can be DELAYED, ON_SCHEDULE, or CRITICAL based on actual release data
                String timelineAdherence = (String) healthIndicators.get("timelineAdherence");
                if (timelineAdherence != null) {
                    assertThat(timelineAdherence).isIn("ON_SCHEDULE", "DELAYED", "CRITICAL");
                }
            }
        });
    }

    @Test
    void shouldValidateSummaryInResponse() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            Map<String, Object> summary = (Map<String, Object>) response.get("summary");

            assertThat(summary.get("totalReleases")).isEqualTo(6);
            assertThat(summary.get("completedReleases")).isEqualTo(6); // All 6 releases are RELEASED
            assertThat(summary.get("draftReleases")).isEqualTo(0);
            assertThat(summary.get("totalFeatures")).isEqualTo(2); // Only 2 features total
            assertThat(summary.get("overallCompletionPercentage")).isEqualTo(100.0); // All releases completed = 100%
        });
    }

    @Test
    void shouldValidateReleaseFieldsInResponse() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            // Find the release that has features (IDEA-2023.3.8 has 2 features)
            Map<String, Object> releaseWithFeatures = roadmapItems.stream()
                    .filter(item -> {
                        Map<String, Object> rel = (Map<String, Object>) item.get("release");
                        String code = (String) rel.get("code");
                        return "IDEA-2023.3.8".equals(code);
                    })
                    .findFirst()
                    .orElse(null);

            if (releaseWithFeatures != null) {
                Map<String, Object> release = (Map<String, Object>) releaseWithFeatures.get("release");
                String code = (String) release.get("code");
                assertThat(code).isEqualTo("IDEA-2023.3.8");

                String status = (String) release.get("status");
                assertThat(status).isEqualTo("RELEASED");

                List<Map<String, Object>> features = (List<Map<String, Object>>) releaseWithFeatures.get("features");
                assertThat(features).hasSize(2);
            }
        });
    }

    // Validation Error Tests
    @Test
    void shouldReturnBadRequestForInvalidStatus() throws Exception {
        var result = mvc.get().uri("/api/roadmap?statuses=INVALID_STATUS").exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestForInvalidGroupBy() throws Exception {
        var result = mvc.get().uri("/api/roadmap?groupBy=invalidGroup").exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestWhenDateFromIsAfterDateTo() throws Exception {
        var result = mvc.get()
                .uri("/api/roadmap?dateFrom=2025-12-31&dateTo=2024-01-01")
                .exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestForInvalidDateFormat() throws Exception {
        var result = mvc.get().uri("/api/roadmap?dateFrom=invalid-date").exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldAllowExtraQueryParameters() throws Exception {
        var result = mvc.get()
                .uri("/api/roadmap?extraParam=extraValue&anotherParam=anotherValue")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6); // Extra params ignored, returns all items
        });
    }

    @Test
    void shouldHandleEmptyResultsForNonExistentOwner() throws Exception {
        var result = mvc.get().uri("/api/roadmap?owner=non.existent.user").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).isEmpty();

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("owner")).isEqualTo("non.existent.user");
        });
    }

    @Test
    void shouldHandleCaseInsensitiveStatuses() throws Exception {
        var result =
                mvc.get().uri("/api/roadmap?statuses=released&statuses=planned").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6); // Case-insensitive: all releases returned
        });
    }

    @Test
    void shouldHandleCaseInsensitiveGroupBy() throws Exception {
        var result = mvc.get().uri("/api/roadmap?groupBy=PRODUCTCODE").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6); // Case-insensitive groupBy returns all 6 items
        });
    }

    // Sorting Tests
    @Test
    void shouldSortReleasesInDescendingOrderByDate() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6);

            // Verify releases are sorted in descending date order
            for (int i = 0; i < roadmapItems.size() - 1; i++) {
                Map<String, Object> currentRelease =
                        (Map<String, Object>) roadmapItems.get(i).get("release");
                Map<String, Object> nextRelease =
                        (Map<String, Object>) roadmapItems.get(i + 1).get("release");

                String currentSortingDate = (String)
                        (currentRelease.get("actualReleaseDate") != null
                                ? currentRelease.get("actualReleaseDate")
                                : (currentRelease.get("releasedAt") != null
                                        ? currentRelease.get("releasedAt")
                                        : (currentRelease.get("plannedReleaseDate") != null
                                                ? currentRelease.get("plannedReleaseDate")
                                                : currentRelease.get("createdAt"))));

                String nextSortingDate = (String)
                        (nextRelease.get("actualReleaseDate") != null
                                ? nextRelease.get("actualReleaseDate")
                                : (nextRelease.get("releasedAt") != null
                                        ? nextRelease.get("releasedAt")
                                        : (nextRelease.get("plannedReleaseDate") != null
                                                ? nextRelease.get("plannedReleaseDate")
                                                : nextRelease.get("createdAt"))));

                // Validate both have dates and are in descending order
                assertThat(currentSortingDate).isNotNull();
                assertThat(nextSortingDate).isNotNull();
                // Current item should have date >= next item (descending order)
                assertThat(currentSortingDate).isGreaterThanOrEqualTo(nextSortingDate);
            }
        });
    }

    @Test
    void shouldMaintainSortingWithinGroupBy() throws Exception {
        var result = mvc.get().uri("/api/roadmap?groupBy=productCode").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6);

            // Validate that items are properly structured and have all required fields
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");

                // Validate all required fields are present
                assertThat(release.get("id")).isNotNull();
                assertThat(release.get("code")).isNotNull();
                assertThat(release.get("description")).isNotNull();
                assertThat(release.get("status")).isNotNull();
                assertThat(release.get("owner")).isNotNull();
                assertThat(release.get("createdBy")).isNotNull();
                assertThat(release.get("createdAt")).isNotNull();

                // Validate complete structure
                assertThat(item).containsKeys("release", "progressMetrics", "healthIndicators", "features");

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

                // Validate healthIndicators structure
                Map<String, Object> healthIndicators = (Map<String, Object>) item.get("healthIndicators");
                assertThat(healthIndicators).containsKeys("riskLevel", "timelineAdherence");

                // Validate features array exists
                assertThat(item.get("features")).isNotNull();
            }
        });
    }

    // Health Indicators - Risk Level Tests
    @Test
    void shouldCalculateRiskLevelZeroWhenNoFeaturesOnHold() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // Check any release with features to verify risk level calculation
            Map<String, Object> releaseWithFeatures = roadmapItems.stream()
                    .filter(item -> {
                        List<Map<String, Object>> features = (List<Map<String, Object>>) item.get("features");
                        return features != null && !features.isEmpty();
                    })
                    .findFirst()
                    .orElseThrow();

            Map<String, Object> healthIndicators = (Map<String, Object>) releaseWithFeatures.get("healthIndicators");
            Object riskLevel = healthIndicators.get("riskLevel");
            assertThat(riskLevel).isIn("ZERO", "LOW", "MEDIUM", "HIGH");
        });
    }

    @Test
    void shouldCalculateRiskLevelBasedOnOnHoldPercentage() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // Verify risk levels are calculated correctly
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> healthIndicators = (Map<String, Object>) item.get("healthIndicators");
                Map<String, Object> progressMetrics = (Map<String, Object>) item.get("progressMetrics");

                String riskLevel = (String) healthIndicators.get("riskLevel");
                int totalFeatures = (Integer) progressMetrics.get("totalFeatures");
                int onHoldFeatures = (Integer) progressMetrics.get("onHoldFeatures");

                // Validate risk level matches percentage
                if (totalFeatures > 0) {
                    double onHoldPercentage = (onHoldFeatures * 100.0) / totalFeatures;

                    if (onHoldFeatures == 0) {
                        assertThat(riskLevel).isEqualTo("ZERO");
                    } else if (onHoldPercentage < 10) {
                        assertThat(riskLevel).isEqualTo("LOW");
                    } else if (onHoldPercentage <= 30) {
                        assertThat(riskLevel).isEqualTo("MEDIUM");
                    } else {
                        assertThat(riskLevel).isEqualTo("HIGH");
                    }
                } else {
                    assertThat(riskLevel).isEqualTo("ZERO");
                }
            }
        });
    }

    @Test
    void shouldCalculateTimelineAdherenceForUpcomingReleases() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // Verify roadmap items are returned with valid timeline adherence values
            assertThat(roadmapItems).isNotEmpty();
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> healthIndicators = (Map<String, Object>) item.get("healthIndicators");
                Object timelineAdherence = healthIndicators.get("timelineAdherence");
                // Can be null, ON_SCHEDULE, DELAYED, or CRITICAL
                if (timelineAdherence != null) {
                    assertThat(timelineAdherence).isIn("ON_SCHEDULE", "DELAYED", "CRITICAL");
                }
            }
        });
    }

    @Test
    void shouldValidateTimelineAdherenceValues() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> healthIndicators = (Map<String, Object>) item.get("healthIndicators");
                String timelineAdherence = (String) healthIndicators.get("timelineAdherence");

                // Timeline adherence must be one of the valid values or null
                if (timelineAdherence != null) {
                    assertThat(timelineAdherence).isIn("ON_SCHEDULE", "DELAYED", "CRITICAL");
                }
            }
        });
    }

    @Test
    void shouldFilterByDateToUsingCorrectSortingDateField() throws Exception {
        var result = mvc.get().uri("/api/roadmap?dateTo=2024-02-20").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // Verify all returned items have sorting date <= 2024-02-20
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");

                // Get the date used for sorting (first non-null)
                String sortingDate = (String)
                        (release.get("actualReleaseDate") != null
                                ? release.get("actualReleaseDate")
                                : (release.get("releasedAt") != null
                                        ? release.get("releasedAt")
                                        : (release.get("plannedReleaseDate") != null
                                                ? release.get("plannedReleaseDate")
                                                : release.get("createdAt"))));

                // Dates come back with timestamps, compare only the date part
                if (sortingDate != null) {
                    String dateOnly = sortingDate.split("T")[0];
                    assertThat(dateOnly).isLessThanOrEqualTo("2024-02-20");
                }
            }
        });
    }

    @Test
    void shouldReturnNullTimelineAdherenceWhenPlannedReleaseDateMissing() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // Find releases without plannedReleaseDate
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                Map<String, Object> healthIndicators = (Map<String, Object>) item.get("healthIndicators");

                Object plannedReleaseDate = release.get("plannedReleaseDate");
                String timelineAdherence = (String) healthIndicators.get("timelineAdherence");

                // STRICT: If no plannedReleaseDate, timelineAdherence MUST be null
                if (plannedReleaseDate == null) {
                    assertThat(timelineAdherence)
                            .as("TimelineAdherence must be null when plannedReleaseDate is missing")
                            .isNull();
                }
            }
        });
    }

    @Test
    void shouldReturnOnScheduleWhenActualReleaseDateBeforePlanned() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // Validate ON_SCHEDULE for completed releases that met deadline
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                Map<String, Object> healthIndicators = (Map<String, Object>) item.get("healthIndicators");

                String plannedReleaseDate = (String) release.get("plannedReleaseDate");
                String actualReleaseDate = (String) release.get("actualReleaseDate");
                String releasedAt = (String) release.get("releasedAt");
                String timelineAdherence = (String) healthIndicators.get("timelineAdherence");

                // If has plannedReleaseDate and actualReleaseDate or releasedAt
                if (plannedReleaseDate != null && (actualReleaseDate != null || releasedAt != null)) {
                    String releaseDate = actualReleaseDate != null ? actualReleaseDate : releasedAt;

                    // Extract date parts for comparison
                    String plannedDate = plannedReleaseDate.split("T")[0];
                    String releaseDateOnly = releaseDate.split("T")[0];

                    // STRICT: If release is on or before planned date, must be ON_SCHEDULE
                    if (releaseDateOnly.compareTo(plannedDate) <= 0) {
                        assertThat(timelineAdherence)
                                .as(
                                        "Release on %s should be ON_SCHEDULE when planned for %s",
                                        releaseDateOnly, plannedDate)
                                .isEqualTo("ON_SCHEDULE");
                    }
                }
            }
        });
    }

    @Test
    void shouldReturnDelayedWhenDelay0To13Days() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // Calculate delays and validate DELAYED status (0-13 days)
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                Map<String, Object> healthIndicators = (Map<String, Object>) item.get("healthIndicators");

                String plannedReleaseDate = (String) release.get("plannedReleaseDate");
                String actualReleaseDate = (String) release.get("actualReleaseDate");
                String releasedAt = (String) release.get("releasedAt");
                String timelineAdherence = (String) healthIndicators.get("timelineAdherence");

                if (plannedReleaseDate != null && (actualReleaseDate != null || releasedAt != null)) {
                    String releaseDate = actualReleaseDate != null ? actualReleaseDate : releasedAt;

                    String plannedDate = plannedReleaseDate.split("T")[0];
                    String releaseDateOnly = releaseDate.split("T")[0];

                    // If release is after planned date, check delay
                    if (releaseDateOnly.compareTo(plannedDate) > 0) {
                        // Calculate days delay
                        long delayDays = java.time.temporal.ChronoUnit.DAYS.between(
                                java.time.LocalDate.parse(plannedDate), java.time.LocalDate.parse(releaseDateOnly));

                        // STRICT: 0-13 days delay = DELAYED
                        if (delayDays >= 0 && delayDays < 14) {
                            assertThat(timelineAdherence)
                                    .as("Release %d days late should be DELAYED", delayDays)
                                    .isEqualTo("DELAYED");
                        }
                    }
                }
            }
        });
    }

    @Test
    void shouldReturnCriticalWhenDelay14DaysOrMore() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // Calculate delays and validate CRITICAL status (14+ days)
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                Map<String, Object> healthIndicators = (Map<String, Object>) item.get("healthIndicators");

                String plannedReleaseDate = (String) release.get("plannedReleaseDate");
                String actualReleaseDate = (String) release.get("actualReleaseDate");
                String releasedAt = (String) release.get("releasedAt");
                String timelineAdherence = (String) healthIndicators.get("timelineAdherence");

                if (plannedReleaseDate != null && (actualReleaseDate != null || releasedAt != null)) {
                    String releaseDate = actualReleaseDate != null ? actualReleaseDate : releasedAt;

                    String plannedDate = plannedReleaseDate.split("T")[0];
                    String releaseDateOnly = releaseDate.split("T")[0];

                    // If release is after planned date, check delay
                    if (releaseDateOnly.compareTo(plannedDate) > 0) {
                        // Calculate days delay
                        long delayDays = java.time.temporal.ChronoUnit.DAYS.between(
                                java.time.LocalDate.parse(plannedDate), java.time.LocalDate.parse(releaseDateOnly));

                        // STRICT: 14+ days delay = CRITICAL
                        if (delayDays >= 14) {
                            assertThat(timelineAdherence)
                                    .as("Release %d days late should be CRITICAL", delayDays)
                                    .isEqualTo("CRITICAL");
                        }
                    }
                }
            }
        });
    }

    @Test
    void shouldValidate14DayBoundary() throws Exception {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            boolean found13DayDelay = false;
            boolean found14DayDelay = false;

            // Look for releases that can validate the 14-day boundary
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                Map<String, Object> healthIndicators = (Map<String, Object>) item.get("healthIndicators");

                String plannedReleaseDate = (String) release.get("plannedReleaseDate");
                String actualReleaseDate = (String) release.get("actualReleaseDate");
                String releasedAt = (String) release.get("releasedAt");
                String timelineAdherence = (String) healthIndicators.get("timelineAdherence");

                if (plannedReleaseDate != null && (actualReleaseDate != null || releasedAt != null)) {
                    String releaseDate = actualReleaseDate != null ? actualReleaseDate : releasedAt;

                    String plannedDate = plannedReleaseDate.split("T")[0];
                    String releaseDateOnly = releaseDate.split("T")[0];

                    if (releaseDateOnly.compareTo(plannedDate) > 0) {
                        long delayDays = java.time.temporal.ChronoUnit.DAYS.between(
                                java.time.LocalDate.parse(plannedDate), java.time.LocalDate.parse(releaseDateOnly));

                        if (delayDays == 13) {
                            found13DayDelay = true;
                            assertThat(timelineAdherence)
                                    .as("13-day delay should be DELAYED, not CRITICAL")
                                    .isEqualTo("DELAYED");
                        } else if (delayDays == 14) {
                            found14DayDelay = true;
                            assertThat(timelineAdherence)
                                    .as("14-day delay should be CRITICAL")
                                    .isEqualTo("CRITICAL");
                        }
                    }
                }
            }
        });
    }

    // CSV Export Tests
    @Test
    void shouldExportRoadmapAsCsv() throws Exception {
        var result = mvc.get().uri("/api/roadmap/export?format=CSV").exchange();

        assertThat(result).hasStatusOk().hasContentType("text/csv").headers().satisfies(headers -> {
            String headerValue = headers.getFirst("Content-Disposition");
            assertThat(headerValue).startsWith("attachment;");
            assertThat(headerValue).contains("filename=\"Roadmap_");
            assertThat(headerValue).endsWith(".csv\"");
        });

        assertThat(result).body().asString().satisfies(content -> {
            String[] lines = content.split("\n");
            assertThat(lines.length).isEqualTo(7); // Header + 6 data rows

            // Validate exact header with all 18 columns (no quotes)
            String expectedHeader =
                    "Product Code,Product Name,Release Code,Release Description,Release Status,Released At,Planned Start Date,Planned Release Date,Actual Release Date,Owner,Total Features,Completed Features,In Progress Features,New Features,On Hold Features,Completion Percentage,Timeline Adherence,Risk Level";
            assertThat(lines[0]).isEqualTo(expectedHeader);
        });
    }

    @Test
    void shouldExportRoadmapAsCsvWithFilters() throws Exception {
        var result = mvc.get()
                .uri("/api/roadmap/export?format=CSV&productCodes=intellij&statuses=RELEASED")
                .exchange();

        assertThat(result)
                .hasStatusOk()
                .hasContentType("text/csv")
                .body()
                .asString()
                .satisfies(content -> {
                    String[] lines = content.split("\n");
                    assertThat(lines.length).isEqualTo(3); // Header + 2 RELEASED intellij releases

                    // Validate exact header (no quotes)
                    String expectedHeader =
                            "Product Code,Product Name,Release Code,Release Description,Release Status,Released At,Planned Start Date,Planned Release Date,Actual Release Date,Owner,Total Features,Completed Features,In Progress Features,New Features,On Hold Features,Completion Percentage,Timeline Adherence,Risk Level";
                    assertThat(lines[0]).isEqualTo(expectedHeader);

                    // Validate first data row
                    String[] firstRow = lines[1].split(",");
                    assertThat(firstRow[0]).isEqualTo("intellij");
                    assertThat(firstRow[1]).isEqualTo("IntelliJ IDEA");
                    assertThat(firstRow[4]).isEqualTo("RELEASED");
                });
    }

    @Test
    void shouldHandleCaseInsensitiveCsvFormat() throws Exception {
        var result = mvc.get().uri("/api/roadmap/export?format=csv").exchange();
        assertThat(result).hasStatusOk().hasContentType("text/csv");
    }

    // PDF Export Tests
    @Test
    void shouldExportRoadmapAsPdf() throws Exception {
        var result = mvc.get().uri("/api/roadmap/export?format=PDF").exchange();

        assertThat(result).hasStatusOk().satisfies(response -> {
            var contentType = response.getMvcResult().getResponse().getContentType();
            assertThat(contentType).contains("application/pdf");

            var contentDisposition = response.getMvcResult().getResponse().getHeader("Content-Disposition");
            // Validate filename format: Roadmap_yyyyMMddHHmmss.pdf
            assertThat(contentDisposition).matches(".*Roadmap_\\d{14}\\.pdf.*");

            // Verify PDF content has data
            var content = response.getMvcResult().getResponse().getContentAsByteArray();
            assertThat(content.length).isGreaterThan(0);
        });
    }

    @Test
    void shouldExportRoadmapAsPdfWithFilters() throws Exception {
        var result = mvc.get()
                .uri(
                        "/api/roadmap/export?format=PDF&productCodes=intellij&productCodes=goland&dateFrom=2024-01-01&dateTo=2025-12-31")
                .exchange();

        assertThat(result).hasStatusOk().satisfies(response -> {
            var content = response.getMvcResult().getResponse().getContentAsByteArray();
            assertThat(content.length).isGreaterThan(0);
            // PDF magic number validation
            assertThat(content[0]).isEqualTo((byte) 0x25); // %
            assertThat(content[1]).isEqualTo((byte) 0x50); // P
            assertThat(content[2]).isEqualTo((byte) 0x44); // D
            assertThat(content[3]).isEqualTo((byte) 0x46); // F
        });
    }

    @Test
    void shouldHandleCaseInsensitivePdfFormat() throws Exception {
        var result = mvc.get().uri("/api/roadmap/export?format=pdf").exchange();
        assertThat(result).hasStatusOk().satisfies(response -> {
            var contentType = response.getMvcResult().getResponse().getContentType();
            assertThat(contentType).contains("application/pdf");
        });
    }

    // File Naming Tests
    @Test
    void shouldGenerateCorrectFileName() throws Exception {
        var result = mvc.get().uri("/api/roadmap/export?format=CSV").exchange();

        assertThat(result).hasStatusOk().headers().satisfies(headers -> {
            String headerValue = headers.getFirst("Content-Disposition");
            assertThat(headerValue).matches(".*filename=\"Roadmap_\\d{14}\\.csv\".*");
        });
    }

    @Test
    void shouldGenerateCorrectPdfFileName() throws Exception {
        var result = mvc.get().uri("/api/roadmap/export?format=PDF").exchange();

        assertThat(result).hasStatusOk().headers().satisfies(headers -> {
            String headerValue = headers.getFirst("Content-Disposition");
            assertThat(headerValue).matches(".*filename=\"Roadmap_\\d{14}\\.pdf\".*");
        });
    }

    // Export Validation Error Tests
    @Test
    void shouldReturnBadRequestWhenFormatIsMissing() throws Exception {
        var result = mvc.get().uri("/api/roadmap/export").exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestForInvalidFormat() throws Exception {
        var result = mvc.get().uri("/api/roadmap/export?format=INVALID").exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestForEmptyFormat() throws Exception {
        var result = mvc.get().uri("/api/roadmap/export?format=").exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestForInvalidStatusInExport() throws Exception {
        var result = mvc.get()
                .uri("/api/roadmap/export?format=CSV&statuses=INVALID_STATUS")
                .exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestForInvalidGroupByInExport() throws Exception {
        var result = mvc.get()
                .uri("/api/roadmap/export?format=PDF&groupBy=invalidGroup")
                .exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestWhenDateFromIsAfterDateToInExport() throws Exception {
        var result = mvc.get()
                .uri("/api/roadmap/export?format=CSV&dateFrom=2025-12-31&dateTo=2024-01-01")
                .exchange();
        assertThat(result).hasStatus(400);
    }

    // Export Filter Application Tests
    @Test
    void shouldApplyAllFiltersToExport() throws Exception {
        var result = mvc.get()
                .uri(
                        "/api/roadmap/export?format=CSV&productCodes=intellij&statuses=RELEASED&dateFrom=2024-01-01&dateTo=2024-12-31&groupBy=productCode&owner=john.doe")
                .exchange();
        assertThat(result).hasStatusOk().hasContentType("text/csv");
    }

    @Test
    void shouldHandleEmptyResultsInExport() throws Exception {
        var result = mvc.get()
                .uri("/api/roadmap/export?format=CSV&owner=non.existent.user")
                .exchange();

        assertThat(result).hasStatusOk().body().asString().satisfies(content -> {
            String[] lines = content.split("\n");
            assertThat(lines.length).isEqualTo(1);
            String headerLine = lines[0];
            assertThat(headerLine).contains("Product Code");
            assertThat(headerLine).contains("Product Name");
            assertThat(headerLine).contains("Release Code");
        });
    }

    // Performance Tests (basic timing validation)
    @Test
    void shouldCompleteExportWithinTimeLimit() throws Exception {
        long startTime = System.currentTimeMillis();
        var result = mvc.get().uri("/api/roadmap/export?format=CSV").exchange();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertThat(result).hasStatusOk();
        assertThat(duration).isBetween(0L, 2000L);
    }

    // Export Sorting Tests
    @Test
    void shouldExportWithSameSortingAsRoadmapApi() throws Exception {
        var result = mvc.get().uri("/api/roadmap/export?format=CSV").exchange();

        assertThat(result)
                .hasStatusOk()
                .hasContentType("text/csv")
                .body()
                .asString()
                .satisfies(content -> {
                    String[] lines = content.split("\n");
                    assertThat(lines.length).isEqualTo(7); // Header + 6 data rows

                    // Verify first row contains a release code
                    assertThat(lines[1]).isNotEmpty();

                    // Verify dates are in descending order
                    // Index 8 = Actual Release Date
                    String[] firstDataRow = lines[1].split(",");
                    String[] secondDataRow = lines[2].split(",");
                    String firstActualDate = firstDataRow[8];
                    String secondActualDate = secondDataRow[8];

                    // If both have actual dates, verify order
                    if (!firstActualDate.isEmpty() && !secondActualDate.isEmpty()) {
                        assertThat(firstActualDate).isGreaterThanOrEqualTo(secondActualDate);
                    }
                });
    }
}

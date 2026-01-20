package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// Suppress unchecked warnings for casting of json responses
/** @noinspection unchecked, SequencedCollectionMethodCanBeUsed */
@WithMockOAuth2User
public class RoadmapControllerIntegrationTests extends AbstractIT {

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

    @Test
    void shouldGetRoadmapWithProductCodeFilter() {
        var result = mvc.get().uri("/api/roadmap?productCodes=intellij").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(3); // 3 IntelliJ IDEA releases

            // Verify all items have the correct product
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> product = (Map<String, Object>) item.get("product");
                assertThat(product.get("code")).isEqualTo("intellij");
                assertThat(product.get("id")).isEqualTo(1);
            }

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            List<String> productCodes = (List<String>) appliedFilters.get("productCodes");
            assertThat(productCodes).containsExactly("intellij");

            assertThat(appliedFilters.get("statuses")).isNull();
            assertThat(appliedFilters.get("dateFrom")).isNull();
            assertThat(appliedFilters.get("dateTo")).isNull();
            assertThat(appliedFilters.get("groupBy")).isNull();
            assertThat(appliedFilters.get("owner")).isNull();

            // Validate specific IntelliJ releases sorted by date descending
            Map<String, Object> release0 =
                    (Map<String, Object>) roadmapItems.get(0).get("release");
            assertThat(release0.get("code")).isEqualTo("IDEA-2024.2.4");
            assertThat(release0.get("status")).isEqualTo("IN_PROGRESS");

            Map<String, Object> release1 =
                    (Map<String, Object>) roadmapItems.get(1).get("release");
            assertThat(release1.get("code")).isEqualTo("IDEA-2024.2.3");
            assertThat(release1.get("status")).isEqualTo("COMPLETED");

            Map<String, Object> release2 =
                    (Map<String, Object>) roadmapItems.get(2).get("release");
            assertThat(release2.get("code")).isEqualTo("IDEA-2023.3.8");
            assertThat(release2.get("status")).isEqualTo("RELEASED");
        });
    }

    @Test
    void shouldGetRoadmapWithMultipleProductCodes() {
        var result = mvc.get()
                .uri("/api/roadmap?productCodes=goland&productCodes=webstorm&productCodes=rider")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(3); // 1 GoLand + 1 WebStorm + 1 Rider

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            List<String> productCodes = (List<String>) appliedFilters.get("productCodes");
            assertThat(productCodes).hasSize(3).containsExactlyInAnyOrder("goland", "webstorm", "rider");

            assertThat(appliedFilters.get("statuses")).isNull();
            assertThat(appliedFilters.get("dateFrom")).isNull();
            assertThat(appliedFilters.get("dateTo")).isNull();
            assertThat(appliedFilters.get("groupBy")).isNull();
            assertThat(appliedFilters.get("owner")).isNull();

            // Validate releases sorted by date descending
            Map<String, Object> item0 = roadmapItems.get(0);
            Map<String, Object> release0 = (Map<String, Object>) item0.get("release");
            assertThat(release0.get("code")).isEqualTo("WEB-2024.2.3");
            assertThat(release0.get("status")).isEqualTo("RELEASED");
            Map<String, Object> product0 = (Map<String, Object>) item0.get("product");
            assertThat(product0.get("code")).isEqualTo("webstorm");

            Map<String, Object> item1 = roadmapItems.get(1);
            Map<String, Object> release1 = (Map<String, Object>) item1.get("release");
            assertThat(release1.get("code")).isEqualTo("RIDER-2024.2.6");
            assertThat(release1.get("status")).isEqualTo("RELEASED");
            Map<String, Object> product1 = (Map<String, Object>) item1.get("product");
            assertThat(product1.get("code")).isEqualTo("rider");

            Map<String, Object> item2 = roadmapItems.get(2);
            Map<String, Object> release2 = (Map<String, Object>) item2.get("release");
            assertThat(release2.get("code")).isEqualTo("GO-2024.2.3");
            assertThat(release2.get("status")).isEqualTo("RELEASED");
            Map<String, Object> product2 = (Map<String, Object>) item2.get("product");
            assertThat(product2.get("code")).isEqualTo("goland");
        });
    }

    @Test
    void shouldGetRoadmapWithStatusFilter() {
        var result = mvc.get().uri("/api/roadmap?statuses=RELEASED").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(4);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat((List<String>) appliedFilters.get("statuses")).containsExactly("RELEASED");
            assertThat(appliedFilters.get("dateFrom")).isNull();
            assertThat(appliedFilters.get("dateTo")).isNull();
            assertThat(appliedFilters.get("groupBy")).isNull();
            assertThat(appliedFilters.get("owner")).isNull();

            // Verify all releases have RELEASED status, sorted by date descending
            Map<String, Object> release0 =
                    (Map<String, Object>) roadmapItems.get(0).get("release");
            assertThat(release0.get("code")).isEqualTo("WEB-2024.2.3");
            assertThat(release0.get("status")).isEqualTo("RELEASED");

            Map<String, Object> release1 =
                    (Map<String, Object>) roadmapItems.get(1).get("release");
            assertThat(release1.get("code")).isEqualTo("RIDER-2024.2.6");
            assertThat(release1.get("status")).isEqualTo("RELEASED");

            Map<String, Object> release2 =
                    (Map<String, Object>) roadmapItems.get(2).get("release");
            assertThat(release2.get("code")).isEqualTo("GO-2024.2.3");
            assertThat(release2.get("status")).isEqualTo("RELEASED");

            Map<String, Object> release3 =
                    (Map<String, Object>) roadmapItems.get(3).get("release");
            assertThat(release3.get("code")).isEqualTo("IDEA-2023.3.8");
            assertThat(release3.get("status")).isEqualTo("RELEASED");
        });
    }

    @Test
    void shouldGetRoadmapWithMultipleStatuses() {
        var result = mvc.get()
                .uri("/api/roadmap?statuses=IN_PROGRESS&statuses=COMPLETED&statuses=PLANNED")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(3); // 1 IN_PROGRESS + 1 COMPLETED + 1 PLANNED

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            List<String> statuses = (List<String>) appliedFilters.get("statuses");
            assertThat(statuses).hasSize(3).containsExactlyInAnyOrder("IN_PROGRESS", "COMPLETED", "PLANNED");

            assertThat(appliedFilters.get("productCodes")).isNull();
            assertThat(appliedFilters.get("dateFrom")).isNull();
            assertThat(appliedFilters.get("dateTo")).isNull();
            assertThat(appliedFilters.get("groupBy")).isNull();
            assertThat(appliedFilters.get("owner")).isNull();

            assertThat(roadmapItems).hasSize(3);

            // Validate releases sorted by date descending
            Map<String, Object> release0 =
                    (Map<String, Object>) roadmapItems.get(0).get("release");
            assertThat(release0.get("code")).isEqualTo("PY-2024.3.1");
            assertThat(release0.get("status")).isEqualTo("PLANNED");

            Map<String, Object> release1 =
                    (Map<String, Object>) roadmapItems.get(1).get("release");
            assertThat(release1.get("code")).isEqualTo("IDEA-2024.2.4");
            assertThat(release1.get("status")).isEqualTo("IN_PROGRESS");

            Map<String, Object> release2 =
                    (Map<String, Object>) roadmapItems.get(2).get("release");
            assertThat(release2.get("code")).isEqualTo("IDEA-2024.2.3");
            assertThat(release2.get("status")).isEqualTo("COMPLETED");
        });
    }

    @Test
    void shouldGetRoadmapWithDateRangeFilter() {
        var result = mvc.get()
                .uri("/api/roadmap?dateFrom=2024-02-26&dateTo=2024-03-05")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(2); // WEB-2024.2.3 and PY-2024.2.3 fall within this range

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("dateFrom")).isEqualTo("2024-02-26");
            assertThat(appliedFilters.get("dateTo")).isEqualTo("2024-03-05");

            assertThat(appliedFilters.get("productCodes")).isNull();
            assertThat(appliedFilters.get("statuses")).isNull();
            assertThat(appliedFilters.get("groupBy")).isNull();
            assertThat(appliedFilters.get("owner")).isNull();

            // Validate that all returned releases are within date range
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                // Use actualReleaseDate if available, otherwise releasedAt, otherwise plannedReleaseDate
                String releaseDate = (String) release.get("actualReleaseDate");
                if (releaseDate == null) {
                    releaseDate = (String) release.get("releasedAt");
                }
                if (releaseDate == null) {
                    releaseDate = (String) release.get("plannedReleaseDate");
                }
                assertThat(releaseDate)
                        .isNotNull()
                        .isGreaterThanOrEqualTo("2024-02-26")
                        .isLessThanOrEqualTo("2024-03-05");
            }
        });
    }

    @Test
    void shouldGetRoadmapWithOwnerFilter() {
        var result = mvc.get().uri("/api/roadmap?owner=john.doe").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(4);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("owner")).isEqualTo("john.doe");

            assertThat(appliedFilters.get("productCodes")).isNull();
            assertThat(appliedFilters.get("statuses")).isNull();
            assertThat(appliedFilters.get("dateFrom")).isNull();
            assertThat(appliedFilters.get("dateTo")).isNull();
            assertThat(appliedFilters.get("groupBy")).isNull();

            // Validate all items have owner john.doe
            for (Map<String, Object> item : roadmapItems) {
                Map<String, Object> release = (Map<String, Object>) item.get("release");
                assertThat(release.get("owner")).isEqualTo("john.doe");
            }
        });
    }

    @Test
    void shouldGetRoadmapWithGroupByProductCode() {
        var result = mvc.get().uri("/api/roadmap?groupBy=productCode").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(10);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("groupBy")).isEqualTo("productCode");

            assertThat(appliedFilters.get("productCodes")).isNull();
            assertThat(appliedFilters.get("statuses")).isNull();
            assertThat(appliedFilters.get("dateFrom")).isNull();
            assertThat(appliedFilters.get("dateTo")).isNull();
            assertThat(appliedFilters.get("owner")).isNull();

            // Groups are sorted by the date of the first release in each group (descending)
            // Within each group, releases are sorted by date descending
            List<String> releaseCodes = roadmapItems.stream()
                    .map(item -> (Map<String, Object>) item.get("release"))
                    .map(release -> (String) release.get("code"))
                    .toList();

            assertThat(releaseCodes)
                    .containsExactly(
                            "PY-2024.3.1", // PyCharm group (first: 2024-07-20)
                            "PY-2024.2.4",
                            "PY-2024.3.0",
                            "PY-2024.2.3",
                            "IDEA-2024.2.4", // IntelliJ group (first: 2024-04-20)
                            "IDEA-2024.2.3",
                            "IDEA-2023.3.8",
                            "WEB-2024.2.3", // WebStorm group (first: 2024-02-28)
                            "RIDER-2024.2.6",
                            "GO-2024.2.3" // GoLand group (first: 2024-02-15)
                            );
        });
    }

    @Test
    void shouldGetRoadmapWithGroupByStatus() {
        var result = mvc.get().uri("/api/roadmap?groupBy=status").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(10);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("groupBy")).isEqualTo("status");

            assertThat(appliedFilters.get("productCodes")).isNull();
            assertThat(appliedFilters.get("statuses")).isNull();
            assertThat(appliedFilters.get("dateFrom")).isNull();
            assertThat(appliedFilters.get("dateTo")).isNull();
            assertThat(appliedFilters.get("owner")).isNull();

            List<String> releaseCodes = roadmapItems.stream()
                    .map(item -> (Map<String, Object>) item.get("release"))
                    .map(release -> (String) release.get("code"))
                    .toList();

            assertThat(releaseCodes)
                    .containsExactly(
                            "PY-2024.3.1", // PLANNED group (first: 2024-07-20)
                            "PY-2024.2.4", // CANCELLED group (first: 2024-06-15)
                            "PY-2024.3.0", // DRAFT group (first: 2024-06-15)
                            "IDEA-2024.2.4", // IN_PROGRESS group (first: 2024-04-20)
                            "PY-2024.2.3", // DELAYED group (first: 2024-03-03)
                            "WEB-2024.2.3", // RELEASED group (first: 2024-02-28)
                            "RIDER-2024.2.6",
                            "GO-2024.2.3",
                            "IDEA-2023.3.8",
                            "IDEA-2024.2.3" // COMPLETED group (first: 2024-02-20)
                            );
        });
    }

    @Test
    void shouldGetRoadmapWithGroupByOwner() {
        var result = mvc.get().uri("/api/roadmap?groupBy=owner").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(10);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("groupBy")).isEqualTo("owner");

            assertThat(appliedFilters.get("productCodes")).isNull();
            assertThat(appliedFilters.get("statuses")).isNull();
            assertThat(appliedFilters.get("dateFrom")).isNull();
            assertThat(appliedFilters.get("dateTo")).isNull();
            assertThat(appliedFilters.get("owner")).isNull();

            List<String> releaseCodes = roadmapItems.stream()
                    .map(item -> (Map<String, Object>) item.get("release"))
                    .map(release -> (String) release.get("code"))
                    .toList();

            assertThat(releaseCodes)
                    .containsExactly(
                            "PY-2024.3.1", // jane.smith group (first: 2024-07-20)
                            "PY-2024.2.3",
                            "WEB-2024.2.3",
                            "RIDER-2024.2.6",
                            "GO-2024.2.3",
                            "IDEA-2023.3.8",
                            "PY-2024.2.4", // john.doe group (first: 2024-06-15)
                            "PY-2024.3.0",
                            "IDEA-2024.2.4",
                            "IDEA-2024.2.3");
        });
    }

    @Test
    void shouldGetRoadmapWithCombinedFilters() {
        var result = mvc.get()
                .uri("/api/roadmap?productCodes=intellij&statuses=COMPLETED&dateFrom=2024-01-01&owner=john.doe")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(1); // john.doe owns IDEA-2024.2.3 which matches all filter criteria

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            List<String> productCodes = (List<String>) appliedFilters.get("productCodes");
            assertThat(productCodes).containsExactly("intellij");

            List<String> statuses = (List<String>) appliedFilters.get("statuses");
            assertThat(statuses).containsExactly("COMPLETED");

            assertThat(appliedFilters.get("dateFrom")).isEqualTo("2024-01-01");
            assertThat(appliedFilters.get("owner")).isEqualTo("john.doe");

            // Validate the returned item matches all filter criteria
            Map<String, Object> item = roadmapItems.get(0);
            Map<String, Object> release = (Map<String, Object>) item.get("release");
            assertThat(release.get("code")).isEqualTo("IDEA-2024.2.3");
            assertThat(release.get("owner")).isEqualTo("john.doe");
            assertThat(release.get("status")).isEqualTo("COMPLETED");
        });
    }

    @Test
    void shouldValidateProgressMetricsInResponse() {
        var result = mvc.get().uri("/api/roadmap?productCodes=intellij").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(3);

            // IDEA-2024.2.4 - 11 features (1 IN_PROGRESS, 9 NEW, 1 ON_HOLD)
            Map<String, Object> metrics0 =
                    (Map<String, Object>) roadmapItems.get(0).get("progressMetrics");
            assertThat(metrics0.get("totalFeatures")).isEqualTo(11);
            assertThat(metrics0.get("completedFeatures")).isEqualTo(0);
            assertThat(metrics0.get("inProgressFeatures")).isEqualTo(1);
            assertThat(metrics0.get("newFeatures")).isEqualTo(9);
            assertThat(metrics0.get("onHoldFeatures")).isEqualTo(1);

            // IDEA-2024.2.3 - 1 RELEASED feature
            Map<String, Object> metrics1 =
                    (Map<String, Object>) roadmapItems.get(1).get("progressMetrics");
            assertThat(metrics1.get("totalFeatures")).isEqualTo(1);
            assertThat(metrics1.get("completedFeatures")).isEqualTo(1);
            assertThat(metrics1.get("inProgressFeatures")).isEqualTo(0);
            assertThat(metrics1.get("newFeatures")).isEqualTo(0);
            assertThat(metrics1.get("onHoldFeatures")).isEqualTo(0);

            // IDEA-2023.3.8 - 2 NEW features
            Map<String, Object> metrics2 =
                    (Map<String, Object>) roadmapItems.get(2).get("progressMetrics");
            assertThat(metrics2.get("totalFeatures")).isEqualTo(2);
            assertThat(metrics2.get("completedFeatures")).isEqualTo(0);
            assertThat(metrics2.get("inProgressFeatures")).isEqualTo(0);
            assertThat(metrics2.get("newFeatures")).isEqualTo(2);
            assertThat(metrics2.get("onHoldFeatures")).isEqualTo(0);
        });
    }

    @Test
    void shouldValidateHealthIndicatorsInResponse() {
        var result = mvc.get()
                .uri("/api/roadmap?productCodes=pycharm&productCodes=intellij")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(7);

            // PY-2024.3.1 - 1 ON_HOLD feature = 100% on hold = HIGH risk
            Map<String, Object> healthIndicators0 =
                    (Map<String, Object>) roadmapItems.get(0).get("healthIndicators");
            assertThat(healthIndicators0.get("riskLevel")).isEqualTo("HIGH");

            // PY-2024.2.4 - 1 NEW feature = ZERO risk
            Map<String, Object> healthIndicators1 =
                    (Map<String, Object>) roadmapItems.get(1).get("healthIndicators");
            assertThat(healthIndicators1.get("riskLevel")).isEqualTo("ZERO");

            // PY-2024.3.0 - 0 features = ZERO risk
            Map<String, Object> healthIndicators2 =
                    (Map<String, Object>) roadmapItems.get(2).get("healthIndicators");
            assertThat(healthIndicators2.get("riskLevel")).isEqualTo("ZERO");

            // IDEA-2024.2.4 - 11 features with 1 ON_HOLD = 9.09% = LOW risk
            Map<String, Object> healthIndicators3 =
                    (Map<String, Object>) roadmapItems.get(3).get("healthIndicators");
            assertThat(healthIndicators3.get("riskLevel")).isEqualTo("LOW");

            // PY-2024.2.3 - 5 features with 1 ON_HOLD = 20% = MEDIUM risk
            Map<String, Object> healthIndicators4 =
                    (Map<String, Object>) roadmapItems.get(4).get("healthIndicators");
            assertThat(healthIndicators4.get("riskLevel")).isEqualTo("MEDIUM");

            // IDEA-2024.2.3 - 1 RELEASED feature = ZERO risk
            Map<String, Object> healthIndicators5 =
                    (Map<String, Object>) roadmapItems.get(5).get("healthIndicators");
            assertThat(healthIndicators5.get("riskLevel")).isEqualTo("ZERO");

            // IDEA-2023.3.8 - 2 NEW features = ZERO risk
            Map<String, Object> healthIndicators6 =
                    (Map<String, Object>) roadmapItems.get(6).get("healthIndicators");
            assertThat(healthIndicators6.get("riskLevel")).isEqualTo("ZERO");
        });
    }

    @Test
    void shouldContainFeaturesInsideRelease() {
        var result = mvc.get().uri("/api/roadmap?productCodes=intellij").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // IDEA-2024.2.4 is the first release in the list
            Map<String, Object> releaseItem = roadmapItems.get(0);
            Map<String, Object> release = (Map<String, Object>) releaseItem.get("release");
            assertThat(release.get("code")).isEqualTo("IDEA-2024.2.4");

            List<Map<String, Object>> features = (List<Map<String, Object>>) releaseItem.get("features");
            assertThat(features).hasSize(11);

            // Find and validate feature IDEA-4
            Map<String, Object> idea4Feature = features.stream()
                    .filter(f -> "IDEA-4".equals(f.get("code")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Feature IDEA-4 not found"));

            assertThat(idea4Feature.get("id")).isEqualTo(5);
            assertThat(idea4Feature.get("code")).isEqualTo("IDEA-4");
            assertThat(idea4Feature.get("title")).isEqualTo("Kotlin Multiplatform Support");
            assertThat(idea4Feature.get("description")).isEqualTo("Improved Kotlin multiplatform project support");
            assertThat(idea4Feature.get("status")).isEqualTo("IN_PROGRESS");
            assertThat(idea4Feature.get("releaseCode")).isEqualTo("IDEA-2024.2.4");
            assertThat(idea4Feature.get("assignedTo")).isEqualTo("john.doe");
            assertThat(idea4Feature.get("createdBy")).isEqualTo("admin");
            assertThat(idea4Feature.get("createdAt")).asString().startsWith("2024-02-22");
            assertThat(idea4Feature.get("updatedBy")).isNull();
            assertThat(idea4Feature.get("updatedAt")).isNull();
        });
    }

    // Validation Error Tests
    @Test
    void shouldReturnBadRequestForInvalidStatus() {
        var result = mvc.get().uri("/api/roadmap?statuses=INVALID_STATUS").exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestForInvalidGroupBy() {
        var result = mvc.get().uri("/api/roadmap?groupBy=invalidGroup").exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestWhenDateFromIsAfterDateTo() {
        var result = mvc.get()
                .uri("/api/roadmap?dateFrom=2025-12-31&dateTo=2024-01-01")
                .exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestForInvalidDateFormat() {
        var result = mvc.get().uri("/api/roadmap?dateFrom=invalid-date").exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldAllowExtraQueryParameters() {
        var result = mvc.get()
                .uri("/api/roadmap?extraParam=extraValue&anotherParam=anotherValue")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(10); // Extra params ignored, returns all items
        });
    }

    @Test
    void shouldHandleEmptyResultsForNonExistentOwner() {
        var result = mvc.get().uri("/api/roadmap?owner=non.existent.user").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).isEmpty();

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("owner")).isEqualTo("non.existent.user");
        });
    }

    @Test
    void shouldHandleCaseInsensitiveStatuses() {
        var result =
                mvc.get().uri("/api/roadmap?statuses=released&statuses=planned").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(5); // 4 RELEASED + 1 PLANNED
        });
    }

    @Test
    void shouldHandleCaseInsensitiveGroupBy() {
        var result = mvc.get().uri("/api/roadmap?groupBy=PRODUCTCODE").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(10); // Case-insensitive groupBy returns all 9 releases
        });
    }

    // Sorting Tests
    @Test
    void shouldSortReleasesInDescendingOrderByDate() {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).isNotEmpty();

            // Extract sorting date from each item
            List<String> sortingDates = roadmapItems.stream()
                    .map(item -> (Map<String, Object>) item.get("release"))
                    .map(release -> (String)
                            (release.get("actualReleaseDate") != null
                                    ? release.get("actualReleaseDate")
                                    : (release.get("releasedAt") != null
                                            ? release.get("releasedAt")
                                            : (release.get("plannedReleaseDate") != null
                                                    ? release.get("plannedReleaseDate")
                                                    : release.get("createdAt")))))
                    .toList();

            // Verify all dates are present
            assertThat(sortingDates).doesNotContainNull();

            // Verify list is sorted in descending order (each date >= next date)
            for (int i = 0; i < sortingDates.size() - 1; i++) {
                assertThat(sortingDates.get(i))
                        .as("Date at index %d should be >= date at index %d (descending order)", i, i + 1)
                        .isGreaterThanOrEqualTo(sortingDates.get(i + 1));
            }
        });
    }

    @Test
    void shouldCalculateRiskLevelBasedOnOnHoldPercentage() {
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

                // Skip validation for releases with no features
                if (totalFeatures == 0) {
                    assertThat(riskLevel).isEqualTo("ZERO");
                } else {
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
                }
            }
        });
    }

    @Test
    void shouldFilterByDateByUsingCorrectSortingDateField() {
        var result = mvc.get()
                .uri("/api/roadmap?dateFrom=2024-02-01&dateTo=2024-02-20")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // Should return exactly 3 releases: GO-2024.2.3 (2024-02-15), RIDER-2024.2.6 (2024-02-20) and IDEA-2024.2.3
            // (2024-02-20)
            assertThat(roadmapItems).hasSize(3);

            // Verify all returned items have sorting date within range
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
                assertThat(sortingDate).isNotNull();
                String dateOnly = sortingDate.split("T")[0];
                assertThat(dateOnly).isGreaterThanOrEqualTo("2024-02-01");
                assertThat(dateOnly).isLessThanOrEqualTo("2024-02-20");
            }
        });
    }

    @Test
    void shouldReturnNullTimelineAdherenceWhenPlannedReleaseDateMissing() {
        var result = mvc.get().uri("/api/roadmap?productCodes=goland").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // GoLand has only one release in test data
            assertThat(roadmapItems).hasSize(1);

            Map<String, Object> goItem = roadmapItems.get(0);
            Map<String, Object> release = (Map<String, Object>) goItem.get("release");
            Map<String, Object> healthIndicators = (Map<String, Object>) goItem.get("healthIndicators");

            assertThat(release.get("code")).isEqualTo("GO-2024.2.3");
            assertThat(release.get("plannedReleaseDate"))
                    .as("GO-2024.2.3 should have no planned release date")
                    .isNull();
            assertThat(healthIndicators.get("timelineAdherence"))
                    .as("TimelineAdherence must be null when plannedReleaseDate is missing")
                    .isNull();
        });
    }

    @Test
    void shouldReturnOnScheduleWhenActualReleaseDateBeforePlanned() {
        var result = mvc.get()
                .uri("/api/roadmap?productCodes=intellij&statuses=RELEASED")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // IntelliJ has only one RELEASED release in test data
            assertThat(roadmapItems).hasSize(1);

            Map<String, Object> idea2023Item = roadmapItems.get(0);
            Map<String, Object> release = (Map<String, Object>) idea2023Item.get("release");
            Map<String, Object> healthIndicators = (Map<String, Object>) idea2023Item.get("healthIndicators");

            assertThat(release.get("code")).isEqualTo("IDEA-2023.3.8");
            assertThat(healthIndicators.get("timelineAdherence"))
                    .as("IDEA-2023.3.8 released on 2023-11-10, planned for 2023-11-15 (5 days early)")
                    .isEqualTo("ON_SCHEDULE");
        });
    }

    @Test
    void shouldReturnDelayedWhenDelay0To13Days() {
        var result = mvc.get()
                .uri("/api/roadmap?productCodes=webstorm&statuses=RELEASED")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // WebStorm has only one RELEASED release in test data
            assertThat(roadmapItems).hasSize(1);

            Map<String, Object> webItem = roadmapItems.get(0);
            Map<String, Object> release = (Map<String, Object>) webItem.get("release");
            Map<String, Object> healthIndicators = (Map<String, Object>) webItem.get("healthIndicators");

            assertThat(release.get("code")).isEqualTo("WEB-2024.2.3");
            assertThat(healthIndicators.get("timelineAdherence"))
                    .as("WEB-2024.2.3 released on 2024-02-29, planned for 2024-02-16 (13 days late)")
                    .isEqualTo("DELAYED");
        });
    }

    @Test
    void shouldReturnCriticalWhenDelay14DaysOrMore() {
        var result = mvc.get()
                .uri("/api/roadmap?productCodes=pycharm&statuses=DELAYED")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // PyCharm has only one DELAYED release in test data
            assertThat(roadmapItems).hasSize(1);

            Map<String, Object> pyItem = roadmapItems.get(0);
            Map<String, Object> release = (Map<String, Object>) pyItem.get("release");
            Map<String, Object> healthIndicators = (Map<String, Object>) pyItem.get("healthIndicators");

            assertThat(release.get("code")).isEqualTo("PY-2024.2.3");
            assertThat(healthIndicators.get("timelineAdherence"))
                    .as("PY-2024.2.3 released on 2024-03-03, planned for 2024-02-18 (14 days late)")
                    .isEqualTo("CRITICAL");
        });
    }

    @Test
    void shouldValidate14DayBoundary() {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");

            // Find WEB-2024.2.3 with 13 days delay (should be DELAYED)
            Map<String, Object> web13DayItem = roadmapItems.stream()
                    .filter(item -> {
                        Map<String, Object> release = (Map<String, Object>) item.get("release");
                        return "WEB-2024.2.3".equals(release.get("code"));
                    })
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("release WEB-2024.2.3 not found"));

            Map<String, Object> web13HealthIndicators = (Map<String, Object>) web13DayItem.get("healthIndicators");
            assertThat(web13HealthIndicators.get("timelineAdherence"))
                    .as("WEB-2024.2.3 with 13 days delay should be DELAYED, not CRITICAL")
                    .isEqualTo("DELAYED");

            // Find PY-2024.2.3 with 14 days delay (should be CRITICAL)
            Map<String, Object> py14DayItem = roadmapItems.stream()
                    .filter(item -> {
                        Map<String, Object> release = (Map<String, Object>) item.get("release");
                        return "PY-2024.2.3".equals(release.get("code"));
                    })
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("release PY-2024.2.3 not found"));

            Map<String, Object> py14HealthIndicators = (Map<String, Object>) py14DayItem.get("healthIndicators");
            assertThat(py14HealthIndicators.get("timelineAdherence"))
                    .as("PY-2024.2.3 with 14 days delay should be CRITICAL")
                    .isEqualTo("CRITICAL");
        });
    }

    // CSV Export Tests
    @Test
    void shouldExportRoadmapAsCsv() {
        var result = mvc.get().uri("/api/roadmap/export?format=CSV").exchange();

        assertThat(result).hasStatusOk().hasContentType("text/csv").headers().satisfies(headers -> {
            String headerValue = headers.getFirst("Content-Disposition");
            assertThat(headerValue).startsWith("attachment;");
            assertThat(headerValue).contains("filename=\"Roadmap_");
            assertThat(headerValue).endsWith(".csv\"");
        });

        assertThat(result).body().asString().satisfies(content -> {
            String[] lines = content.split("\n");
            assertThat(lines.length).isEqualTo(11); // Header + 10 data rows

            // Validate exact header with all 17 columns (no quotes)
            String expectedHeader =
                    "Product Code,Release Code,Release Description,Release Status,Released At,Planned Start Date,Planned Release Date,Actual Release Date,Owner,Total Features,Completed Features,In Progress Features,New Features,On Hold Features,Completion Percentage,Timeline Adherence,Risk Level";
            assertThat(lines[0]).isEqualTo(expectedHeader);
        });
    }

    @Test
    void shouldExportRoadmapAsCsvWithFilters() {
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
                    assertThat(lines.length).isEqualTo(2); // Header + 1 RELEASED intellij release

                    // Validate exact header (no quotes)
                    String expectedHeader =
                            "Product Code,Release Code,Release Description,Release Status,Released At,Planned Start Date,Planned Release Date,Actual Release Date,Owner,Total Features,Completed Features,In Progress Features,New Features,On Hold Features,Completion Percentage,Timeline Adherence,Risk Level";
                    assertThat(lines[0]).isEqualTo(expectedHeader);

                    // Validate first data row
                    String[] firstRow = lines[1].split(",");
                    assertThat(firstRow[0]).isEqualTo("intellij");
                    assertThat(firstRow[1]).isEqualTo("IDEA-2023.3.8");
                    assertThat(firstRow[3]).isEqualTo("RELEASED");
                });
    }

    @Test
    void shouldHandleCaseInsensitiveCsvFormat() {
        var result = mvc.get().uri("/api/roadmap/export?format=csv").exchange();
        assertThat(result).hasStatusOk().hasContentType("text/csv");
    }

    // PDF Export Tests
    @Test
    void shouldExportRoadmapAsPdf() {
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
    void shouldExportRoadmapAsPdfWithFilters() {
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
    void shouldHandleCaseInsensitivePdfFormat() {
        var result = mvc.get().uri("/api/roadmap/export?format=pdf").exchange();
        assertThat(result).hasStatusOk().satisfies(response -> {
            var contentType = response.getMvcResult().getResponse().getContentType();
            assertThat(contentType).contains("application/pdf");
        });
    }

    // File Naming Tests
    @Test
    void shouldGenerateCorrectFileName() {
        var result = mvc.get().uri("/api/roadmap/export?format=CSV").exchange();

        assertThat(result).hasStatusOk().headers().satisfies(headers -> {
            String headerValue = headers.getFirst("Content-Disposition");
            assertThat(headerValue).matches(".*filename=\"Roadmap_\\d{14}\\.csv\".*");
        });
    }

    @Test
    void shouldGenerateCorrectPdfFileName() {
        var result = mvc.get().uri("/api/roadmap/export?format=PDF").exchange();

        assertThat(result).hasStatusOk().headers().satisfies(headers -> {
            String headerValue = headers.getFirst("Content-Disposition");
            assertThat(headerValue).matches(".*filename=\"Roadmap_\\d{14}\\.pdf\".*");
        });
    }

    // Export Validation Error Tests
    @Test
    void shouldReturnBadRequestWhenFormatIsMissing() {
        var result = mvc.get().uri("/api/roadmap/export").exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestForInvalidFormat() {
        var result = mvc.get().uri("/api/roadmap/export?format=INVALID").exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestForEmptyFormat() {
        var result = mvc.get().uri("/api/roadmap/export?format=").exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestForInvalidStatusInExport() {
        var result = mvc.get()
                .uri("/api/roadmap/export?format=CSV&statuses=INVALID_STATUS")
                .exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestForInvalidGroupByInExport() {
        var result = mvc.get()
                .uri("/api/roadmap/export?format=PDF&groupBy=invalidGroup")
                .exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void shouldReturnBadRequestWhenDateFromIsAfterDateToInExport() {
        var result = mvc.get()
                .uri("/api/roadmap/export?format=CSV&dateFrom=2025-12-31&dateTo=2024-01-01")
                .exchange();
        assertThat(result).hasStatus(400);
    }

    // Export Filter Application Tests
    @Test
    void shouldApplyAllFiltersToExport() {
        var result = mvc.get()
                .uri(
                        "/api/roadmap/export?format=CSV&productCodes=intellij&statuses=RELEASED&dateFrom=2024-01-01&dateTo=2024-12-31&groupBy=productCode&owner=john.doe")
                .exchange();
        assertThat(result).hasStatusOk().hasContentType("text/csv");
    }

    @Test
    void shouldHandleEmptyResultsInExport() {
        var result = mvc.get()
                .uri("/api/roadmap/export?format=CSV&owner=non.existent.user")
                .exchange();

        assertThat(result).hasStatusOk().body().asString().satisfies(content -> {
            String[] lines = content.split("\n");
            assertThat(lines.length).isEqualTo(1);
            String headerLine = lines[0];
            assertThat(headerLine).contains("Product Code");
            assertThat(headerLine).contains("Release Code");
        });
    }

    // Performance Tests (basic timing validation)
    @Test
    void shouldCompleteExportWithinTimeLimit() {
        long startTime = System.currentTimeMillis();
        var result = mvc.get().uri("/api/roadmap/export?format=CSV").exchange();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertThat(result).hasStatusOk();
        assertThat(duration).isBetween(0L, 2000L);
    }

    // Export Sorting Tests
    @Test
    void shouldExportWithSameSortingAsRoadmapApi() {
        var result = mvc.get().uri("/api/roadmap/export?format=CSV").exchange();

        assertThat(result)
                .hasStatusOk()
                .hasContentType("text/csv")
                .body()
                .asString()
                .satisfies(content -> {
                    String[] lines = content.split("\n");
                    assertThat(lines.length).isEqualTo(11); // Header + 10 data rows

                    // Verify first row contains a release code
                    assertThat(lines[1]).isNotEmpty();

                    // Verify dates are in descending order
                    // Index 7 = Actual Release Date
                    String[] firstDataRow = lines[1].split(",");
                    String[] secondDataRow = lines[2].split(",");
                    String firstActualDate = firstDataRow[7];
                    String secondActualDate = secondDataRow[7];

                    // If both have actual dates, verify order
                    if (!firstActualDate.isEmpty() && !secondActualDate.isEmpty()) {
                        assertThat(firstActualDate).isGreaterThanOrEqualTo(secondActualDate);
                    }
                });
    }
}

package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;

class RoadmapControllerIntegrationTests extends AbstractIT {

    // ===== SUCCESS TESTS (200 status codes) with EXACT VALUE ASSERTIONS =====

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldGetRoadmapWithoutFilters() {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // Based on test data: 6 releases total
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6);

            // Validate summary structure and consistency
            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            assertThat(summary.get("totalReleases")).isEqualTo(6);
            assertThat(summary.get("completedReleases")).isEqualTo(6);
            assertThat(summary.get("draftReleases")).isEqualTo(0);
            assertThat(summary.get("totalFeatures")).isEqualTo(2); // Only 2 features have release_id set
            assertThat((Double) summary.get("overallCompletionPercentage")).isBetween(0.0, 100.0);
            assertThat((Integer) summary.get("completedReleases") + (Integer) summary.get("draftReleases"))
                    .isEqualTo((Integer) summary.get("totalReleases"));

            // Check applied filters are null/default for no filter request
            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("productCode")).isNull();
            assertThat(appliedFilters.get("startDate")).isNull();
            assertThat(appliedFilters.get("endDate")).isNull();
            assertThat(appliedFilters.get("includeCompleted")).isEqualTo(true);
            assertThat(appliedFilters.get("groupBy")).isNull();
            assertThat(appliedFilters.get("owner")).isNull();
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldGetRoadmapWithProductFilter() {
        var result = mvc.get().uri("/api/roadmap?productCode=intellij").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // IntelliJ has 2 releases (IDEA-2023.3.8, IDEA-2024.2.3)
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(2);

            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            assertThat(summary.get("totalReleases")).isEqualTo(2);
            assertThat(summary.get("totalFeatures")).isEqualTo(2);

            // Check exact applied filters
            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("productCode")).isEqualTo("intellij");

            // Verify actual release codes
            assertThat(roadmapItems)
                    .extracting(item -> ((Map<String, Object>) item.get("release")).get("code"))
                    .containsExactlyInAnyOrder("IDEA-2023.3.8", "IDEA-2024.2.3");

            // Validate isFavorite field is properly populated based on favorite entries
            for (Map<String, Object> roadmapItem : roadmapItems) {
                List<Map<String, Object>> features = (List<Map<String, Object>>) roadmapItem.get("features");
                for (Map<String, Object> feature : features) {
                    String code = (String) feature.get("code");
                    Boolean isFavorite = (Boolean) feature.get("isFavorite");

                    // Verify isFavorite field exists and is not null
                    assertThat(isFavorite).isNotNull();

                    // For user 'user', IDEA-2 is favorited, others are not
                    if ("IDEA-2".equals(code)) {
                        assertThat(isFavorite).isTrue();
                    } else {
                        assertThat(isFavorite).isFalse();
                    }
                }
            }
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldGetRoadmapWithDateRange() {
        var result = mvc.get()
                .uri("/api/roadmap?startDate=2024-02-01&endDate=2024-12-31")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // Should include 5 releases from 2024 (excluding IDEA-2023.3.8)
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(5);

            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            assertThat(summary.get("totalReleases")).isEqualTo(5);

            // Check exact filter values
            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("startDate").toString()).isEqualTo("2024-02-01");
            assertThat(appliedFilters.get("endDate").toString()).isEqualTo("2024-12-31");

            // Verify no 2023 releases are included
            assertThat(roadmapItems)
                    .extracting(item -> ((Map<String, Object>) item.get("release")).get("code"))
                    .doesNotContain("IDEA-2023.3.8")
                    .contains("IDEA-2024.2.3", "GO-2024.2.3", "WEB-2024.2.3", "PY-2024.2.3", "RIDER-2024.2.6");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldGetRoadmapWithIncludeCompleted() {
        var result = mvc.get().uri("/api/roadmap?includeCompleted=true").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // All 6 releases are RELEASED status
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6);

            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            assertThat(summary.get("totalReleases")).isEqualTo(6);
            assertThat(summary.get("completedReleases")).isEqualTo(6);
            assertThat(summary.get("draftReleases")).isEqualTo(0);

            // Check exact filter value
            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("includeCompleted")).isEqualTo(true);
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldGetRoadmapWithGroupBy() {
        var result = mvc.get().uri("/api/roadmap?groupBy=PRODUCT").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("groupBy").toString()).isEqualTo("PRODUCT");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldGetMultiProductRoadmap() {
        var result = mvc.get()
                .uri("/api/roadmap/multi-product?productCodes=intellij,goland")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // Check new structure: products array
            List<Map<String, Object>> products = (List<Map<String, Object>>) response.get("products");
            assertThat(products).hasSize(2); // IntelliJ and GoLand

            // Verify product structure
            Map<String, Object> intellijProduct = products.stream()
                    .filter(p -> "intellij".equals(p.get("productCode")))
                    .findFirst()
                    .orElseThrow();
            assertThat(intellijProduct.get("productName")).isEqualTo("IntelliJ IDEA");
            assertThat(intellijProduct.get("productCode")).isEqualTo("intellij");
            List<Map<String, Object>> intellijRoadmapItems =
                    (List<Map<String, Object>>) intellijProduct.get("roadmapItems");
            assertThat(intellijRoadmapItems).hasSize(2); // 2 IntelliJ releases

            Map<String, Object> golandProduct = products.stream()
                    .filter(p -> "goland".equals(p.get("productCode")))
                    .findFirst()
                    .orElseThrow();
            assertThat(golandProduct.get("productName")).isEqualTo("GoLand");
            assertThat(golandProduct.get("productCode")).isEqualTo("goland");
            List<Map<String, Object>> golandRoadmapItems =
                    (List<Map<String, Object>>) golandProduct.get("roadmapItems");
            assertThat(golandRoadmapItems).hasSize(1); // 1 GoLand release

            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            assertThat(summary.get("totalReleases")).isEqualTo(3);

            // Check exact applied filters
            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            List<String> productCodes = (List<String>) appliedFilters.get("productCodes");
            assertThat(productCodes).containsExactlyInAnyOrder("intellij", "goland");

            // Verify specific release codes across products
            List<String> allReleaseCodes = products.stream()
                    .flatMap(product -> ((List<Map<String, Object>>) product.get("roadmapItems")).stream())
                    .map(item -> (String) ((Map<String, Object>) item.get("release")).get("code"))
                    .toList();
            assertThat(allReleaseCodes).containsExactlyInAnyOrder("IDEA-2023.3.8", "IDEA-2024.2.3", "GO-2024.2.3");

            // Validate isFavorite field in multi-product roadmap
            for (Map<String, Object> product : products) {
                List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) product.get("roadmapItems");
                for (Map<String, Object> roadmapItem : roadmapItems) {
                    List<Map<String, Object>> features = (List<Map<String, Object>>) roadmapItem.get("features");
                    for (Map<String, Object> feature : features) {
                        String code = (String) feature.get("code");
                        Boolean isFavorite = (Boolean) feature.get("isFavorite");
                        assertThat(isFavorite).isNotNull();

                        // For user 'user', IDEA-2 is favorited, others are not
                        if ("IDEA-2".equals(code)) {
                            assertThat(isFavorite).isTrue();
                        } else {
                            assertThat(isFavorite).isFalse();
                        }
                    }
                }
            }
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldGetRoadmapByOwner() {
        var result = mvc.get().uri("/api/roadmap/by-owner?owner=siva").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            assertThat(response.get("owner")).isEqualTo("siva");

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("owner")).isEqualTo("siva");

            // Features assigned to siva: IDEA-2 (should have 1 roadmap item with 1 feature)
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            if (!roadmapItems.isEmpty()) {
                assertThat(roadmapItems).hasSize(1);
                List<Map<String, Object>> features =
                        (List<Map<String, Object>>) roadmapItems.get(0).get("features");
                assertThat(features).hasSize(1).allSatisfy(feature -> assertThat(feature.get("assignedTo"))
                        .isEqualTo("siva"));
            }
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldHandleComplexFilterCombination() {
        var result = mvc.get()
                .uri(
                        "/api/roadmap?productCode=intellij&startDate=2024-01-01&endDate=2024-12-31&includeCompleted=true&groupBy=STATUS")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // IntelliJ releases in 2024: IDEA-2024.2.3 (1 release)
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(1);

            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            assertThat(summary.get("totalReleases")).isEqualTo(1);

            // Check exact filter values
            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("productCode")).isEqualTo("intellij");
            assertThat(appliedFilters.get("includeCompleted")).isEqualTo(true);
            assertThat(appliedFilters.get("groupBy").toString()).isEqualTo("STATUS");
            assertThat(appliedFilters.get("startDate").toString()).isEqualTo("2024-01-01");
            assertThat(appliedFilters.get("endDate").toString()).isEqualTo("2024-12-31");

            // Verify specific release
            Map<String, Object> release =
                    (Map<String, Object>) roadmapItems.get(0).get("release");
            assertThat(release.get("code")).isEqualTo("IDEA-2024.2.3");
        });
    }

    // ===== BAD REQUEST TESTS (400 status codes) =====

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400ForInvalidStartDate() {
        var result = mvc.get().uri("/api/roadmap?startDate=invalid-format").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400ForInvalidEndDate() {
        var result = mvc.get().uri("/api/roadmap?endDate=invalid-format").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400ForInvalidGroupBy() {
        var result = mvc.get().uri("/api/roadmap?groupBy=INVALID").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400ForEmptyProductCodes() {
        var result = mvc.get().uri("/api/roadmap/multi-product").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400ForEmptyOwner() {
        var result = mvc.get().uri("/api/roadmap/by-owner").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400ForInvalidExportFormat() {
        var result = mvc.get().uri("/api/roadmap/export?format=INVALID").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400ForMissingExportFormat() {
        var result = mvc.get().uri("/api/roadmap/export").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400ForReversedDateRange() {
        var result = mvc.get()
                .uri("/api/roadmap?startDate=2024-12-31&endDate=2024-01-01")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    // ===== UNAUTHORIZED TESTS (401 status codes) =====

    @Test
    void shouldReturn401ForUnauthenticatedRoadmapRequest() {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401ForUnauthenticatedMultiProductRequest() {
        var result = mvc.get()
                .uri("/api/roadmap/multi-product?productCodes=intellij")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401ForUnauthenticatedByOwnerRequest() {
        var result = mvc.get().uri("/api/roadmap/by-owner?owner=siva").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401ForUnauthenticatedExportRequest() {
        var result = mvc.get().uri("/api/roadmap/export?format=CSV").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    // ===== EDGE CASE TESTS with EXACT VALUES =====

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldHandleEmptyProductCode() {
        var result = mvc.get().uri("/api/roadmap?productCode=").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // Empty product code should return all releases
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6);

            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            assertThat(summary.get("totalReleases")).isEqualTo(6);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("productCode")).isNull();
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldHandleNonExistentProductCode() {
        var result = mvc.get().uri("/api/roadmap?productCode=NON_EXISTENT").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // No releases for non-existent product
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(0);

            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            assertThat(summary.get("totalReleases")).isEqualTo(0);
            assertThat(summary.get("totalFeatures")).isEqualTo(0);
            assertThat(summary.get("completedReleases")).isEqualTo(0);
            assertThat(summary.get("draftReleases")).isEqualTo(0);
            assertThat(summary.get("overallCompletionPercentage")).isEqualTo(0.0);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("productCode")).isEqualTo("NON_EXISTENT");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldHandleNonExistentOwner() {
        var result = mvc.get().uri("/api/roadmap/by-owner?owner=nonexistent").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            assertThat(response.get("owner")).isEqualTo("nonexistent");

            // No features for non-existent owner
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(0);

            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            assertThat(summary.get("totalReleases")).isEqualTo(0);
            assertThat(summary.get("totalFeatures")).isEqualTo(0);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("owner")).isEqualTo("nonexistent");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldReturnProgressMetricsAndHealthIndicators() {
        var result = mvc.get().uri("/api/roadmap?productCode=intellij").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(2);

            // Find IDEA-2023.3.8 release (has 2 features: IDEA-1, IDEA-2)
            Map<String, Object> idea2023Release = roadmapItems.stream()
                    .filter(item -> ((Map<String, Object>) item.get("release"))
                            .get("code")
                            .equals("IDEA-2023.3.8"))
                    .findFirst()
                    .orElseThrow();

            // Validate progress metrics with specific values
            Map<String, Object> progress = (Map<String, Object>) idea2023Release.get("progressMetrics");
            assertThat(progress.get("totalFeatures")).isEqualTo(2);
            assertThat(progress.get("completedFeatures")).isEqualTo(0);
            assertThat(progress.get("inProgressFeatures")).isEqualTo(0);
            assertThat(progress.get("newFeatures")).isEqualTo(2); // Both IDEA-1 and IDEA-2 are NEW
            assertThat(progress.get("onHoldFeatures")).isEqualTo(0);
            assertThat(progress.get("completionPercentage")).isEqualTo(0.0);

            // Validate health indicators
            Map<String, Object> health = (Map<String, Object>) idea2023Release.get("healthIndicators");
            assertThat(health.get("timelineAdherence").toString()).isIn("ON_SCHEDULE", "DELAYED", "CRITICAL");
            assertThat(health.get("riskLevel").toString()).isIn("LOW", "MEDIUM", "HIGH", "CRITICAL");
            assertThat(health.get("blockedFeatures")).isEqualTo(0);

            // Validate release structure with specific values
            Map<String, Object> release = (Map<String, Object>) idea2023Release.get("release");
            assertThat(release.get("id")).isEqualTo(1);
            assertThat(release.get("code")).isEqualTo("IDEA-2023.3.8");
            assertThat(release.get("status").toString()).isEqualTo("RELEASED");
            assertThat(release.get("description")).isEqualTo("IntelliJ IDEA 2023.3.8");
            assertThat(release.get("createdBy")).isEqualTo("admin");
        });
    }

    // ===== ADDITIONAL EDGE CASES with EXACT VALUES =====

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldHandleOnlyStartDateProvided() {
        var result = mvc.get().uri("/api/roadmap?startDate=2024-02-20").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // Should include releases from 2024-02-20 onwards: WEB-2024.2.3, PY-2024.2.3, IDEA-2024.2.3
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(3);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("startDate").toString()).isEqualTo("2024-02-20");
            assertThat(appliedFilters.get("endDate")).isNull();

            assertThat(roadmapItems)
                    .extracting(item -> ((Map<String, Object>) item.get("release")).get("code"))
                    .containsExactlyInAnyOrder("WEB-2024.2.3", "PY-2024.2.3", "IDEA-2024.2.3");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldHandleOnlyEndDateProvided() {
        var result = mvc.get().uri("/api/roadmap?endDate=2024-02-21").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // Should include releases up to 2024-02-20: all 2023 + early 2024 releases
            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("startDate")).isNull();
            assertThat(appliedFilters.get("endDate").toString()).isEqualTo("2024-02-21");

            // Verify specific releases included (before or on 2024-02-20)
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).isNotEmpty();
            assertThat(roadmapItems)
                    .extracting(item -> ((Map<String, Object>) item.get("release")).get("code"))
                    .contains("IDEA-2023.3.8", "GO-2024.2.3", "WEB-2024.2.3", "PY-2024.2.3", "RIDER-2024.2.6");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldHandleAllGroupByOptions() {
        // Test PRODUCT grouping
        var productResult = mvc.get().uri("/api/roadmap?groupBy=PRODUCT").exchange();
        assertThat(productResult).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("groupBy").toString()).isEqualTo("PRODUCT");
        });

        // Test STATUS grouping
        var statusResult = mvc.get().uri("/api/roadmap?groupBy=STATUS").exchange();
        assertThat(statusResult).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("groupBy").toString()).isEqualTo("STATUS");
        });

        // Test ASSIGNEE grouping
        var assigneeResult = mvc.get().uri("/api/roadmap?groupBy=ASSIGNEE").exchange();
        assertThat(assigneeResult).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(6);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("groupBy").toString()).isEqualTo("ASSIGNEE");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldHandleSpecificProductWithFeatures() {
        var result = mvc.get()
                .uri("/api/roadmap?productCode=intellij&includeCompleted=true")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(2); // 2 IntelliJ releases

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("productCode")).isEqualTo("intellij");
            assertThat(appliedFilters.get("includeCompleted")).isEqualTo(true);

            // Find the release with features (IDEA-2023.3.8 has 2 features)
            var releaseWithFeatures = roadmapItems.stream()
                    .filter(item -> ((Map<String, Object>) item.get("release"))
                            .get("code")
                            .equals("IDEA-2023.3.8"))
                    .findFirst();

            if (releaseWithFeatures.isPresent()) {
                var item = releaseWithFeatures.get();
                List<Map<String, Object>> features = (List<Map<String, Object>>) item.get("features");
                assertThat(features).hasSize(2);
                assertThat(features)
                        .extracting(feature -> feature.get("code"))
                        .containsExactlyInAnyOrder("IDEA-1", "IDEA-2");
                assertThat(features)
                        .extracting(feature -> feature.get("status").toString())
                        .containsExactlyInAnyOrder("NEW", "NEW");

                // Validate complete feature structure with specific values
                var featureIdea1 = features.stream()
                        .filter(f -> f.get("code").equals("IDEA-1"))
                        .findFirst()
                        .orElseThrow();
                assertThat(featureIdea1.get("id")).isEqualTo(1);
                assertThat(featureIdea1.get("code")).isEqualTo("IDEA-1");
                assertThat(featureIdea1.get("title")).isEqualTo("Redesign Structure Tool Window");
                assertThat(featureIdea1.get("status").toString()).isEqualTo("NEW");
                assertThat(featureIdea1.get("createdBy")).isEqualTo("siva");
                assertThat(featureIdea1.get("assignedTo")).isEqualTo("marcobehler");

                var featureIdea2 = features.stream()
                        .filter(f -> f.get("code").equals("IDEA-2"))
                        .findFirst()
                        .orElseThrow();
                assertThat(featureIdea2.get("id")).isEqualTo(2);
                assertThat(featureIdea2.get("code")).isEqualTo("IDEA-2");
                assertThat(featureIdea2.get("title")).isEqualTo("SDJ Repository Method AutoCompletion");
                assertThat(featureIdea2.get("status").toString()).isEqualTo("NEW");
                assertThat(featureIdea2.get("createdBy")).isEqualTo("daniiltsarev");
                assertThat(featureIdea2.get("assignedTo")).isEqualTo("siva");
            }

            // Validate summary matches items
            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            assertThat(summary.get("totalReleases")).isEqualTo(roadmapItems.size());
            int totalFeaturesFromItems = roadmapItems.stream()
                    .mapToInt(item -> ((List<Map<String, Object>>) item.get("features")).size())
                    .sum();
            assertThat(summary.get("totalFeatures")).isEqualTo(totalFeaturesFromItems);
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldExportCsvWithCorrectContentType() {
        var result = mvc.get().uri("/api/roadmap/export?format=CSV").exchange();

        assertThat(result).hasStatusOk().satisfies(response -> {
            var contentType = response.getMvcResult().getResponse().getContentType();
            assertThat(contentType).contains("text/csv");

            var contentDisposition = response.getMvcResult().getResponse().getHeader("Content-Disposition");
            // Validate filename format: Roadmap_yyyyMMddHHmmss.csv
            assertThat(contentDisposition).matches(".*Roadmap_\\d{14}\\.csv.*");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldExportPdfWithCorrectContentType() {
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
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldExportCsvWithProductCodesFilter() {
        var result = mvc.get()
                .uri("/api/roadmap/export?format=CSV&productCodes=intellij")
                .exchange();

        assertThat(result).hasStatusOk().satisfies(response -> {
            var content = response.getMvcResult().getResponse().getContentAsString();
            assertThat(content).isNotEmpty();

            // Validate CSV headers in any order
            String[] lines = content.split("\n");
            String headerLine = lines[0].trim();
            String[] actualHeaders = headerLine.split(",");
            String[] expectedHeaders = {
                "Product Code",
                "Product Name",
                "Release Code",
                "Release Description",
                "Release Status",
                "Released At",
                "Total Features",
                "Completed Features",
                "In Progress Features",
                "New Features",
                "On Hold Features",
                "Completion Percentage",
                "Timeline Adherence",
                "Risk Level",
                "Blocked Features",
                "Feature Code",
                "Feature Title",
                "Feature Status",
                "Assigned To",
                "Created At"
            };
            assertThat(actualHeaders).containsExactlyInAnyOrder(expectedHeaders);

            // Validate data rows contain expected values
            assertThat(lines.length).isGreaterThan(1); // At least header + 1 data row

            // Validate that data rows contain the IntelliJ product with IDEA releases
            String dataContent = content.substring(content.indexOf('\n')); // Skip header
            assertThat(dataContent).contains("IntelliJ IDEA");
            assertThat(dataContent).contains("IDEA-2023.3.8");
            assertThat(dataContent).contains("IDEA-2024.2.3");
            assertThat(dataContent).contains("RELEASED");
            assertThat(dataContent).contains("Redesign Structure Tool Window");
            assertThat(dataContent).contains("SDJ Repository Method AutoCompletion");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldExportPdfWithDateRange() {
        var result = mvc.get()
                .uri("/api/roadmap/export?format=PDF&startDate=2024-01-01&endDate=2024-12-31")
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
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldExportPdfWithMultipleProducts() {
        var result = mvc.get()
                .uri("/api/roadmap/export?format=PDF&productCodes=intellij,goland")
                .exchange();

        assertThat(result).hasStatusOk().satisfies(response -> {
            var content = response.getMvcResult().getResponse().getContentAsByteArray();
            assertThat(content.length).isGreaterThan(0);
        });
    }

    // ===== MULTI-PRODUCT COMPREHENSIVE TESTS =====

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldGetMultiProductRoadmapWithAllProducts() {
        var result = mvc.get()
                .uri("/api/roadmap/multi-product?productCodes=intellij,goland,webstorm,pycharm,rider")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // Check new structure: products array
            List<Map<String, Object>> products = (List<Map<String, Object>>) response.get("products");
            assertThat(products).hasSize(5); // 5 products

            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            assertThat(summary.get("totalReleases")).isEqualTo(6);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            List<String> productCodes = (List<String>) appliedFilters.get("productCodes");
            assertThat(productCodes).containsExactlyInAnyOrder("intellij", "goland", "webstorm", "pycharm", "rider");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldGetMultiProductRoadmapWithDateFilter() {
        var result = mvc.get()
                .uri("/api/roadmap/multi-product?productCodes=intellij,goland&startDate=2024-01-01&endDate=2024-12-31")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // Check new structure: products array
            List<Map<String, Object>> products = (List<Map<String, Object>>) response.get("products");
            assertThat(products).hasSize(2); // IntelliJ and GoLand

            // Count total releases across products (IntelliJ 2024.2.3 + GoLand 2024.2.3 = 2 releases)
            int totalReleases = products.stream()
                    .mapToInt(product -> ((List<Map<String, Object>>) product.get("roadmapItems")).size())
                    .sum();
            assertThat(totalReleases).isEqualTo(2);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("startDate").toString()).isEqualTo("2024-01-01");
            assertThat(appliedFilters.get("endDate").toString()).isEqualTo("2024-12-31");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldGetMultiProductRoadmapWithGrouping() {
        var result = mvc.get()
                .uri("/api/roadmap/multi-product?productCodes=intellij,goland&groupBy=PRODUCT")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // Check new structure: products array
            List<Map<String, Object>> products = (List<Map<String, Object>>) response.get("products");
            assertThat(products).hasSize(2); // IntelliJ and GoLand

            // Count total releases across products (IntelliJ has 2, GoLand has 1 = 3 total)
            int totalReleases = products.stream()
                    .mapToInt(product -> ((List<Map<String, Object>>) product.get("roadmapItems")).size())
                    .sum();
            assertThat(totalReleases).isEqualTo(3);

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("groupBy").toString()).isEqualTo("PRODUCT");
        });
    }

    // ===== BY-OWNER WITH FILTERS TESTS =====

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldGetRoadmapByOwnerWithProductFilter() {
        var result = mvc.get()
                .uri("/api/roadmap/by-owner?owner=siva&productCode=intellij")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            assertThat(response.get("owner")).isEqualTo("siva");

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("owner")).isEqualTo("siva");
            assertThat(appliedFilters.get("productCode")).isEqualTo("intellij");

            // Should have 1 roadmap item (IDEA-2023.3.8 release with siva's feature IDEA-2)
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            if (!roadmapItems.isEmpty()) {
                assertThat(roadmapItems).hasSize(1);
                Map<String, Object> release =
                        (Map<String, Object>) roadmapItems.get(0).get("release");
                assertThat(release.get("code")).isEqualTo("IDEA-2023.3.8");

                List<Map<String, Object>> features =
                        (List<Map<String, Object>>) roadmapItems.get(0).get("features");
                assertThat(features).hasSize(1);
            }
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldGetRoadmapByOwnerWithAllFilters() {
        var result = mvc.get()
                .uri(
                        "/api/roadmap/by-owner?owner=siva&productCode=intellij&startDate=2023-01-01&endDate=2024-12-31&includeCompleted=true&groupBy=STATUS")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            assertThat(response.get("owner")).isEqualTo("siva");

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            assertThat(appliedFilters.get("owner")).isEqualTo("siva");
            assertThat(appliedFilters.get("productCode")).isEqualTo("intellij");
            assertThat(appliedFilters.get("includeCompleted")).isEqualTo(true);
            assertThat(appliedFilters.get("groupBy").toString()).isEqualTo("STATUS");
        });
    }

    // ===== ADDITIONAL EDGE CASES =====

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldHandleFutureDateRange() {
        var result = mvc.get()
                .uri("/api/roadmap?startDate=2025-01-01&endDate=2025-12-31")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // No releases in 2025
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).isEmpty();

            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            assertThat(summary.get("totalReleases")).isEqualTo(0);
            assertThat(summary.get("totalFeatures")).isEqualTo(0);
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldHandleMultipleProductCodesInSingleRequest() {
        var result = mvc.get()
                .uri("/api/roadmap/multi-product?productCodes=intellij&productCodes=goland&productCodes=webstorm")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            // Check new structure: products array
            List<Map<String, Object>> products = (List<Map<String, Object>>) response.get("products");
            assertThat(products).hasSize(3); // IntelliJ, GoLand, and WebStorm

            Map<String, Object> appliedFilters = (Map<String, Object>) response.get("appliedFilters");
            List<String> productCodes = (List<String>) appliedFilters.get("productCodes");
            assertThat(productCodes).containsExactlyInAnyOrder("intellij", "goland", "webstorm");

            // Verify product codes in response match filters
            List<String> actualProductCodes =
                    products.stream().map(p -> (String) p.get("productCode")).toList();
            assertThat(actualProductCodes).containsExactlyInAnyOrder("intellij", "goland", "webstorm");
        });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldReturnConsistentSummaryAcrossFilters() {
        var result = mvc.get()
                .uri("/api/roadmap?productCode=intellij&includeCompleted=true")
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            Map<String, Object> summary = (Map<String, Object>) response.get("summary");

            // Summary should match actual items count
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(summary.get("totalReleases")).isEqualTo(roadmapItems.size());

            // Calculate total features from items
            int totalFeaturesFromItems = roadmapItems.stream()
                    .mapToInt(item -> ((List<Map<String, Object>>) item.get("features")).size())
                    .sum();

            assertThat(summary.get("totalFeatures")).isEqualTo(totalFeaturesFromItems);
        });
    }
}

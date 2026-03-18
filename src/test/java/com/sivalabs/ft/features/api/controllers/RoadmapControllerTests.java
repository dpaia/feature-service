package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.domain.dtos.RoadmapResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;

@Sql(scripts = {"/test-data.sql", "/roadmap-test-data.sql"})
class RoadmapControllerTests extends AbstractIT {

    @Test
    void shouldGetRoadmapWithAllReleases() {
        var result = mvc.get().uri("/api/roadmap").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> {
            assertThat(response.roadmapItems()).isNotEmpty();
        });
    }

    @Test
    void shouldGetRoadmapWithProductCodeFilter() {
        var result = mvc.get().uri("/api/roadmap?productCodes=intellij").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> {
            assertThat(response.roadmapItems()).isNotEmpty();
            assertThat(response.roadmapItems())
                    .allMatch(item -> item.product().code().equals("intellij"));
        });
    }

    @Test
    void shouldGetRoadmapWithMultipleProductCodesFilter() {
        var result = mvc.get()
                .uri("/api/roadmap?productCodes=intellij&productCodes=goland")
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> {
            assertThat(response.roadmapItems()).isNotEmpty();
            assertThat(response.roadmapItems())
                    .allMatch(item -> item.product().code().equals("intellij")
                            || item.product().code().equals("goland"));
        });
    }

    @Test
    void shouldGetRoadmapWithStatusFilter() {
        var result = mvc.get().uri("/api/roadmap?statuses=RELEASED").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> {
            assertThat(response.roadmapItems()).isNotEmpty();
            assertThat(response.roadmapItems())
                    .allMatch(item -> item.release().status().name().equals("RELEASED"));
        });
    }

    @Test
    void shouldGetRoadmapWithStatusFilterCaseInsensitive() {
        var result = mvc.get().uri("/api/roadmap?statuses=released").exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    void shouldGetRoadmapWithOwnerFilter() {
        var result = mvc.get().uri("/api/roadmap?owner=john.doe").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> {
            assertThat(response.roadmapItems()).isNotEmpty();
            assertThat(response.roadmapItems())
                    .allMatch(item -> "john.doe".equals(item.release().owner()));
        });
    }

    @Test
    void shouldReturnEmptyResultsForNonExistentOwner() {
        var result = mvc.get().uri("/api/roadmap?owner=nonexistent.user").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> {
            assertThat(response.roadmapItems()).isEmpty();
            assertThat(response.summary().totalReleases()).isEqualTo(0);
            assertThat(response.summary().overallCompletionPercentage()).isEqualTo(0.0);
        });
    }

    @Test
    void shouldGetRoadmapWithDateFromFilter() {
        var result = mvc.get().uri("/api/roadmap?dateFrom=2024-01-01").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> assertThat(
                        response.roadmapItems())
                .isNotEmpty());
    }

    @Test
    void shouldGetRoadmapWithDateRange() {
        var result = mvc.get()
                .uri("/api/roadmap?dateFrom=2024-01-01&dateTo=2024-12-31")
                .exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    void shouldGetRoadmapGroupedByProductCode() {
        var result = mvc.get().uri("/api/roadmap?groupBy=productCode").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> assertThat(
                        response.roadmapItems())
                .isNotEmpty());
    }

    @Test
    void shouldGetRoadmapGroupedByStatus() {
        var result = mvc.get().uri("/api/roadmap?groupBy=status").exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    void shouldGetRoadmapGroupedByOwner() {
        var result = mvc.get().uri("/api/roadmap?groupBy=owner").exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    void shouldGetRoadmapGroupedByStatusCaseInsensitive() {
        var result = mvc.get().uri("/api/roadmap?groupBy=STATUS").exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    void shouldIncludeProgressMetrics() {
        var result = mvc.get()
                .uri("/api/roadmap?productCodes=intellij&statuses=RELEASED")
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> {
            assertThat(response.roadmapItems()).isNotEmpty();
            response.roadmapItems().forEach(item -> {
                assertThat(item.progressMetrics()).isNotNull();
                assertThat(item.progressMetrics().totalFeatures()).isGreaterThanOrEqualTo(0);
                assertThat(item.progressMetrics().completionPercentage()).isGreaterThanOrEqualTo(0.0);
            });
        });
    }

    @Test
    void shouldIncludeHealthIndicators() {
        var result = mvc.get()
                .uri("/api/roadmap?productCodes=intellij&statuses=RELEASED")
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> {
            assertThat(response.roadmapItems()).isNotEmpty();
            response.roadmapItems().forEach(item -> {
                assertThat(item.healthIndicators()).isNotNull();
                assertThat(item.healthIndicators().riskLevel()).isNotNull();
            });
        });
    }

    @Test
    void shouldCalculateRiskLevelZeroWhenNoFeaturesOnHold() {
        // IDEA-2024.2.3 has no features -> riskLevel should be ZERO
        var result = mvc.get()
                .uri("/api/roadmap?productCodes=intellij&statuses=RELEASED")
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> {
            var release2 = response.roadmapItems().stream()
                    .filter(item -> "IDEA-2024.2.3".equals(item.release().code()))
                    .findFirst();
            assertThat(release2).isPresent();
            assertThat(release2.get().healthIndicators().riskLevel().name()).isEqualTo("ZERO");
        });
    }

    @Test
    void shouldCalculateTimelineAdherenceOnScheduleForActualBeforePlanned() {
        // IDEA-2023.3.8: actual_release_date (Dec 10) before planned_release_date (Dec 15)
        var result = mvc.get()
                .uri("/api/roadmap?productCodes=intellij&statuses=RELEASED")
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> {
            var release = response.roadmapItems().stream()
                    .filter(item -> "IDEA-2023.3.8".equals(item.release().code()))
                    .findFirst();
            assertThat(release).isPresent();
            assertThat(release.get().healthIndicators().timelineAdherence().name())
                    .isEqualTo("ON_SCHEDULE");
        });
    }

    @Test
    void shouldCalculateTimelineAdherenceCriticalForOverdueRelease() {
        // GO-CRITICAL: planned date in 2020, no actual date -> CRITICAL (14+ days past planned)
        var result = mvc.get().uri("/api/roadmap?productCodes=goland").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> {
            var release = response.roadmapItems().stream()
                    .filter(item -> "GO-CRITICAL".equals(item.release().code()))
                    .findFirst();
            assertThat(release).isPresent();
            assertThat(release.get().healthIndicators().timelineAdherence().name())
                    .isEqualTo("CRITICAL");
        });
    }

    @Test
    void shouldReturnNullTimelineAdherenceWhenNoPlannedDate() {
        // WEB-DRAFT has no plannedReleaseDate
        var result = mvc.get().uri("/api/roadmap?productCodes=webstorm").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> {
            var release = response.roadmapItems().stream()
                    .filter(item -> "WEB-DRAFT".equals(item.release().code()))
                    .findFirst();
            assertThat(release).isPresent();
            assertThat(release.get().healthIndicators().timelineAdherence()).isNull();
        });
    }

    @Test
    void shouldIncludeAppliedFiltersInResponse() {
        var result = mvc.get()
                .uri("/api/roadmap?productCodes=intellij&statuses=RELEASED&owner=john.doe&groupBy=productCode")
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> {
            assertThat(response.appliedFilters()).isNotNull();
            assertThat(response.appliedFilters().productCodes()).containsExactly("intellij");
            assertThat(response.appliedFilters().statuses()).containsExactly("RELEASED");
            assertThat(response.appliedFilters().owner()).isEqualTo("john.doe");
            assertThat(response.appliedFilters().groupBy()).isEqualTo("productCode");
        });
    }

    @Test
    void shouldIncludeSummaryInResponse() {
        var result = mvc.get().uri("/api/roadmap").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> {
            assertThat(response.summary()).isNotNull();
            assertThat(response.summary().totalReleases()).isGreaterThan(0);
            assertThat(response.summary().overallCompletionPercentage()).isGreaterThanOrEqualTo(0.0);
        });
    }

    @Test
    void shouldReturn400ForInvalidStatusValue() {
        var result = mvc.get().uri("/api/roadmap?statuses=INVALID_STATUS").exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn400WhenDateFromIsAfterDateTo() {
        var result = mvc.get()
                .uri("/api/roadmap?dateFrom=2024-12-31&dateTo=2024-01-01")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn400ForInvalidGroupByValue() {
        var result = mvc.get().uri("/api/roadmap?groupBy=invalidField").exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn400ForInvalidDateFormat() {
        var result = mvc.get().uri("/api/roadmap?dateFrom=not-a-date").exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldIgnoreUnknownQueryParameters() {
        var result = mvc.get()
                .uri("/api/roadmap?unknownParam=value&anotherParam=123")
                .exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    void shouldExportRoadmapAsCsv() {
        var result = mvc.get().uri("/api/roadmap/export?format=CSV").exchange();
        assertThat(result).hasStatusOk();
        String contentType = result.getResponse().getContentType();
        assertThat(contentType).contains("text/csv");
        String contentDisposition = result.getResponse().getHeader("Content-Disposition");
        assertThat(contentDisposition).contains("attachment").contains(".csv");
    }

    @Test
    void shouldExportRoadmapAsCsvCaseInsensitive() {
        var result = mvc.get().uri("/api/roadmap/export?format=csv").exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    void shouldExportRoadmapAsPdf() {
        var result = mvc.get().uri("/api/roadmap/export?format=PDF").exchange();
        assertThat(result).hasStatusOk();
        String contentType = result.getResponse().getContentType();
        assertThat(contentType).contains("application/pdf");
        String contentDisposition = result.getResponse().getHeader("Content-Disposition");
        assertThat(contentDisposition).contains("attachment").contains(".pdf");
    }

    @Test
    void shouldExportRoadmapAsPdfCaseInsensitive() {
        var result = mvc.get().uri("/api/roadmap/export?format=pdf").exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    void shouldReturn400ForInvalidExportFormat() {
        var result = mvc.get().uri("/api/roadmap/export?format=EXCEL").exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn400WhenFormatIsMissingForExport() {
        var result = mvc.get().uri("/api/roadmap/export").exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldExportCsvWithFiltersApplied() {
        var result = mvc.get()
                .uri("/api/roadmap/export?format=CSV&productCodes=intellij")
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyText().contains("Product Code").contains("intellij");
    }

    @Test
    void shouldExportCsvContainsCorrectHeaders() {
        var result = mvc.get().uri("/api/roadmap/export?format=CSV").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyText()
                .contains("Product Code")
                .contains("Release Code")
                .contains("Release Status")
                .contains("Total Features")
                .contains("Completion Percentage")
                .contains("Timeline Adherence")
                .contains("Risk Level");
    }

    @Test
    void shouldSortReleasesInDescendingOrderByDate() {
        // IDEA-2024.2.3: actual_release_date Jan 20
        // IDEA-2023.3.8: actual_release_date Dec 10
        // So IDEA-2024.2.3 should come first
        var result = mvc.get()
                .uri("/api/roadmap?productCodes=intellij&statuses=RELEASED")
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().convertTo(RoadmapResponse.class).satisfies(response -> {
            assertThat(response.roadmapItems()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(response.roadmapItems().get(0).release().code()).isEqualTo("IDEA-2024.2.3");
        });
    }
}

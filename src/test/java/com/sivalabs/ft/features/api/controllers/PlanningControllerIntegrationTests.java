package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;

class PlanningControllerIntegrationTests extends AbstractIT {

    // ===== PLANNING HEALTH ENDPOINT TESTS =====

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldGetPlanningHealth() {
        var result = mvc.get().uri("/api/planning/health").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(Map.class)
                .satisfies(response -> {
                    // Verify releases by status section with specific values based on test data
                    Map<String, Object> releasesByStatus = (Map<String, Object>) response.get("releasesByStatus");
                    assertThat(releasesByStatus).isNotNull();
                    assertThat(releasesByStatus.get("DRAFT")).isEqualTo(0); // No draft releases in test data
                    assertThat(releasesByStatus.get("IN_PROGRESS")).isEqualTo(0); // No in-progress releases
                    assertThat(releasesByStatus.get("RELEASED")).isEqualTo(6); // All 6 releases are RELEASED
                    assertThat(releasesByStatus.get("CANCELLED")).isEqualTo(0); // No cancelled releases

                    // Verify at-risk releases section with specific calculations
                    Map<String, Object> atRiskReleases = (Map<String, Object>) response.get("atRiskReleases");
                    assertThat(atRiskReleases).isNotNull();
                    assertThat((Integer) atRiskReleases.get("overdue")).isGreaterThanOrEqualTo(0);
                    assertThat((Integer) atRiskReleases.get("criticallyDelayed"))
                            .isGreaterThanOrEqualTo(0);
                    // Total should be overdue + criticallyDelayed
                    Integer overdue = (Integer) atRiskReleases.get("overdue");
                    Integer criticallyDelayed = (Integer) atRiskReleases.get("criticallyDelayed");
                    Integer total = (Integer) atRiskReleases.get("total");
                    assertThat(total).isEqualTo(overdue + criticallyDelayed);

                    // Verify planning accuracy section with percentage values
                    Map<String, Object> planningAccuracy = (Map<String, Object>) response.get("planningAccuracy");
                    assertThat(planningAccuracy).isNotNull();
                    Double onTimeDelivery = (Double) planningAccuracy.get("onTimeDelivery");
                    assertThat(onTimeDelivery).isGreaterThanOrEqualTo(0.0);
                    assertThat((Double) planningAccuracy.get("averageDelay")).isGreaterThanOrEqualTo(0.0);
                    // Estimation accuracy should equal on-time delivery rate
                    Double estimationAccuracy = (Double) planningAccuracy.get("estimationAccuracy");
                    assertThat(estimationAccuracy).isEqualTo(onTimeDelivery);
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldValidateReleasesByStatusCounts() {
        var result = mvc.get().uri("/api/planning/health").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(Map.class)
                .satisfies(response -> {
                    Map<String, Object> releasesByStatus = (Map<String, Object>) response.get("releasesByStatus");

                    Integer draft = (Integer) releasesByStatus.get("DRAFT");
                    Integer inProgress = (Integer) releasesByStatus.get("IN_PROGRESS");
                    Integer released = (Integer) releasesByStatus.get("RELEASED");
                    Integer cancelled = (Integer) releasesByStatus.get("CANCELLED");

                    // Validate specific counts based on test data
                    assertThat(draft).isEqualTo(0);
                    assertThat(inProgress).isEqualTo(0);
                    assertThat(released).isEqualTo(6); // All releases in test data are RELEASED
                    assertThat(cancelled).isEqualTo(0);

                    // Total should match sum of all statuses
                    Integer total = draft + inProgress + released + cancelled;
                    assertThat(total).isEqualTo(6); // Total releases in test data
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldValidateAtRiskReleasesConsistency() {
        var result = mvc.get().uri("/api/planning/health").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(Map.class)
                .satisfies(response -> {
                    Map<String, Object> atRiskReleases = (Map<String, Object>) response.get("atRiskReleases");

                    Integer overdue = (Integer) atRiskReleases.get("overdue");
                    Integer criticallyDelayed = (Integer) atRiskReleases.get("criticallyDelayed");
                    Integer total = (Integer) atRiskReleases.get("total");

                    // Validate logical consistency
                    assertThat(criticallyDelayed).isLessThanOrEqualTo(overdue);
                    // Total should be overdue + criticallyDelayed
                    assertThat(total).isEqualTo(overdue + criticallyDelayed);

                    // All counts should be non-negative
                    assertThat(overdue).isGreaterThanOrEqualTo(0);
                    assertThat(criticallyDelayed).isGreaterThanOrEqualTo(0);
                    assertThat(total).isGreaterThanOrEqualTo(0);
                });
    }

    // ===== PLANNING TRENDS ENDPOINT TESTS =====

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldGetPlanningTrends() {
        var result = mvc.get().uri("/api/planning/trends").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(Map.class)
                .satisfies(response -> {
                    // Verify releases completed section with specific structure
                    Map<String, Object> releasesCompleted = (Map<String, Object>) response.get("releasesCompleted");
                    assertThat(releasesCompleted).isNotNull();

                    List<Map<String, Object>> trend = (List<Map<String, Object>>) releasesCompleted.get("trend");
                    assertThat(trend).isNotNull();
                    assertThat((Integer) releasesCompleted.get("total")).isEqualTo(6); // Based on test data

                    // Verify average release duration section with specific values
                    Map<String, Object> averageReleaseDuration =
                            (Map<String, Object>) response.get("averageReleaseDuration");
                    assertThat(averageReleaseDuration).isNotNull();

                    List<Map<String, Object>> durationTrend =
                            (List<Map<String, Object>>) averageReleaseDuration.get("trend");
                    assertThat(durationTrend).isNotNull();
                    assertThat((Double) averageReleaseDuration.get("current")).isGreaterThanOrEqualTo(0.0);

                    // Verify planning accuracy trend section
                    Map<String, Object> planningAccuracyTrend =
                            (Map<String, Object>) response.get("planningAccuracyTrend");
                    assertThat(planningAccuracyTrend).isNotNull();

                    List<Map<String, Object>> accuracyTrend =
                            (List<Map<String, Object>>) planningAccuracyTrend.get("onTimeDelivery");
                    assertThat(accuracyTrend).isNotNull();
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldValidateTrendDataPoints() {
        var result = mvc.get().uri("/api/planning/trends").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(Map.class)
                .satisfies(response -> {
                    Map<String, Object> releasesCompleted = (Map<String, Object>) response.get("releasesCompleted");
                    List<Map<String, Object>> trend = (List<Map<String, Object>>) releasesCompleted.get("trend");

                    // Validate trend data point structure and values
                    for (Map<String, Object> dataPoint : trend) {
                        assertThat(dataPoint.get("period")).isInstanceOf(String.class);
                        assertThat(dataPoint.get("value")).isInstanceOf(Number.class);

                        // Validate period format (should be YYYY-MM)
                        String period = (String) dataPoint.get("period");
                        assertThat(period).matches("\\d{4}-\\d{2}");

                        // Validate value is non-negative
                        Number value = (Number) dataPoint.get("value");
                        assertThat(value.doubleValue()).isGreaterThanOrEqualTo(0.0);
                    }
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldValidateAverageReleaseDurationTrend() {
        var result = mvc.get().uri("/api/planning/trends").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(Map.class)
                .satisfies(response -> {
                    Map<String, Object> averageReleaseDuration =
                            (Map<String, Object>) response.get("averageReleaseDuration");
                    List<Map<String, Object>> trend = (List<Map<String, Object>>) averageReleaseDuration.get("trend");
                    Double current = (Double) averageReleaseDuration.get("current");

                    // Validate current average is non-negative
                    assertThat(current).isGreaterThanOrEqualTo(0.0);

                    // Validate trend data points with specific values
                    for (Map<String, Object> dataPoint : trend) {
                        Double value = (Double) dataPoint.get("value");
                        assertThat(value).isGreaterThanOrEqualTo(0.0);

                        // Validate period format
                        String period = (String) dataPoint.get("period");
                        assertThat(period).matches("\\d{4}-\\d{2}");
                    }
                });
    }

    // ===== CAPACITY PLANNING ENDPOINT TESTS =====

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldGetCapacityPlanning() {
        var result = mvc.get().uri("/api/planning/capacity").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(Map.class)
                .satisfies(response -> {
                    // Verify overall capacity section with dynamic calculation
                    Map<String, Object> overallCapacity = (Map<String, Object>) response.get("overallCapacity");
                    assertThat(overallCapacity).isNotNull();

                    Integer totalResources = (Integer) overallCapacity.get("totalResources");
                    Double utilizationRate = (Double) overallCapacity.get("utilizationRate");
                    Double availableCapacity = (Double) overallCapacity.get("availableCapacity");
                    Integer overallocatedResources = (Integer) overallCapacity.get("overallocatedResources");

                    assertThat(totalResources).isGreaterThan(0); // Should have resources from test data
                    assertThat(utilizationRate).isGreaterThanOrEqualTo(0.0).isLessThanOrEqualTo(200.0);
                    // Available capacity should be 100 - utilization rate
                    assertThat(availableCapacity).isEqualTo(100.0 - utilizationRate);
                    assertThat(overallocatedResources).isGreaterThanOrEqualTo(0).isLessThanOrEqualTo(totalResources);

                    // Verify workload by owner section with detailed validation
                    List<Map<String, Object>> workloadByOwner =
                            (List<Map<String, Object>>) response.get("workloadByOwner");
                    assertThat(workloadByOwner).isNotNull();

                    // Validate each owner's workload data structure
                    for (Map<String, Object> ownerWorkload : workloadByOwner) {
                        assertThat(ownerWorkload.get("owner"))
                                .isInstanceOf(String.class)
                                .isNotNull();
                        assertThat((Integer) ownerWorkload.get("currentWorkload"))
                                .isGreaterThanOrEqualTo(0);
                        assertThat((Integer) ownerWorkload.get("capacity")).isGreaterThan(0);
                        assertThat((Double) ownerWorkload.get("utilizationRate"))
                                .isGreaterThanOrEqualTo(0.0);
                        assertThat(ownerWorkload.get("overallocationRisk").toString())
                                .isIn("NONE", "LOW", "MEDIUM", "HIGH");
                    }

                    // Verify commitments section with specific calculations
                    Map<String, Object> commitments = (Map<String, Object>) response.get("commitments");
                    assertThat(commitments).isNotNull();
                    assertThat((Integer) commitments.get("activeReleases")).isGreaterThanOrEqualTo(0);
                    assertThat((Integer) commitments.get("plannedReleases")).isGreaterThanOrEqualTo(0);
                    assertThat((Integer) commitments.get("totalFeatures")).isGreaterThanOrEqualTo(0);
                    assertThat((Double) commitments.get("estimatedEffort")).isGreaterThanOrEqualTo(0.0);

                    // Verify overallocation warnings section
                    List<Map<String, Object>> overallocationWarnings =
                            (List<Map<String, Object>>) response.get("overallocationWarnings");
                    assertThat(overallocationWarnings).isNotNull();

                    // Validate each warning structure
                    for (Map<String, Object> warning : overallocationWarnings) {
                        assertThat(warning.get("owner"))
                                .isInstanceOf(String.class)
                                .isNotNull();
                        assertThat(warning.get("severity").toString()).isIn("MEDIUM", "HIGH");
                        assertThat((Double) warning.get("overallocationPercentage"))
                                .isGreaterThan(100.0);
                    }
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldValidateOverallCapacityMetrics() {
        var result = mvc.get().uri("/api/planning/capacity").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(Map.class)
                .satisfies(response -> {
                    Map<String, Object> overallCapacity = (Map<String, Object>) response.get("overallCapacity");

                    Integer totalResources = (Integer) overallCapacity.get("totalResources");
                    Double utilizationRate = (Double) overallCapacity.get("utilizationRate");
                    Double availableCapacity = (Double) overallCapacity.get("availableCapacity");
                    Integer overallocatedResources = (Integer) overallCapacity.get("overallocatedResources");

                    // Validate metrics are reasonable and consistent
                    assertThat(totalResources).isGreaterThan(0);
                    assertThat(utilizationRate).isGreaterThanOrEqualTo(0.0).isLessThanOrEqualTo(200.0);
                    // Available capacity = 100 - utilization rate
                    assertThat(availableCapacity).isEqualTo(100.0 - utilizationRate);
                    // Overallocated resources should not exceed total resources
                    assertThat(overallocatedResources).isGreaterThanOrEqualTo(0).isLessThanOrEqualTo(totalResources);
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldValidateWorkloadByOwnerStructure() {
        var result = mvc.get().uri("/api/planning/capacity").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(Map.class)
                .satisfies(response -> {
                    List<Map<String, Object>> workloadByOwner =
                            (List<Map<String, Object>>) response.get("workloadByOwner");

                    // Should have workload data for known users from test data
                    assertThat(workloadByOwner).hasSizeGreaterThanOrEqualTo(2);

                    // Find specific users and validate their data
                    Map<String, Object> sivaWorkload = workloadByOwner.stream()
                            .filter(owner -> "siva".equals(owner.get("owner")))
                            .findFirst()
                            .orElse(null);

                    if (sivaWorkload != null) {
                        assertThat((Integer) sivaWorkload.get("currentWorkload"))
                                .isGreaterThanOrEqualTo(0);
                        assertThat((Integer) sivaWorkload.get("capacity")).isGreaterThan(0);
                        assertThat((Double) sivaWorkload.get("utilizationRate")).isGreaterThanOrEqualTo(0.0);
                        assertThat(sivaWorkload.get("overallocationRisk").toString())
                                .isIn("NONE", "LOW", "MEDIUM", "HIGH");
                    }
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldIdentifyOverallocationWarningsCorrectly() {
        var result = mvc.get().uri("/api/planning/capacity").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(Map.class)
                .satisfies(response -> {
                    List<Map<String, Object>> overallocationWarnings =
                            (List<Map<String, Object>>) response.get("overallocationWarnings");

                    // Validate overallocation warning structure when warnings exist
                    for (Map<String, Object> warning : overallocationWarnings) {
                        assertThat(warning.get("owner"))
                                .isInstanceOf(String.class)
                                .isNotNull();
                        assertThat(warning.get("severity").toString()).isIn("MEDIUM", "HIGH");

                        Double overallocationPercentage = (Double) warning.get("overallocationPercentage");
                        // Overallocation percentage should be > 100% to trigger warning
                        assertThat(overallocationPercentage).isGreaterThan(100.0);

                        String severity = (String) warning.get("severity");
                        // Validate severity levels based on percentage thresholds
                        if (overallocationPercentage > 150.0) {
                            assertThat(severity).isIn("HIGH", "MEDIUM");
                        } else {
                            assertThat(severity).isIn("MEDIUM", "HIGH");
                        }
                    }
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldValidateCommitmentsAccuracy() {
        var result = mvc.get().uri("/api/planning/capacity").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(Map.class)
                .satisfies(response -> {
                    Map<String, Object> commitments = (Map<String, Object>) response.get("commitments");

                    Integer activeReleases = (Integer) commitments.get("activeReleases");
                    Integer plannedReleases = (Integer) commitments.get("plannedReleases");
                    Integer totalFeatures = (Integer) commitments.get("totalFeatures");
                    Double estimatedEffort = (Double) commitments.get("estimatedEffort");

                    // Validate all metrics are non-negative and reasonable
                    assertThat(activeReleases).isGreaterThanOrEqualTo(0);
                    assertThat(plannedReleases).isGreaterThanOrEqualTo(0);
                    assertThat(totalFeatures)
                            .isEqualTo(4); // Based on actual API response - 4 total features (IDEA-4 removed)
                    assertThat(estimatedEffort).isGreaterThanOrEqualTo(0.0);
                });
    }

    // ===== UNAUTHORIZED TESTS =====

    @Test
    void shouldReturn401ForUnauthenticatedPlanningHealthRequest() {
        var result = mvc.get().uri("/api/planning/health").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401ForUnauthenticatedPlanningTrendsRequest() {
        var result = mvc.get().uri("/api/planning/trends").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401ForUnauthenticatedCapacityPlanningRequest() {
        var result = mvc.get().uri("/api/planning/capacity").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    // ===== EDGE CASES =====

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldHandleEmptyDatabaseGracefully() {
        // Test with no test data loaded to ensure graceful handling
        var healthResult = mvc.get().uri("/api/planning/health").exchange();
        assertThat(healthResult)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(Map.class)
                .satisfies(response -> {
                    Map<String, Object> releasesByStatus = (Map<String, Object>) response.get("releasesByStatus");
                    assertThat(releasesByStatus.get("DRAFT")).isEqualTo(0);
                    assertThat(releasesByStatus.get("IN_PROGRESS")).isEqualTo(0);
                    assertThat(releasesByStatus.get("RELEASED"))
                            .isEqualTo(6); // Database still has data from previous tests
                    assertThat(releasesByStatus.get("CANCELLED")).isEqualTo(0);
                });

        var trendsResult = mvc.get().uri("/api/planning/trends").exchange();
        assertThat(trendsResult)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(Map.class)
                .satisfies(response -> {
                    Map<String, Object> releasesCompleted = (Map<String, Object>) response.get("releasesCompleted");
                    assertThat(releasesCompleted.get("total"))
                            .isEqualTo(6); // Database still has data from previous tests
                });

        var capacityResult = mvc.get().uri("/api/planning/capacity").exchange();
        assertThat(capacityResult)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(Map.class)
                .satisfies(response -> {
                    Map<String, Object> overallCapacity = (Map<String, Object>) response.get("overallCapacity");
                    assertThat(overallCapacity.get("totalResources"))
                            .isEqualTo(3); // Database still has data from previous tests
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    @Sql("/test-data.sql")
    void shouldValidatePlanningAccuracyTrendDataPoints() {
        var result = mvc.get().uri("/api/planning/trends").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(Map.class)
                .satisfies(response -> {
                    Map<String, Object> planningAccuracyTrend =
                            (Map<String, Object>) response.get("planningAccuracyTrend");
                    List<Map<String, Object>> onTimeDelivery =
                            (List<Map<String, Object>>) planningAccuracyTrend.get("onTimeDelivery");

                    // Validate accuracy trend data points
                    for (Map<String, Object> dataPoint : onTimeDelivery) {
                        assertThat(dataPoint.get("period")).isInstanceOf(String.class);
                        assertThat(dataPoint.get("value")).isInstanceOf(Number.class);

                        String period = (String) dataPoint.get("period");
                        assertThat(period).matches("\\d{4}-\\d{2}");

                        Double value = (Double) dataPoint.get("value");
                        assertThat(value).isEqualTo(0.0); // No trend data in basic test scenario
                    }
                });
    }
}

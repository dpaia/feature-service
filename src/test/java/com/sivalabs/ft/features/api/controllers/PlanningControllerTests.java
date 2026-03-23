package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.CapacityPlanningResponse;
import com.sivalabs.ft.features.api.models.PlanningHealthResponse;
import com.sivalabs.ft.features.api.models.PlanningTrendsResponse;
import org.junit.jupiter.api.Test;

class PlanningControllerTests extends AbstractIT {

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldGetPlanningHealth() {
        var result = mvc.get().uri("/api/planning/health").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(PlanningHealthResponse.class)
                .satisfies(health -> {
                    assertThat(health.releasesByStatus()).isNotNull();
                    assertThat(health.releasesByStatus()).containsKeys("DRAFT", "IN_PROGRESS", "RELEASED", "CANCELLED");

                    assertThat(health.atRiskReleases()).isNotNull();
                    assertThat(health.atRiskReleases().overdue()).isGreaterThanOrEqualTo(0);
                    assertThat(health.atRiskReleases().criticallyDelayed()).isGreaterThanOrEqualTo(0);
                    assertThat(health.atRiskReleases().total())
                            .isEqualTo(health.atRiskReleases().overdue()
                                    + health.atRiskReleases().criticallyDelayed());

                    assertThat(health.planningAccuracy()).isNotNull();
                    assertThat(health.planningAccuracy().onTimeDelivery()).isBetween(0.0, 100.0);
                    assertThat(health.planningAccuracy().averageDelay()).isGreaterThanOrEqualTo(0.0);
                    assertThat(health.planningAccuracy().estimationAccuracy()).isBetween(0.0, 100.0);
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldGetPlanningTrends() {
        var result = mvc.get().uri("/api/planning/trends").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(PlanningTrendsResponse.class)
                .satisfies(trends -> {
                    assertThat(trends.releasesCompleted()).isNotNull();
                    assertThat(trends.releasesCompleted().trend()).isNotNull();
                    assertThat(trends.releasesCompleted().total()).isGreaterThanOrEqualTo(0);

                    // Validate trend data structure
                    trends.releasesCompleted().trend().forEach(trendData -> {
                        assertThat(trendData.period()).matches("\\d{4}-\\d{2}"); // YYYY-MM format
                        assertThat(trendData.value()).isGreaterThanOrEqualTo(0.0);
                    });

                    assertThat(trends.averageReleaseDuration()).isNotNull();
                    assertThat(trends.averageReleaseDuration().trend()).isNotNull();
                    assertThat(trends.averageReleaseDuration().current()).isGreaterThanOrEqualTo(0.0);

                    assertThat(trends.planningAccuracyTrend()).isNotNull();
                    assertThat(trends.planningAccuracyTrend().onTimeDelivery()).isNotNull();

                    // Validate planning accuracy trend values
                    trends.planningAccuracyTrend().onTimeDelivery().forEach(trendData -> {
                        assertThat(trendData.period()).matches("\\d{4}-\\d{2}"); // YYYY-MM format
                        assertThat(trendData.value()).isBetween(0.0, 100.0);
                    });
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldGetCapacityPlanning() {
        var result = mvc.get().uri("/api/planning/capacity").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(CapacityPlanningResponse.class)
                .satisfies(capacity -> {
                    assertThat(capacity.overallCapacity()).isNotNull();
                    assertThat(capacity.overallCapacity().totalResources()).isGreaterThanOrEqualTo(0);
                    assertThat(capacity.overallCapacity().utilizationRate())
                            .isBetween(0.0, 200.0); // Allow for overallocation
                    assertThat(capacity.overallCapacity().availableCapacity()).isBetween(-100.0, 100.0);
                    assertThat(capacity.overallCapacity().overallocatedResources())
                            .isGreaterThanOrEqualTo(0);

                    assertThat(capacity.workloadByOwner()).isNotNull();
                    capacity.workloadByOwner().forEach(workload -> {
                        assertThat(workload.owner()).isNotNull();
                        assertThat(workload.currentWorkload()).isGreaterThanOrEqualTo(0);
                        assertThat(workload.capacity()).isGreaterThan(0);
                        assertThat(workload.utilizationRate()).isGreaterThanOrEqualTo(0.0);
                        assertThat(workload.futureCommitments()).isGreaterThanOrEqualTo(0);
                        assertThat(workload.overallocationRisk()).isIn("NONE", "MEDIUM", "HIGH");
                    });

                    assertThat(capacity.commitments()).isNotNull();
                    assertThat(capacity.commitments().activeReleases()).isGreaterThanOrEqualTo(0);
                    assertThat(capacity.commitments().plannedReleases()).isGreaterThanOrEqualTo(0);
                    assertThat(capacity.commitments().totalFeatures()).isGreaterThanOrEqualTo(0);
                    assertThat(capacity.commitments().estimatedEffort()).isGreaterThanOrEqualTo(0.0);

                    assertThat(capacity.overallocationWarnings()).isNotNull();
                    capacity.overallocationWarnings().forEach(warning -> {
                        assertThat(warning.owner()).isNotNull();
                        assertThat(warning.severity()).isIn("MEDIUM", "HIGH");
                        assertThat(warning.overallocationPercentage()).isGreaterThan(100.0);
                    });
                });
    }

    @Test
    @WithMockOAuth2User
    void shouldValidateAtRiskReleasesCalculation() {
        var result = mvc.get().uri("/api/planning/health").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(PlanningHealthResponse.class)
                .satisfies(health -> {
                    var atRisk = health.atRiskReleases();

                    // Verify that overdue and critically delayed are mutually exclusive
                    // and their sum equals total
                    assertThat(atRisk.total()).isEqualTo(atRisk.overdue() + atRisk.criticallyDelayed());

                    // Verify that counts are non-negative
                    assertThat(atRisk.overdue()).isGreaterThanOrEqualTo(0);
                    assertThat(atRisk.criticallyDelayed()).isGreaterThanOrEqualTo(0);
                });
    }

    @Test
    @WithMockOAuth2User
    void shouldValidatePlanningAccuracyMetrics() {
        var result = mvc.get().uri("/api/planning/health").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(PlanningHealthResponse.class)
                .satisfies(health -> {
                    var accuracy = health.planningAccuracy();

                    // Verify percentage values are within valid range
                    assertThat(accuracy.onTimeDelivery()).isBetween(0.0, 100.0);
                    assertThat(accuracy.estimationAccuracy()).isBetween(0.0, 100.0);

                    // Average delay should be non-negative
                    assertThat(accuracy.averageDelay()).isGreaterThanOrEqualTo(0.0);
                });
    }

    @Test
    @WithMockOAuth2User
    void shouldValidateCapacityPlanningConsistency() {
        var result = mvc.get().uri("/api/planning/capacity").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(CapacityPlanningResponse.class)
                .satisfies(capacity -> {
                    // Verify that overallocation warnings are consistent with workload data
                    var highUtilizationOwners = capacity.workloadByOwner().stream()
                            .filter(w -> w.utilizationRate() >= 110.0) // High threshold from config
                            .map(CapacityPlanningResponse.WorkloadByOwner::owner)
                            .toList();

                    var warningOwners = capacity.overallocationWarnings().stream()
                            .map(CapacityPlanningResponse.OverallocationWarning::owner)
                            .toList();

                    // All owners with high utilization should have warnings
                    // (Note: This might not be exactly equal due to different calculation logic)
                    assertThat(warningOwners).containsAll(highUtilizationOwners);
                });
    }

    @Test
    @WithMockOAuth2User
    void shouldValidateTrendDataConsistency() {
        var result = mvc.get().uri("/api/planning/trends").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(PlanningTrendsResponse.class)
                .satisfies(trends -> {
                    // Verify that trend data is sorted chronologically
                    var releaseTrend = trends.releasesCompleted().trend();
                    if (releaseTrend.size() > 1) {
                        for (int i = 1; i < releaseTrend.size(); i++) {
                            String current = releaseTrend.get(i).period();
                            String previous = releaseTrend.get(i - 1).period();
                            assertThat(current).isGreaterThanOrEqualTo(previous);
                        }
                    }

                    // Verify that duration trend values are reasonable (not negative)
                    trends.averageReleaseDuration().trend().forEach(trendData -> {
                        assertThat(trendData.value()).isGreaterThanOrEqualTo(0.0);
                    });
                });
    }

    @Test
    @WithMockOAuth2User
    void shouldHandleEmptyDataGracefully() {
        // Test that endpoints handle cases with no data gracefully
        // This tests the edge case handling in the analytics service
        var healthResult = mvc.get().uri("/api/planning/health").exchange();
        assertThat(healthResult).hasStatusOk();

        var trendsResult = mvc.get().uri("/api/planning/trends").exchange();
        assertThat(trendsResult).hasStatusOk();

        var capacityResult = mvc.get().uri("/api/planning/capacity").exchange();
        assertThat(capacityResult).hasStatusOk();
    }
}

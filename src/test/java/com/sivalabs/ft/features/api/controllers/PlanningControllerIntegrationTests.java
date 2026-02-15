package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PlanningControllerIntegrationTests extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM favorite_features");
        jdbcTemplate.execute("DELETE FROM comments");
        jdbcTemplate.execute("DELETE FROM features");
        jdbcTemplate.execute("DELETE FROM releases");
        jdbcTemplate.execute("DELETE FROM products");

        setupProducts();
        setupReleases();
        setupFeaturesForWorkload();
        setupFeaturesForPlanningAccuracy();
        setupOverdueReleaseFeatures();
        setupCriticallyDelayedFeatures();
        setupJaneSmithFeatures();
        setupTrendTestData();
    }

    private void setupProducts() {
        jdbcTemplate.update(
                """
                        INSERT INTO products (id, code, prefix, name, description, image_url, disabled, created_by, created_at) VALUES
                        (1, 'intellij', 'IDEA', 'IntelliJ IDEA', 'JetBrains IDE for Java',
                            'https://resources.jetbrains.com/storage/products/company/brand/logos/IntelliJ_IDEA.png', false, 'admin', '2024-03-01 00:00:00'),
                        (2, 'goland', 'GO', 'GoLand', 'JetBrains IDE for Go',
                            'https://resources.jetbrains.com/storage/products/company/brand/logos/GoLand.png', false, 'admin', '2024-03-01 00:00:00'),
                        (3, 'webstorm', 'WEB', 'WebStorm', 'JetBrains IDE for Web Development',
                            'https://resources.jetbrains.com/storage/products/company/brand/logos/WebStorm.png', false, 'admin', '2024-03-01 00:00:00'),
                        (4, 'pycharm', 'PY', 'PyCharm', 'JetBrains IDE for Python',
                            'https://resources.jetbrains.com/storage/products/company/brand/logos/PyCharm.png', false, 'admin', '2024-03-01 00:00:00'),
                        (5, 'rider', 'RIDER', 'Rider', 'JetBrains IDE for .NET',
                            'https://resources.jetbrains.com/storage/products/company/brand/logos/Rider.png', false, 'admin', '2024-03-01 00:00:00')
                        """);
    }

    private void setupReleases() {
        // Old DRAFT releases - created 2023-01-01 and 2023-02-01
        // 90 business days from then = ~2023-05-xx, now Feb 2026 = well past ->
        // critically delayed
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
                        (1, 1, 'OLD-DRAFT-1', 'Old draft 1', 'DRAFT', 'admin', '2023-01-01 00:00:00'),
                        (2, 1, 'OLD-DRAFT-2', 'Old draft 2', 'DRAFT', 'admin', '2023-02-01 00:00:00')
                        """);

        // IN_PROGRESS releases past their planned end - overdue
        // Created 2023-11-01 and 2023-12-01 -> planned end ~March/April 2024 -> overdue
        // since then
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
                        (4, 1, 'INPROG-OVERDUE-1', 'In-progress overdue 1', 'IN_PROGRESS', 'admin', '2023-11-01 00:00:00'),
                        (5, 1, 'INPROG-OVERDUE-2', 'In-progress overdue 2', 'IN_PROGRESS', 'admin', '2023-12-01 00:00:00')
                        """);

        // Critically delayed IN_PROGRESS - created 2023-01-01, severely past planned
        // end
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
                        (12, 1, 'CRITICAL-DELAY', 'Critically delayed in-progress', 'IN_PROGRESS', 'admin', '2023-01-01 00:00:00')
                        """);

        // RELEASED on time: created 2024-01-01, released 2024-03-01 (before 90bd
        // ~2024-05-10)
        // RELEASED on time: created 2024-02-01, released 2024-04-01 (before 90bd
        // ~2024-06-10)
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, description, status, created_by, created_at, released_at) VALUES
                        (6, 1, 'REL-ON-TIME-1', 'Released on time 1', 'RELEASED', 'admin', '2024-01-01 00:00:00', '2024-03-01 00:00:00'),
                        (7, 1, 'REL-ON-TIME-2', 'Released on time 2', 'RELEASED', 'admin', '2024-02-01 00:00:00', '2024-04-01 00:00:00')
                        """);

        // RELEASED delayed: created 2024-01-01, plannedEnd ~2024-05-10, released
        // 2024-06-15 (~36 days late)
        // RELEASED delayed: created 2024-02-01, plannedEnd ~2024-06-10, released
        // 2024-07-20 (~40 days late)
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, description, status, created_by, created_at, released_at) VALUES
                        (8, 1, 'REL-DELAYED-1', 'Released delayed 1', 'RELEASED', 'admin', '2024-01-01 00:00:00', '2024-06-15 00:00:00'),
                        (9, 1, 'REL-DELAYED-2', 'Released delayed 2', 'RELEASED', 'admin', '2024-02-01 00:00:00', '2024-07-20 00:00:00')
                        """);

        // CANCELLED - should never appear in at-risk counts
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
                        (10, 1, 'CANCELLED-1', 'Cancelled release', 'CANCELLED', 'admin', '2024-01-01 00:00:00')
                        """);

        // Brand new DRAFT (5 days old) - should NOT be at-risk
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
                        (11, 1, 'DRAFT-NEW', 'Brand new draft', 'DRAFT', 'admin', NOW() - INTERVAL '5 days')
                        """);
    }

    private void setupFeaturesForWorkload() {
        // Active features across releases for workload capacity calculation
        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, planning_status, feature_owner, priority) VALUES
                        (1, 1, 1,  'F-1', 'Feature 1', '...', 'IN_PROGRESS', 'admin', 'john.doe',   NOW(), 'IN_PROGRESS', 'john.doe',   'HIGH'),
                        (2, 1, 1,  'F-2', 'Feature 2', '...', 'IN_PROGRESS', 'admin', 'alice.green', NOW(), 'IN_PROGRESS', 'alice.green', 'MEDIUM'),
                        (3, 1, 4,  'F-3', 'Feature 3', '...', 'IN_PROGRESS', 'admin', 'john.doe',   NOW(), 'IN_PROGRESS', 'john.doe',   'LOW'),
                        (4, 1, 5,  'F-4', 'Feature 4', '...', 'IN_PROGRESS', 'admin', 'bob.wilson', NOW(), 'IN_PROGRESS', 'bob.wilson', 'CRITICAL')
                        """);
    }

    private void setupFeaturesForPlanningAccuracy() {
        // On-time releases (6, 7): features completed on or before planned date
        // Delayed releases (8, 9): features completed after planned date
        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planned_completion_date, planning_status, feature_owner, priority) VALUES
                        (11, 1, 6, 'PR-1', 'On time feature 1', '...', 'RELEASED', 'admin', 'alice',
                            '2024-01-05 00:00:00', '2024-03-01 00:00:00', '2024-03-01', 'DONE', 'alice', 'HIGH'),
                        (12, 1, 7, 'PR-2', 'On time feature 2', '...', 'RELEASED', 'admin', 'bob',
                            '2024-02-05 00:00:00', '2024-04-01 00:00:00', '2024-04-01', 'DONE', 'bob', 'MEDIUM'),
                        (13, 1, 8, 'PR-3', 'Delayed feature 1', '...', 'RELEASED', 'admin', 'carol',
                            '2024-01-05 00:00:00', '2024-06-15 00:00:00', '2024-05-10', 'DONE', 'carol', 'HIGH'),
                        (14, 1, 9, 'PR-4', 'Delayed feature 2', '...', 'RELEASED', 'admin', 'dave',
                            '2024-02-05 00:00:00', '2024-07-20 00:00:00', '2024-06-10', 'DONE', 'dave', 'MEDIUM')
                        """);
    }

    private void setupOverdueReleaseFeatures() {
        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, planning_status, feature_owner, priority) VALUES
                        (15, 1, 4, 'PO-1', 'Overdue feature 1', '...', 'IN_PROGRESS', 'admin', 'alice',
                            '2023-11-05 00:00:00', 'IN_PROGRESS', 'alice', 'MEDIUM'),
                        (16, 1, 5, 'PO-2', 'Overdue feature 2', '...', 'IN_PROGRESS', 'admin', 'bob',
                            '2023-12-05 00:00:00', 'IN_PROGRESS', 'bob', 'HIGH')
                        """);
    }

    private void setupCriticallyDelayedFeatures() {
        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, planning_status, feature_owner, priority) VALUES
                        (17, 1, 12, 'CD-1', 'Critically delayed feature', '...', 'IN_PROGRESS', 'admin', 'carol',
                            '2023-01-05 00:00:00', 'IN_PROGRESS', 'carol', 'HIGH')
                        """);
    }

    private void setupJaneSmithFeatures() {
        // 12 features for jane.smith across active releases (IDs 18-29)
        // With capacity = 10, utilization = 12/10 * 100 = 120% -> HIGH overallocation
        for (int i = 18; i <= 29; i++) {
            jdbcTemplate.update(
                    """
                            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                                created_at, planning_status, feature_owner, priority) VALUES
                            (?, 1, 4, ?, 'Jane Smith Feature', '...', 'IN_PROGRESS', 'admin', 'jane.smith',
                                NOW(), 'IN_PROGRESS', 'jane.smith', 'MEDIUM')
                            """,
                    i,
                    "JS-" + i);
        }
    }

    private void setupTrendTestData() {
        // Insert 13 months of released releases to verify the 12-month limit is
        // enforced
        // Releases are bucketed by released_at month in trend data
        YearMonth now = YearMonth.now();
        ZoneId zone = ZoneOffset.UTC;

        for (int i = 0; i < 12; i++) {
            YearMonth createdMonth = now.minusMonths(i);
            YearMonth releasedMonth = createdMonth.plusMonths(1);

            LocalDate releasedDate = releasedMonth.isAfter(now) ? now.atEndOfMonth() : releasedMonth.atDay(15);

            Instant createdAt = createdMonth.atDay(15).atStartOfDay(zone).toInstant();

            Instant releasedAt = releasedMonth.isAfter(now)
                    ? now.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant()
                    : releasedDate.atStartOfDay(zone).toInstant();

            jdbcTemplate.update(
                    """
                            INSERT INTO releases
                            (id, product_id, code, description, status, created_by, created_at, released_at)
                            VALUES (?, 1, ?, 'Trend release', 'RELEASED', 'admin', ?, ?)
                            """,
                    100 + i,
                    "TREND-" + createdMonth,
                    Timestamp.from(createdAt),
                    Timestamp.from(releasedAt));
        }
    }

    @Test
    void shouldReturnAllRequiredTopLevelFieldsInHealthReport() throws Exception {
        Map<String, Object> map = getHealth();

        assertThat(map).containsKeys("releasesByStatus", "atRiskReleases", "planningAccuracy");

        Map<String, Object> byStatus = (Map<String, Object>) map.get("releasesByStatus");
        assertThat(byStatus).containsKeys("DRAFT", "IN_PROGRESS", "RELEASED", "CANCELLED");
    }

    @Test
    void shouldCountAllReleaseStatusesCorrectly() throws Exception {
        Map<String, Object> byStatus = (Map<String, Object>) getHealth().get("releasesByStatus");
        assertThat(i(byStatus, "DRAFT")).isEqualTo(3);
        assertThat(i(byStatus, "IN_PROGRESS")).isEqualTo(3);
        assertThat(i(byStatus, "RELEASED")).isGreaterThanOrEqualTo(4);
        assertThat(i(byStatus, "CANCELLED")).isEqualTo(1);
    }

    @Test
    void shouldEnsureAtRiskTotalEqualsOverduePlusCriticallyDelayed() throws Exception {
        Map<String, Object> risk = atRisk(getHealth());
        assertThat(i(risk, "total")).isEqualTo(i(risk, "overdue") + i(risk, "criticallyDelayed"));
    }

    @Test
    void shouldCountAtLeastOneReleaseAsOverdue() throws Exception {
        assertThat(i(atRisk(getHealth()), "overdue")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldCountAtLeastOneReleaseAsCriticallyDelayed() throws Exception {
        assertThat(i(atRisk(getHealth()), "criticallyDelayed")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldNotCountFutureDraftAsAtRisk() throws Exception {
        Map<String, Object> map = getHealth();
        int draftCount = i((Map<String, Object>) map.get("releasesByStatus"), "DRAFT");
        assertThat(i(atRisk(map), "total")).isLessThan(draftCount + 3);
        assertThat(i(atRisk(map), "criticallyDelayed") + i(atRisk(map), "overdue"))
                .isEqualTo(i(atRisk(map), "total"));
    }

    @Test
    void shouldNotCountReleasedReleasesAsAtRisk() throws Exception {
        Map<String, Object> map = getHealth();
        int totalReleases = ((Map<?, ?>) map.get("releasesByStatus"))
                .values().stream().mapToInt(v -> ((Number) v).intValue()).sum();
        assertThat(i(atRisk(map), "total")).isLessThan(totalReleases);
    }

    @Test
    void shouldNotCountCancelledReleasesAsAtRisk() throws Exception {
        Map<String, Object> map = getHealth();
        Map<String, Object> byStatus = (Map<String, Object>) map.get("releasesByStatus");
        int cancelledCount = i(byStatus, "CANCELLED");
        assertThat(cancelledCount).isEqualTo(1);
        int maxPossibleAtRisk = i(byStatus, "DRAFT") + i(byStatus, "IN_PROGRESS");
        assertThat(i(atRisk(map), "total")).isLessThanOrEqualTo(maxPossibleAtRisk);
    }

    @Test
    void shouldReturnAllRequiredPlanningAccuracyFields() throws Exception {
        Map<String, Object> accuracy = accuracy(getHealth());
        assertThat(accuracy).containsKeys("onTimeDelivery", "averageDelay", "estimationAccuracy");
    }

    @Test
    void shouldCalculateOnTimeDeliveryExactly() throws Exception {
        double otd = d(accuracy(getHealth()), "onTimeDelivery");
        assertThat(otd).isBetween(50.0, 100.0);
        assertThat(otd).isEqualTo(Math.round(otd * 10.0) / 10.0);
    }

    @Test
    void shouldCalculateAverageDelayOnlyForLateReleases() throws Exception {
        double avgDelay = d(accuracy(getHealth()), "averageDelay");
        assertThat(avgDelay).isGreaterThan(0.0);
        assertThat(avgDelay).isEqualTo(Math.round(avgDelay * 10.0) / 10.0);
    }

    @Test
    void shouldCalculateEstimationAccuracyWithinValidRange() throws Exception {
        double accuracy = d(accuracy(getHealth()), "estimationAccuracy");
        assertThat(accuracy).isBetween(0.0, 100.0);
        assertThat(accuracy).isEqualTo(Math.round(accuracy * 10.0) / 10.0);
    }

    @Test
    void shouldRoundAllPlanningAccuracyMetricsToOneDecimal() throws Exception {
        Map<String, Object> accuracy = accuracy(getHealth());
        for (String field : new String[] {"onTimeDelivery", "averageDelay", "estimationAccuracy"}) {
            double v = d(accuracy, field);
            assertThat(v).as("Field %s should be rounded to 1 decimal", field).isEqualTo(Math.round(v * 10.0) / 10.0);
        }
    }

    @Test
    void shouldEnsureNoNaNOrInfinityInPlanningAccuracy() throws Exception {
        Map<String, Object> accuracy = accuracy(getHealth());
        for (String field : new String[] {"onTimeDelivery", "averageDelay", "estimationAccuracy"}) {
            double v = d(accuracy, field);
            assertThat(Double.isNaN(v)).as("Field %s should not be NaN", field).isFalse();
            assertThat(Double.isInfinite(v))
                    .as("Field %s should not be Infinite", field)
                    .isFalse();
        }
    }

    @Test
    void shouldReturnAllRequiredTrendFields() throws Exception {
        Map<String, Object> trends = getTrends();
        assertThat(trends).containsKeys("releasesCompleted", "averageReleaseDuration", "planningAccuracyTrend");

        Map<String, Object> releasesCompleted = (Map<String, Object>) trends.get("releasesCompleted");
        assertThat(releasesCompleted).containsKeys("trend", "total");

        Map<String, Object> duration = (Map<String, Object>) trends.get("averageReleaseDuration");
        assertThat(duration).containsKeys("trend", "current");

        Map<String, Object> accuracyTrend = (Map<String, Object>) trends.get("planningAccuracyTrend");
        assertThat(accuracyTrend).containsKey("onTimeDelivery");
    }

    @Test
    void shouldLimitReleasesCompletedTrendToLast12Months() throws Exception {
        List<Map<String, Object>> trend = trendList(getTrends(), "releasesCompleted");
        assertThat(trend.size()).isLessThanOrEqualTo(12);
    }

    @Test
    void shouldLimitAverageDurationTrendToLast12Months() throws Exception {
        assertThat(trendList(getTrends(), "averageReleaseDuration").size()).isLessThanOrEqualTo(12);
    }

    @Test
    void shouldShowTrendDataInChronologicalOrder() throws Exception {
        List<Map<String, Object>> trend = trendList(getTrends(), "releasesCompleted");
        for (int i = 0; i < trend.size() - 1; i++) {
            assertThat((String) trend.get(i).get("period"))
                    .isLessThanOrEqualTo((String) trend.get(i + 1).get("period"));
        }
    }

    @Test
    void shouldFormatTrendPeriodsAsYYYYMM() throws Exception {
        for (Map<String, Object> data : trendList(getTrends(), "releasesCompleted")) {
            assertThat((String) data.get("period")).matches("\\d{4}-\\d{2}");
        }
    }

    @Test
    void shouldRoundAllTrendValuesToOneDecimalPlace() throws Exception {
        Map<String, Object> trends = getTrends();

        for (Map<String, Object> data : trendList(trends, "averageReleaseDuration")) {
            double v = ((Number) data.get("value")).doubleValue();
            assertThat(v).as("Duration trend value should be rounded").isEqualTo(Math.round(v * 10.0) / 10.0);
        }

        List<Map<String, Object>> accuracyTrend =
                (List<Map<String, Object>>) ((Map<?, ?>) trends.get("planningAccuracyTrend")).get("onTimeDelivery");
        for (Map<String, Object> data : accuracyTrend) {
            double v = ((Number) data.get("value")).doubleValue();
            assertThat(v).as("Accuracy trend value should be rounded").isEqualTo(Math.round(v * 10.0) / 10.0);
        }
    }

    @Test
    void shouldIncludeTotalReleasesCompletedCount() throws Exception {
        Map<String, Object> releasesCompleted =
                (Map<String, Object>) getTrends().get("releasesCompleted");
        int total = ((Number) releasesCompleted.get("total")).intValue();
        assertThat(total).isGreaterThan(0);
    }

    @Test
    void shouldSetCurrentAverageDurationFromMostRecentTrendPeriod() throws Exception {
        Map<String, Object> durationSection = (Map<String, Object>) getTrends().get("averageReleaseDuration");
        List<Map<String, Object>> trend = (List<Map<String, Object>>) durationSection.get("trend");
        double current = d(durationSection, "current");

        if (!trend.isEmpty()) {
            double lastValue = ((Number) trend.getLast().get("value")).doubleValue();
            assertThat(current).isEqualTo(lastValue);
        } else {
            assertThat(current).isEqualTo(0.0);
        }
    }

    @Test
    void shouldReturnAllRequiredCapacityFields() throws Exception {
        Map<String, Object> capacity = getCapacity();
        assertThat(capacity)
                .containsKeys("overallCapacity", "workloadByOwner", "commitments", "overallocationWarnings");
    }

    @Test
    void shouldIncludeAllRequiredFieldsInWorkloadByOwner() throws Exception {
        for (Map<String, Object> w : workloads(getCapacity())) {
            assertThat(w)
                    .containsKeys(
                            "owner",
                            "currentWorkload",
                            "capacity",
                            "utilizationRate",
                            "futureCommitments",
                            "overallocationRisk");
        }
    }

    @Test
    void shouldCalculateUtilizationRateAsCurrentWorkloadOverCapacity() throws Exception {
        for (Map<String, Object> w : workloads(getCapacity())) {
            int capacity = i(w, "capacity");
            if (capacity > 0) {
                double expected = Math.round((i(w, "currentWorkload") * 100.0 / capacity) * 10.0) / 10.0;
                assertThat(d(w, "utilizationRate"))
                        .as("Utilization rate for owner %s", w.get("owner"))
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void shouldIdentifyHighOverallocationForJaneSmith() throws Exception {
        Map<String, Object> jane = workloads(getCapacity()).stream()
                .filter(w -> "jane.smith".equals(w.get("owner")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("jane.smith not found in workload distribution"));

        assertThat(i(jane, "currentWorkload")).isEqualTo(12);
        assertThat(i(jane, "capacity")).isEqualTo(10);
        assertThat(d(jane, "utilizationRate")).isEqualTo(120.0);
        assertThat((String) jane.get("overallocationRisk")).isEqualTo("HIGH");
    }

    @Test
    void shouldApplyCorrectOverallocationRiskThresholds() throws Exception {
        for (Map<String, Object> w : workloads(getCapacity())) {
            double util = d(w, "utilizationRate");
            String risk = (String) w.get("overallocationRisk");

            if (util >= 120.0) {
                assertThat(risk).as("util=%.1f should be HIGH", util).isEqualTo("HIGH");
            } else if (util >= 100.0) {
                assertThat(risk).as("util=%.1f should be MEDIUM", util).isIn("MEDIUM", "HIGH");
            } else {
                assertThat(risk).as("util=%.1f should be NONE", util).isEqualTo("NONE");
            }
        }
    }

    @Test
    void shouldGenerateOneWarningPerOverallocatedOwner() throws Exception {
        Map<String, Object> capacity = getCapacity();
        List<Map<String, Object>> warnings = (List<Map<String, Object>>) capacity.get("overallocationWarnings");

        long overallocatedCount = workloads(capacity).stream()
                .filter(w -> !"NONE".equals(w.get("overallocationRisk")))
                .count();
        assertThat(warnings.size()).isEqualTo(overallocatedCount);
    }

    @Test
    void shouldIncludeAllRequiredFieldsInOverallocationWarnings() throws Exception {
        List<Map<String, Object>> warnings =
                (List<Map<String, Object>>) getCapacity().get("overallocationWarnings");
        for (Map<String, Object> w : warnings) {
            assertThat(w).containsKeys("owner", "severity", "overallocationPercentage");
            assertThat(w.get("owner")).isNotNull();
            assertThat((String) w.get("severity")).isIn("MEDIUM", "HIGH");
        }
    }

    @Test
    void shouldCalculateOverallUtilizationAndAvailableCapacityToSum100() throws Exception {
        Map<String, Object> overall = (Map<String, Object>) getCapacity().get("overallCapacity");
        double util = d(overall, "utilizationRate");
        double avail = d(overall, "availableCapacity");

        assertThat(util).isBetween(0.0, 100.0);
        assertThat(avail).isGreaterThanOrEqualTo(0.0);
        if (util < 100.0) {
            assertThat(Math.round((util + avail) * 10.0) / 10.0).isEqualTo(100.0);
        } else {
            assertThat(avail).isEqualTo(0.0);
        }
    }

    @Test
    void shouldEnsureNoNaNOrInfinityInCapacityMetrics() throws Exception {
        Map<String, Object> overall = (Map<String, Object>) getCapacity().get("overallCapacity");
        for (String field : new String[] {"utilizationRate", "availableCapacity"}) {
            double v = d(overall, field);
            assertThat(Double.isNaN(v)).as("Field %s should not be NaN", field).isFalse();
            assertThat(Double.isInfinite(v))
                    .as("Field %s should not be Infinite", field)
                    .isFalse();
        }
    }

    @Test
    void shouldReturnAllRequiredCommitmentFields() throws Exception {
        Map<String, Object> commitments = (Map<String, Object>) getCapacity().get("commitments");
        assertThat(commitments).containsKeys("activeReleases", "plannedReleases", "totalFeatures");

        assertThat(i(commitments, "activeReleases")).isGreaterThan(0);
        assertThat(i(commitments, "plannedReleases")).isGreaterThan(0);
        assertThat(i(commitments, "totalFeatures")).isGreaterThan(0);
    }

    @Test
    void shouldReturnSuccessfullyWhenNoFeaturesOrOwnersExist() {
        jdbcTemplate.execute("DELETE FROM features");
        var result = mvc.get().uri("/api/planning/capacity").exchange();
        assertThat(result).hasStatusOk();
    }

    private Map<String, Object> getHealth() throws Exception {
        var result = mvc.get().uri("/api/planning/health").exchange();
        assertThat(result).hasStatusOk();
        return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
    }

    private Map<String, Object> getTrends() throws Exception {
        var result = mvc.get().uri("/api/planning/trends").exchange();
        assertThat(result).hasStatusOk();
        return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
    }

    private Map<String, Object> getCapacity() throws Exception {
        var result = mvc.get().uri("/api/planning/capacity").exchange();
        assertThat(result).hasStatusOk();
        return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
    }

    private Map<String, Object> atRisk(Map<String, Object> health) {
        return (Map<String, Object>) health.get("atRiskReleases");
    }

    private Map<String, Object> accuracy(Map<String, Object> health) {
        return (Map<String, Object>) health.get("planningAccuracy");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> workloads(Map<String, Object> capacity) {
        return (List<Map<String, Object>>) capacity.get("workloadByOwner");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> trendList(Map<String, Object> trends, String sectionKey) {
        return (List<Map<String, Object>>) ((Map<?, ?>) trends.get(sectionKey)).get("trend");
    }

    private int i(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).intValue();
    }

    private double d(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).doubleValue();
    }
}

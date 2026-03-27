package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ReleaseMetricsIntegrationTests extends AbstractIT {

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

        // Added 5-minute buffer instead of exact "-7 days" because during test execution time passes,
        // and it will not be possible to have exact number of days
        final Instant createdAt = Instant.now()
                .truncatedTo(ChronoUnit.DAYS)
                .minus(10, ChronoUnit.DAYS)
                .minus(5, ChronoUnit.MINUTES);
        final Instant blockedFeatureOneUpdatedAt = Instant.now()
                .truncatedTo(ChronoUnit.DAYS)
                .minus(7, ChronoUnit.DAYS)
                .minus(5, ChronoUnit.MINUTES);
        final Instant blockedFeatureTwoUpdatedAt = Instant.now()
                .truncatedTo(ChronoUnit.DAYS)
                .minus(5, ChronoUnit.DAYS)
                .minus(5, ChronoUnit.MINUTES);
        final Instant blockedFeatureThreeUpdatedAt = Instant.now()
                .truncatedTo(ChronoUnit.DAYS)
                .minus(9, ChronoUnit.DAYS)
                .minus(5, ChronoUnit.MINUTES);
        final Instant blockedFeatureFourUpdatedAt = Instant.now()
                .truncatedTo(ChronoUnit.DAYS)
                .minus(4, ChronoUnit.DAYS)
                .minus(5, ChronoUnit.MINUTES);
        final Instant createdToday = Instant.now().minus(5, ChronoUnit.MINUTES);
        final Instant doneFeatureUpdatedAt = Instant.now()
                .truncatedTo(ChronoUnit.DAYS)
                .minus(5, ChronoUnit.DAYS)
                .minus(5, ChronoUnit.MINUTES);

        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, status, created_by, created_at, description) VALUES
                        (1, 1, 'IDEA-2023.3.8', 'IN_PROGRESS', 'admin', '2023-03-25 00:00:00', '1 blocked + 1 done: basic blocked-time calculation'),
                        (2, 1, 'IDEA-2026', 'IN_PROGRESS', 'admin', '2026-03-25 00:00:00', '3 blocked, 0 done: multiple blocked features summed'),
                        (3, 1, 'IDEA-2027', 'IN_PROGRESS', 'admin', '2027-03-25 00:00:00', '0 blocked, 1 done: all metrics should be zero'),
                        (4, 1, 'IDEA-2028', 'IN_PROGRESS', 'admin', '2028-03-25 00:00:00', '3 blocked, 0 done: non-integer average triggers rounding'),
                        (5, 1, 'IDEA-2029', 'IN_PROGRESS', 'admin', '2029-03-25 00:00:00', '1 blocked created today: division by zero (0 days blocked)'),
                        (6, 1, 'IDEA-2030', 'IN_PROGRESS', 'admin', '2030-03-25 00:00:00', '0 features: empty release')
                        """);

        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, blockage_reason, priority) VALUES
                        (400, 1, 1, 'R-400', 'Blocked for 7 days', '...', 'ON_HOLD', 'admin', 'alice', ?, ?, 'BLOCKED', 'alice', null, 'HIGH')
                        """,
                Timestamp.from(createdAt),
                Timestamp.from(blockedFeatureOneUpdatedAt));

        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, blockage_reason, priority) VALUES
                        (401, 1, 1, 'R-401', 'Done 5 days ago', '...', 'RELEASED', 'admin', 'bob', ?, ?, 'DONE', 'bob', null, 'MEDIUM')
                        """,
                Timestamp.from(createdAt),
                Timestamp.from(doneFeatureUpdatedAt));

        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, blockage_reason, priority) VALUES
                        (500, 1, 2, 'R-500', 'Blocked for 5 days', '...', 'ON_HOLD', 'admin', 'alice', ?, ?, 'BLOCKED', 'alice', null, 'HIGH')
                        """,
                Timestamp.from(createdAt),
                Timestamp.from(blockedFeatureTwoUpdatedAt));

        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, blockage_reason, priority) VALUES
                        (501, 1, 2, 'R-501', 'Blocked for 7 days', '...', 'ON_HOLD', 'admin', 'alice', ?, ?, 'BLOCKED', 'alice', null, 'HIGH')
                        """,
                Timestamp.from(createdAt),
                Timestamp.from(blockedFeatureOneUpdatedAt));

        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, blockage_reason, priority) VALUES
                        (502, 1, 2, 'R-502', 'Blocked for 9 days', '...', 'ON_HOLD', 'admin', 'alice', ?, ?, 'BLOCKED', 'alice', null, 'HIGH')
                        """,
                Timestamp.from(createdAt),
                Timestamp.from(blockedFeatureThreeUpdatedAt));

        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, blockage_reason, priority) VALUES
                        (601, 1, 3, 'R-601', 'Done 5 days ago', '...', 'RELEASED', 'admin', 'bob', ?, ?, 'DONE', 'bob', null, 'MEDIUM')
                        """,
                Timestamp.from(createdAt),
                Timestamp.from(doneFeatureUpdatedAt));

        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, blockage_reason, priority) VALUES
                        (700, 1, 4, 'R-700', 'Blocked for 5 days', '...', 'ON_HOLD', 'admin', 'alice', ?, ?, 'BLOCKED', 'alice', null, 'HIGH')
                        """,
                Timestamp.from(createdAt),
                Timestamp.from(blockedFeatureTwoUpdatedAt));

        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, blockage_reason, priority) VALUES
                        (701, 1, 4, 'R-701', 'Blocked for 7 days', '...', 'ON_HOLD', 'admin', 'alice', ?, ?, 'BLOCKED', 'alice', null, 'HIGH')
                        """,
                Timestamp.from(createdAt),
                Timestamp.from(blockedFeatureOneUpdatedAt));

        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, blockage_reason, priority) VALUES
                        (702, 1, 4, 'R-702', 'Blocked for 4 days', '...', 'ON_HOLD', 'admin', 'alice', ?, ?, 'BLOCKED', 'alice', null, 'HIGH')
                        """,
                Timestamp.from(createdAt),
                Timestamp.from(blockedFeatureFourUpdatedAt));

        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, blockage_reason, priority) VALUES
                        (800, 1, 5, 'R-800', 'Blocked today', '...', 'ON_HOLD', 'admin', 'alice', ?, ?, 'BLOCKED', 'alice', null, 'HIGH')
                        """,
                Timestamp.from(createdToday),
                Timestamp.from(createdToday));
    }

    @Test
    void shouldCorrectlyCalculateTotalBlockedDaysWhenNoFeatureIsBlocked() throws Exception {
        Map<String, Object> blocked = blockedTime(getMetrics("IDEA-2027"));

        long actual = ((Number) blocked.get("totalBlockedDays")).longValue();
        assertThat(actual).isEqualTo(0);
    }

    @Test
    void shouldCorrectlyCalculateBlockedPercentageOfTimeWhenNoFeatureIsBlocked() throws Exception {
        Map<String, Object> metrics = getMetrics("IDEA-2027");
        Map<String, Object> blocked = blockedTime(metrics);

        double actualPercentage = d(blocked, "percentageOfTime");
        assertThat(actualPercentage).isEqualTo(0.0);
    }

    @Test
    void shouldCorrectlyCalculateTotalBlockedDaysForOneBlockedFeature() throws Exception {
        Map<String, Object> blocked = blockedTime(getMetrics("IDEA-2023.3.8"));

        long actual = ((Number) blocked.get("totalBlockedDays")).longValue();
        assertThat(actual).isEqualTo(7L);
    }

    @Test
    void shouldCorrectlyCalculateBlockedPercentageOfTimeForOneBlockedFeature() throws Exception {
        Map<String, Object> metrics = getMetrics("IDEA-2023.3.8");
        Map<String, Object> blocked = blockedTime(metrics);

        double actualPercentage = d(blocked, "percentageOfTime");
        assertThat(actualPercentage).isEqualTo(46.7);
    }

    @Test
    void shouldCorrectlyCalculateTotalBlockedDaysForSeveralBlockedFeature() throws Exception {
        Map<String, Object> blocked = blockedTime(getMetrics("IDEA-2026"));

        long actual = ((Number) blocked.get("totalBlockedDays")).longValue();
        assertThat(actual).isEqualTo(21L);
    }

    @Test
    void shouldCorrectlyCalculateBlockedPercentageOfTimeForSeveralBlockedFeature() throws Exception {
        Map<String, Object> metrics = getMetrics("IDEA-2026");
        Map<String, Object> blocked = blockedTime(metrics);

        double actualPercentage = d(blocked, "percentageOfTime");
        assertThat(actualPercentage).isEqualTo(70.0);
    }

    @Test
    void shouldReturnZeroBlockedMetricsWhenBlockedFeatureCreatedToday() throws Exception {
        Map<String, Object> blocked = blockedTime(getMetrics("IDEA-2029"));

        long totalBlockedDays = ((Number) blocked.get("totalBlockedDays")).longValue();
        double percentageOfTime = d(blocked, "percentageOfTime");
        assertThat(totalBlockedDays).isEqualTo(0);
        assertThat(percentageOfTime).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroBlockedMetricsForEmptyRelease() throws Exception {
        Map<String, Object> blocked = blockedTime(getMetrics("IDEA-2030"));

        long totalBlockedDays = ((Number) blocked.get("totalBlockedDays")).longValue();
        double percentageOfTime = d(blocked, "percentageOfTime");
        double averageBlockedDuration = d(blocked, "averageBlockedDuration");
        assertThat(totalBlockedDays).isEqualTo(0);
        assertThat(percentageOfTime).isEqualTo(0.0);
        assertThat(averageBlockedDuration).isEqualTo(0.0);
    }

    @Test
    void shouldCorrectlyCalculateAverageBlockedDurationForSeveralBlockedFeatures() throws Exception {
        Map<String, Object> blocked = blockedTime(getMetrics("IDEA-2028"));

        double actual = d(blocked, "averageBlockedDuration");
        assertThat(actual).isEqualTo(5.3);
    }

    private Map<String, Object> getMetrics(String code) throws Exception {
        var result = mvc.get().uri("/api/releases/{code}/metrics", code).exchange();
        assertThat(result).hasStatusOk();
        return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
    }

    private Map<String, Object> blockedTime(Map<String, Object> metricsMap) {
        return (Map<String, Object>) metricsMap.get("blockedTime");
    }

    private double d(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).doubleValue();
    }
}

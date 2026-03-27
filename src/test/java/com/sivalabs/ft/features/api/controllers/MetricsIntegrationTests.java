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

class MetricsIntegrationTests extends AbstractIT {

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

        setupReleases();
        setupFeatures();
    }

    @Test
    void shouldCalculateFeaturesPerWeekExactlyForKnownFixture() throws Exception {
        assertThat(d(velocity(getMetrics("TEST-FAST")), "featuresPerWeek")).isEqualTo(5.0);
    }

    @Test
    void shouldCalculateFractionalFeaturesPerWeek() throws Exception {
        assertThat(d(velocity(getMetrics("FRACTIONAL")), "featuresPerWeek")).isEqualTo(2.7);
    }

    @Test
    void shouldReturnZeroVelocityWhenLessThanTwoWeeksOfData() throws Exception {
        assertThat(d(velocity(getMetrics("GO-2024.2.3")), "featuresPerWeek")).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroVelocityWhenThereAreNoFeatures() throws Exception {
        assertThat(d(velocity(getMetrics("GO-2024.2.7")), "featuresPerWeek")).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroVelocityWhenAllFeaturesAreInProgress() throws Exception {
        assertThat(d(velocity(getMetrics("IN-PROGRESS-ALL")), "featuresPerWeek"))
                .isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroVelocityWhenFeaturesArePartiallyDone() throws Exception {
        assertThat(d(velocity(getMetrics("PARTIALLY-DONE")), "featuresPerWeek")).isEqualTo(0.3);
    }

    @Test
    void shouldReturnCorrectValueWhenReleaseDateIsNull() throws Exception {
        // release date is null
        assertThat(d(velocity(getMetrics("RELEASE-DATE-NULL")), "featuresPerWeek"))
                .isEqualTo(0.3);
    }

    private Map<String, Object> getMetrics(String code) throws Exception {
        var result = mvc.get().uri("/api/releases/{code}/metrics", code).exchange();
        assertThat(result).hasStatusOk();
        return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
    }

    private Map<String, Object> velocity(Map<String, Object> metricsMap) {
        return (Map<String, Object>) metricsMap.get("velocity");
    }

    private int i(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).intValue();
    }

    private double d(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).doubleValue();
    }

    private void setupReleases() {
        final Instant startDate = Instant.now()
                .truncatedTo(ChronoUnit.DAYS)
                .minus(21, ChronoUnit.DAYS)
                .minus(5, ChronoUnit.MINUTES);
        final Instant releaseDate = Instant.now()
                .truncatedTo(ChronoUnit.DAYS)
                .minus(6, ChronoUnit.DAYS)
                .minus(5, ChronoUnit.MINUTES);

        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, status, created_by, created_at, released_at, description) VALUES
                        (3, 2, 'GO-2024.2.3', 'RELEASED', 'admin', '2025-02-03 00:00:00', '2025-02-07 00:00:00', 'RELEASED, <2 biz weeks, has DONE features -> threshold returns 0'),
                        (4, 2, 'GO-2024.2.7', 'RELEASED', 'admin', '2024-01-15 00:00:00', '2025-01-20 00:00:00', 'RELEASED, long span, zero features -> 0'),
                        (10, 1, 'TEST-FAST', 'RELEASED', 'admin', '2025-01-01 00:00:00', '2025-01-14 00:00:00', 'RELEASED, 10 DONE features, 2 biz weeks -> 5.0/week'),
                        (20, 1, 'SUPER-TEST', 'IN_PROGRESS', 'admin', ?, NULL, 'IN_PROGRESS, unused fixture'),
                        (30, 1, 'IN-PROGRESS-ALL', 'IN_PROGRESS', 'admin', '2025-01-01 00:00:00', NULL, 'IN_PROGRESS, all features IN_PROGRESS -> 0'),
                        (40, 1, 'PARTIALLY-DONE', 'IN_PROGRESS', 'admin', ?, ?, 'IN_PROGRESS, 1/2 features DONE, ~3 weeks -> 0.3'),
                        (50, 1, 'RELEASE-DATE-NULL', 'IN_PROGRESS', 'admin', ?, NULL, 'IN_PROGRESS, null releasedAt, 1 DONE feature -> 0.3'),
                        (60, 1, 'FRACTIONAL', 'RELEASED', 'admin', '2025-02-03 00:00:00', '2025-02-19 00:00:00', 'RELEASED, 7 DONE features, 13 biz days (2.6 weeks) -> 2.7/week')
                        """,
                Timestamp.from(startDate),
                Timestamp.from(startDate),
                Timestamp.from(releaseDate),
                Timestamp.from(startDate));
    }

    private void setupFeatures() {
        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, priority) VALUES
                        (200, 1, 10, 'F-1', 'Fast feature 1', '...', 'RELEASED', 'admin', 'alice', '2025-01-02 00:00:00', '2025-01-08 00:00:00', 'DONE', 'alice', 'HIGH'),
                        (201, 1, 10, 'F-2', 'Fast feature 2', '...', 'RELEASED', 'admin', 'alice', '2025-01-03 00:00:00', '2025-01-09 00:00:00', 'DONE', 'alice', 'HIGH'),
                        (202, 1, 10, 'F-3', 'Fast feature 3', '...', 'RELEASED', 'admin', 'bob',   '2025-01-04 00:00:00', '2025-01-12 00:00:00', 'DONE', 'bob',   'MEDIUM'),
                        (203, 1, 10, 'F-4', 'Fast feature 4', '...', 'RELEASED', 'admin', 'bob',   '2025-01-05 00:00:00', '2025-01-15 00:00:00', 'DONE', 'bob',   'MEDIUM'),
                        (204, 1, 10, 'F-5', 'Fast feature 5', '...', 'RELEASED', 'admin', 'carol', '2025-01-06 00:00:00', '2025-01-20 00:00:00', 'DONE', 'carol', 'LOW'),
                        (205, 1, 10, 'F-6', 'Fast feature 6', '...', 'RELEASED', 'admin', 'carol', '2025-01-07 00:00:00', '2025-01-14 00:00:00', 'DONE', 'carol', 'HIGH'),
                        (206, 1, 10, 'F-7', 'Fast feature 7', '...', 'RELEASED', 'admin', 'carol', '2025-01-08 00:00:00', '2025-01-14 00:00:00', 'DONE', 'carol', 'MEDIUM'),
                        (207, 1, 10, 'F-8', 'Fast feature 8', '...', 'RELEASED', 'admin', 'carol', '2025-01-09 00:00:00', '2025-01-14 00:00:00', 'DONE', 'carol', 'LOW'),
                        (208, 1, 10, 'F-9', 'Fast feature 9', '...', 'RELEASED', 'admin', 'carol', '2025-01-10 00:00:00', '2025-01-14 00:00:00', 'DONE', 'carol', 'CRITICAL'),
                        (209, 1, 10, 'F-10','Fast feature 10','...', 'RELEASED', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 00:00:00', 'DONE', 'carol', 'HIGH'),
                        (700, 1, 60, 'FR-1', 'Fractional 1', '...', 'RELEASED', 'admin', 'alice', '2025-02-03 00:00:00', '2025-02-10 00:00:00', 'DONE', 'alice', 'HIGH'),
                        (701, 1, 60, 'FR-2', 'Fractional 2', '...', 'RELEASED', 'admin', 'alice', '2025-02-04 00:00:00', '2025-02-11 00:00:00', 'DONE', 'alice', 'HIGH'),
                        (702, 1, 60, 'FR-3', 'Fractional 3', '...', 'RELEASED', 'admin', 'bob',   '2025-02-05 00:00:00', '2025-02-12 00:00:00', 'DONE', 'bob',   'MEDIUM'),
                        (703, 1, 60, 'FR-4', 'Fractional 4', '...', 'RELEASED', 'admin', 'bob',   '2025-02-06 00:00:00', '2025-02-13 00:00:00', 'DONE', 'bob',   'MEDIUM'),
                        (704, 1, 60, 'FR-5', 'Fractional 5', '...', 'RELEASED', 'admin', 'carol', '2025-02-07 00:00:00', '2025-02-14 00:00:00', 'DONE', 'carol', 'LOW'),
                        (705, 1, 60, 'FR-6', 'Fractional 6', '...', 'RELEASED', 'admin', 'carol', '2025-02-10 00:00:00', '2025-02-17 00:00:00', 'DONE', 'carol', 'HIGH'),
                        (706, 1, 60, 'FR-7', 'Fractional 7', '...', 'RELEASED', 'admin', 'carol', '2025-02-11 00:00:00', '2025-02-18 00:00:00', 'DONE', 'carol', 'MEDIUM'),
                        (600, 2, 3, 'GL-1', 'GoLand feature 1', '...', 'RELEASED', 'admin', 'alice', '2025-02-03 00:00:00', '2025-02-06 00:00:00', 'DONE', 'alice', 'HIGH'),
                        (601, 2, 3, 'GL-2', 'GoLand feature 2', '...', 'RELEASED', 'admin', 'bob',   '2025-02-04 00:00:00', '2025-02-07 00:00:00', 'DONE', 'bob',   'MEDIUM'),
                        (300, 1, 30, 'IPF-9', 'In progress feature 1', '...', 'IN_PROGRESS', 'admin', 'bob', '2025-01-10 00:00:00', '2025-01-14 00:00:00', 'IN_PROGRESS', 'carol', 'CRITICAL'),
                        (301, 1, 30, 'IPF-10','In progress feature 2','...', 'IN_PROGRESS', 'admin', 'bob', '2025-01-11 00:00:00', '2025-01-14 00:00:00', 'IN_PROGRESS', 'carol', 'HIGH'),
                        (400, 1, 40, 'PD-9', 'Partially done feature 1', '...', 'IN_PROGRESS', 'admin', 'bob', '2025-01-10 00:00:00', '2025-01-14 00:00:00', 'IN_PROGRESS', 'carol', 'CRITICAL'),
                        (401, 1, 40, 'PD-10','Partially done feature 2','...', 'RELEASED', 'admin', 'bob', '2025-01-11 00:00:00', '2025-01-14 00:00:00', 'DONE', 'carol', 'HIGH'),
                        (500, 1, 50, 'RDN-10','Release date null feature 1','...', 'RELEASED', 'admin', 'bob', '2025-01-11 00:00:00', '2025-01-14 00:00:00', 'DONE', 'carol', 'HIGH')
                        """);
    }
}

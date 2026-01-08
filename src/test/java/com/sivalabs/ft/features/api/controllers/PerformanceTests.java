package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Basic performance tests to verify queries perform adequately with larger datasets.
 * Not exhaustive load testing - just smoke tests to ensure no obvious performance issues.
 */
@WithMockOAuth2User(roles = {"ADMIN"})
class PerformanceTests extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM feature_usage");
    }

    @Test
    void shouldHandleTrendsQueryWithLargeDataset() {
        // Create 1000 usage events across 30 days
        Instant now = Instant.now();
        for (int i = 0; i < 1000; i++) {
            int dayOffset = i % 30; // Spread across 30 days
            jdbcTemplate.update(
                    "INSERT INTO feature_usage (user_id, feature_code, action_type, timestamp) VALUES (?, ?, ?, ?)",
                    "user-" + (i % 100), // 100 unique users
                    "PERF-FEAT-" + (i % 10), // 10 unique features
                    "FEATURE_VIEWED",
                    java.sql.Timestamp.from(now.minus(dayOffset, ChronoUnit.DAYS)));
        }

        // Measure query time
        Instant start = Instant.now();

        var result = mvc.get()
                .uri("/api/usage/trends?periodType=DAY&startDate="
                        + now.minus(30, ChronoUnit.DAYS).toString() + "&endDate=" + now.toString())
                .exchange();

        Duration duration = Duration.between(start, Instant.now());

        // Should complete within reasonable time
        assertThat(result).hasStatus2xxSuccessful();
        assertThat(duration.toMillis())
                .as("Trends query with 1000 events should complete in < 2s")
                .isLessThan(2000);

        // Should return valid data
        assertThat(result).bodyJson().extractingPath("$.trends").asList().isNotEmpty();
    }

    @Test
    void shouldHandleAdoptionRateWithMultipleWindows() {
        // Create feature with release
        String featureCode = createFeatureWithRelease("PERF-ADOPTION", 100);
        Instant releaseDate = Instant.now().minus(100, ChronoUnit.DAYS);

        // Create 500 events spread across 90 days
        for (int i = 0; i < 500; i++) {
            int dayOffset = i % 90;
            jdbcTemplate.update(
                    "INSERT INTO feature_usage (user_id, feature_code, action_type, timestamp) VALUES (?, ?, ?, ?)",
                    "user-" + i, // 500 unique users
                    featureCode,
                    "FEATURE_VIEWED",
                    java.sql.Timestamp.from(releaseDate.plus(dayOffset, ChronoUnit.DAYS)));
        }

        // Measure query time
        Instant start = Instant.now();

        var result = mvc.get().uri("/api/usage/adoption-rate/" + featureCode).exchange();

        Duration duration = Duration.between(start, Instant.now());

        // Should complete within reasonable time
        assertThat(result).hasStatus2xxSuccessful();
        assertThat(duration.toMillis())
                .as("Adoption rate calculation with 500 events should complete in < 1s")
                .isLessThan(1000);

        // Should return valid adoption windows
        assertThat(result).bodyJson().extractingPath("$.adoptionWindows.7").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.adoptionWindows.30").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.adoptionWindows.90").isNotNull();
    }

    @Test
    void shouldHandleSegmentAnalyticsWithLargeDataset() {
        // Create 500 mobile events
        for (int i = 0; i < 500; i++) {
            jdbcTemplate.update(
                    "INSERT INTO feature_usage (user_id, feature_code, action_type, context, timestamp) VALUES (?, ?, ?, ?, ?)",
                    "mobile-user-" + i,
                    "MOBILE-FEAT-" + (i % 20), // 20 features
                    "FEATURE_VIEWED",
                    "{\"device\":\"mobile\"}",
                    java.sql.Timestamp.from(Instant.now().minus(i % 30, ChronoUnit.DAYS)));
        }

        // Measure query time
        Instant start = Instant.now();

        var result = mvc.get().uri("/api/usage/segments?segments=mobile").exchange();

        Duration duration = Duration.between(start, Instant.now());

        // Should complete within reasonable time
        assertThat(result).hasStatus2xxSuccessful();
        assertThat(duration.toMillis())
                .as("Segment analytics with 500 events should complete in < 2s")
                .isLessThan(2000);

        // Should aggregate correctly
        assertThat(result)
                .bodyJson()
                .extractingPath("$[0].totalUsage")
                .asNumber()
                .isEqualTo(500);
    }

    /**
     * Helper to create feature with release for performance test.
     */
    private String createFeatureWithRelease(String featureCode, int daysAgo) {
        String productCode = "PERF-PROD-" + System.nanoTime();
        String prefix = "PP" + (System.nanoTime() % 100000);

        jdbcTemplate.update(
                "INSERT INTO products (code, prefix, name, description, image_url, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                productCode,
                prefix,
                "Performance Product",
                "Test",
                "http://example.com/test.png",
                "test-user",
                java.sql.Timestamp.from(Instant.now()));

        Long productId = jdbcTemplate.queryForObject("SELECT id FROM products WHERE code = ?", Long.class, productCode);

        String releaseCode = productCode + "-REL";
        Instant releaseDate = Instant.now().minus(daysAgo, ChronoUnit.DAYS);

        jdbcTemplate.update(
                "INSERT INTO releases (product_id, code, description, status, released_at, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                productId,
                releaseCode,
                "Test release",
                "RELEASED",
                java.sql.Timestamp.from(releaseDate),
                "test-user",
                java.sql.Timestamp.from(Instant.now()));

        Long releaseId = jdbcTemplate.queryForObject("SELECT id FROM releases WHERE code = ?", Long.class, releaseCode);

        jdbcTemplate.update(
                "INSERT INTO features (code, title, description, status, product_id, release_id, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                featureCode,
                "Performance Feature",
                "Test",
                "RELEASED",
                productId,
                releaseId,
                "test-user",
                java.sql.Timestamp.from(Instant.now()));

        return featureCode;
    }
}

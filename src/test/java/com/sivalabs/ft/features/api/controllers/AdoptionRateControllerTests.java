package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for AdoptionRateController.
 * Tests adoption rate calculations, feature comparisons, and release statistics.
 */
class AdoptionRateControllerTests extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM feature_usage");
    }

    /**
     * Tests for unauthenticated requests - should return 401 Unauthorized
     */
    @Nested
    class UnauthenticatedTests {

        @Test
        void shouldReturn401ForUnauthenticatedAdoptionRateRequest() {
            var result = mvc.get().uri("/api/usage/adoption-rate/TEST-FEATURE").exchange();
            assertThat(result).hasStatus(401);
        }

        @Test
        void shouldReturn401ForUnauthenticatedCompareRequest() {
            var result = mvc.get()
                    .uri("/api/usage/adoption-rate/compare?featureCodes=FEAT1,FEAT2")
                    .exchange();
            assertThat(result).hasStatus(401);
        }

        @Test
        void shouldReturn401ForUnauthenticatedReleaseStatsRequest() {
            var result = mvc.get().uri("/api/usage/release/REL-1/stats").exchange();
            assertThat(result).hasStatus(401);
        }
    }

    /**
     * Tests for authenticated users
     */
    @Nested
    @WithMockOAuth2User(roles = {"USER"})
    class AuthenticatedUserTests {

        @Test
        void shouldGetAdoptionRateForFeatureWithRelease() {
            // Create a feature with release date and usage events
            String featureCode = createFeatureWithRelease("ADOPT-FEATURE-1", 10);

            // Create usage events after release
            Instant now = Instant.now();
            createUsageEvent(featureCode, now.minus(3, ChronoUnit.DAYS));
            createUsageEvent(featureCode, now.minus(1, ChronoUnit.DAYS));
            createUsageEvent(featureCode, now);

            var result =
                    mvc.get().uri("/api/usage/adoption-rate/" + featureCode).exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify response structure
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.featureCode")
                    .asString()
                    .isEqualTo(featureCode);
            assertThat(result).bodyJson().extractingPath("$.releaseDate").isNotNull();
            assertThat(result).bodyJson().extractingPath("$.adoptionWindows").isNotNull();
            // We created 3 unique users, so expect exactly 3
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalUniqueUsers")
                    .asNumber()
                    .isEqualTo(3);
            // Overall adoption score should be > 0 (calculated from windows)
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.overallAdoptionScore")
                    .asNumber()
                    .satisfies(score -> assertThat(score.doubleValue()).isGreaterThan(0.0));

            // Verify adoption windows exist
            assertThat(result).bodyJson().extractingPath("$.adoptionWindows.7").isNotNull();
            assertThat(result).bodyJson().extractingPath("$.adoptionWindows.30").isNotNull();
            assertThat(result).bodyJson().extractingPath("$.adoptionWindows.90").isNotNull();
        }

        @Test
        void shouldReturn404ForNonExistentFeature() {
            var result = mvc.get()
                    .uri("/api/usage/adoption-rate/NON-EXISTENT-FEATURE")
                    .exchange();

            assertThat(result).hasStatus(404);
        }

        @Test
        void shouldReturn400ForFeatureWithoutRelease() {
            // Create product first with unique prefix
            long nonce = System.nanoTime();
            String productCode = "NO-REL-PROD-" + nonce;
            String prefix = "N" + (nonce % 1000000);
            jdbcTemplate.update(
                    "INSERT INTO products (code, prefix, name, description, image_url, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    productCode,
                    prefix,
                    "No Release Product " + nonce,
                    "Test product",
                    "http://example.com/image.png",
                    "test-user",
                    java.sql.Timestamp.from(Instant.now()));

            Long productId =
                    jdbcTemplate.queryForObject("SELECT id FROM products WHERE code = ?", Long.class, productCode);

            // Create feature without release
            jdbcTemplate.update(
                    "INSERT INTO features (code, title, description, status, product_id, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    "NO-RELEASE-FEATURE",
                    "Feature Without Release",
                    "Test feature",
                    "RELEASED",
                    productId,
                    "test-user",
                    java.sql.Timestamp.from(Instant.now()));

            var result =
                    mvc.get().uri("/api/usage/adoption-rate/NO-RELEASE-FEATURE").exchange();

            assertThat(result).hasStatus4xxClientError();
        }

        @Test
        void shouldCompareMultipleFeaturesWithRanking() {
            // Create two features with different adoption patterns
            String feature1 = createFeatureWithRelease("COMPARE-FEAT-1", 15);
            String feature2 = createFeatureWithRelease("COMPARE-FEAT-2", 10);

            Instant now = Instant.now();

            // Feature 1: High adoption (5 events)
            for (int i = 0; i < 5; i++) {
                createUsageEvent(feature1, now.minus(i, ChronoUnit.DAYS));
            }

            // Feature 2: Lower adoption (2 events)
            createUsageEvent(feature2, now.minus(1, ChronoUnit.DAYS));
            createUsageEvent(feature2, now);

            var result = mvc.get()
                    .uri("/api/usage/adoption-rate/compare?featureCodes=" + feature1 + "," + feature2
                            + "&windowDays=30")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify comparison structure - exactly 2 features compared
            assertThat(result).bodyJson().extractingPath("$").asList().hasSize(2);

            // Verify ranking (feature 1 should be ranked higher due to more adoption)
            assertThat(result).bodyJson().extractingPath("$[0].rank").asNumber().isEqualTo(1);
            assertThat(result).bodyJson().extractingPath("$[1].rank").asNumber().isEqualTo(2);

            // Verify metrics structure
            assertThat(result).bodyJson().extractingPath("$[0].featureCode").isNotNull();
            assertThat(result).bodyJson().extractingPath("$[0].uniqueUsers").isNotNull();
            assertThat(result).bodyJson().extractingPath("$[0].adoptionScore").isNotNull();
        }

        @Test
        void shouldReturn400ForCompareWithLessThanTwoFeatures() {
            var result = mvc.get()
                    .uri("/api/usage/adoption-rate/compare?featureCodes=SINGLE-FEATURE")
                    .exchange();

            assertThat(result).hasStatus4xxClientError();
        }

        @Test
        void shouldReturn400ForCompareWithEmptyFeatureCodes() {
            var result = mvc.get()
                    .uri("/api/usage/adoption-rate/compare?featureCodes=")
                    .exchange();

            assertThat(result).hasStatus4xxClientError();
        }

        @Test
        void shouldReturn400ForCompareWithInvalidWindowDays() {
            var result = mvc.get()
                    .uri("/api/usage/adoption-rate/compare?featureCodes=FEAT1,FEAT2&windowDays=-10")
                    .exchange();

            assertThat(result).hasStatus4xxClientError();
        }

        @Test
        void shouldGetReleaseStatsWithMultipleFeatures() {
            // Create release with features
            String releaseCode = createReleaseWithFeatures();

            var result = mvc.get()
                    .uri("/api/usage/release/" + releaseCode + "/stats")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify release stats structure
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.releaseCode")
                    .asString()
                    .isEqualTo(releaseCode);
            assertThat(result).bodyJson().extractingPath("$.releaseDate").isNotNull();
            // We created exactly 2 features in release
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalFeatures")
                    .asNumber()
                    .isEqualTo(2);
            assertThat(result).bodyJson().extractingPath("$.topAdoptedFeatures").isNotNull();
            // Overall score should be > 0 since we have usage
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.overallReleaseScore")
                    .asNumber()
                    .satisfies(score -> assertThat(score.doubleValue()).isGreaterThan(0.0));
        }

        @Test
        void shouldReturn404ForNonExistentRelease() {
            var result = mvc.get()
                    .uri("/api/usage/release/NON-EXISTENT-RELEASE/stats")
                    .exchange();

            assertThat(result).hasStatus(404);
        }

        @Test
        void shouldHandleReleaseWithNoFeatures() {
            // Create release without features
            String productCode = "TEST-PRODUCT"; // From sample data
            String releaseCode = createReleaseWithoutFeatures(productCode, "EMPTY-RELEASE");

            var result = mvc.get()
                    .uri("/api/usage/release/" + releaseCode + "/stats")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should return valid structure with zero counts
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalFeatures")
                    .asNumber()
                    .isEqualTo(0);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.overallReleaseScore")
                    .asNumber()
                    .isEqualTo(0.0);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.topAdoptedFeatures")
                    .asList()
                    .isEmpty();
        }

        @Test
        void shouldCalculateAdoptionWindowsCorrectly() {
            int daysAgoReleased = 30;
            String featureCode = createFeatureWithRelease("WINDOW-FEATURE", daysAgoReleased);

            Instant releaseDate = Instant.now().minus(daysAgoReleased, ChronoUnit.DAYS);

            // Create events relative to release date, not now
            // Within 7 days from release: 2 events
            createUsageEvent(featureCode, releaseDate.plus(3, ChronoUnit.DAYS));
            createUsageEvent(featureCode, releaseDate.plus(5, ChronoUnit.DAYS));

            // Within 30 days from release (includes 7-day events): 2 more = 4 total
            createUsageEvent(featureCode, releaseDate.plus(15, ChronoUnit.DAYS));
            createUsageEvent(featureCode, releaseDate.plus(20, ChronoUnit.DAYS));

            // Within 90 days from release (includes all above): 1 more = 5 total
            createUsageEvent(featureCode, releaseDate.plus(60, ChronoUnit.DAYS));

            var result =
                    mvc.get().uri("/api/usage/adoption-rate/" + featureCode).exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify 7-day window: events at +3 and +5 days = 2 unique users
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.adoptionWindows.7.uniqueUsers")
                    .asNumber()
                    .isEqualTo(2);

            // Verify 30-day window: includes all events except +60 days = 4 unique users
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.adoptionWindows.30.uniqueUsers")
                    .asNumber()
                    .isEqualTo(4);

            // Verify 90-day window: includes all 5 events = 5 unique users
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.adoptionWindows.90.uniqueUsers")
                    .asNumber()
                    .isEqualTo(5);
        }

        @Test
        void shouldSupportCustomWindowDays7And90() {
            // Create feature released 100 days ago
            String featureCode = createFeatureWithRelease("CUSTOM-WINDOW-FEAT", 100);
            Instant releaseDate = Instant.now().minus(100, ChronoUnit.DAYS);

            // Create events at different times
            createUsageEvent(featureCode, releaseDate.plus(5, ChronoUnit.DAYS)); // Within 7 days
            createUsageEvent(featureCode, releaseDate.plus(15, ChronoUnit.DAYS)); // Within 30 days
            createUsageEvent(featureCode, releaseDate.plus(75, ChronoUnit.DAYS)); // Within 90 days

            // Test with 7-day window
            var result7 =
                    mvc.get().uri("/api/usage/adoption-rate/" + featureCode).exchange();

            assertThat(result7).hasStatus2xxSuccessful();
            assertThat(result7)
                    .bodyJson()
                    .extractingPath("$.adoptionWindows.7.uniqueUsers")
                    .asNumber()
                    .isEqualTo(1);
            assertThat(result7)
                    .bodyJson()
                    .extractingPath("$.adoptionWindows.90.uniqueUsers")
                    .asNumber()
                    .isEqualTo(3);
        }

        @Test
        void shouldCompareThreeOrMoreFeatures() {
            // Create 3 features with different adoption levels
            String feat1 = createFeatureWithRelease("COMPARE-3-FEAT-1", 20);
            String feat2 = createFeatureWithRelease("COMPARE-3-FEAT-2", 20);
            String feat3 = createFeatureWithRelease("COMPARE-3-FEAT-3", 20);

            Instant now = Instant.now();

            // Feature 1: Highest adoption (6 events)
            for (int i = 0; i < 6; i++) {
                createUsageEvent(feat1, now.minus(i, ChronoUnit.DAYS));
            }

            // Feature 2: Medium adoption (3 events)
            for (int i = 0; i < 3; i++) {
                createUsageEvent(feat2, now.minus(i, ChronoUnit.DAYS));
            }

            // Feature 3: Lowest adoption (1 event)
            createUsageEvent(feat3, now);

            var result = mvc.get()
                    .uri("/api/usage/adoption-rate/compare?featureCodes=" + feat1 + "," + feat2 + "," + feat3
                            + "&windowDays=30")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should have exactly 3 features
            assertThat(result).bodyJson().extractingPath("$").asList().hasSize(3);

            // Verify ranking: feat1=1, feat2=2, feat3=3
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].featureCode")
                    .asString()
                    .isEqualTo(feat1);
            assertThat(result).bodyJson().extractingPath("$[0].rank").asNumber().isEqualTo(1);

            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[1].featureCode")
                    .asString()
                    .isEqualTo(feat2);
            assertThat(result).bodyJson().extractingPath("$[1].rank").asNumber().isEqualTo(2);

            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[2].featureCode")
                    .asString()
                    .isEqualTo(feat3);
            assertThat(result).bodyJson().extractingPath("$[2].rank").asNumber().isEqualTo(3);
        }

        @Test
        void shouldCalculateGrowthRatesBetweenWindows() {
            // Create feature with progressive growth
            String featureCode = createFeatureWithRelease("GROWTH-RATE-FEAT", 100);
            Instant releaseDate = Instant.now().minus(100, ChronoUnit.DAYS);

            // 7-day window: 2 users
            createUsageEvent(featureCode, releaseDate.plus(3, ChronoUnit.DAYS));
            createUsageEvent(featureCode, releaseDate.plus(6, ChronoUnit.DAYS));

            // 30-day window: 4 users total (2 new)
            createUsageEvent(featureCode, releaseDate.plus(15, ChronoUnit.DAYS));
            createUsageEvent(featureCode, releaseDate.plus(25, ChronoUnit.DAYS));

            // 90-day window: 6 users total (2 new)
            createUsageEvent(featureCode, releaseDate.plus(60, ChronoUnit.DAYS));
            createUsageEvent(featureCode, releaseDate.plus(80, ChronoUnit.DAYS));

            var result =
                    mvc.get().uri("/api/usage/adoption-rate/" + featureCode).exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Verify 7-day window has 2 users with 0 growth (first window)
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.adoptionWindows.7.uniqueUsers")
                    .asNumber()
                    .isEqualTo(2);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.adoptionWindows.7.growthRate")
                    .asNumber()
                    .isEqualTo(0.0);

            // Verify 30-day window has 4 users with 100% growth (2->4)
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.adoptionWindows.30.uniqueUsers")
                    .asNumber()
                    .isEqualTo(4);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.adoptionWindows.30.growthRate")
                    .asNumber()
                    .isEqualTo(100.0);

            // Verify 90-day window has 6 users with 50% growth (4->6)
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.adoptionWindows.90.uniqueUsers")
                    .asNumber()
                    .isEqualTo(6);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.adoptionWindows.90.growthRate")
                    .asNumber()
                    .isEqualTo(50.0);
        }

        @Test
        void shouldHandleReleaseWithNoUsageEvents() {
            // Create release with features but no usage
            String releaseCode = createReleaseWithFeaturesButNoUsage();

            var result = mvc.get()
                    .uri("/api/usage/release/" + releaseCode + "/stats")
                    .exchange();

            assertThat(result).hasStatus2xxSuccessful();

            // Should have features but no usage
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalFeatures")
                    .asNumber()
                    .isEqualTo(2);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.featuresWithUsage")
                    .asNumber()
                    .isEqualTo(0);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalUniqueUsers")
                    .asNumber()
                    .isEqualTo(0);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.totalUsage")
                    .asNumber()
                    .isEqualTo(0);

            // Overall score should be low due to zero coverage
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.overallReleaseScore")
                    .asNumber()
                    .isEqualTo(0.0);
        }

        /**
         * Helper: Create a feature with a release that has a release date X days ago.
         */
        private String createFeatureWithRelease(String featureCode, int daysAgoReleased) {
            // Create test product with unique prefix
            long nonce = System.nanoTime();
            String productCode = "TST-PROD-" + nonce;
            String prefix = "P" + (nonce % 1000000); // Unique prefix
            jdbcTemplate.update(
                    "INSERT INTO products (code, prefix, name, description, image_url, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    productCode,
                    prefix,
                    "Test Product " + nonce,
                    "Test product",
                    "http://example.com/test.png",
                    "test-user",
                    java.sql.Timestamp.from(Instant.now()));

            // Get product ID
            Long productId =
                    jdbcTemplate.queryForObject("SELECT id FROM products WHERE code = ?", Long.class, productCode);

            // Create release
            Instant releaseDate = Instant.now().minus(daysAgoReleased, ChronoUnit.DAYS);
            String releaseCode = productCode + "-REL";

            // Create release
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

            // Get release ID
            Long releaseId =
                    jdbcTemplate.queryForObject("SELECT id FROM releases WHERE code = ?", Long.class, releaseCode);

            // Create feature with release
            jdbcTemplate.update(
                    "INSERT INTO features (code, title, description, status, product_id, release_id, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    featureCode,
                    "Test Feature " + featureCode,
                    "Test feature for adoption rate",
                    "RELEASED",
                    productId,
                    releaseId,
                    "test-user",
                    java.sql.Timestamp.from(Instant.now()));

            return featureCode;
        }

        /**
         * Helper: Create a release without features.
         */
        private String createReleaseWithoutFeatures(String productCode, String releaseSuffix) {
            // Create test product with unique prefix
            long nonce = System.nanoTime();
            String prefix = "R" + (nonce % 1000000); // Unique prefix
            jdbcTemplate.update(
                    "INSERT INTO products (code, prefix, name, description, image_url, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    productCode,
                    prefix,
                    "Test Product " + nonce,
                    "Test product",
                    "http://example.com/test.png",
                    "test-user",
                    java.sql.Timestamp.from(Instant.now()));

            // Get product ID
            Long productId =
                    jdbcTemplate.queryForObject("SELECT id FROM products WHERE code = ?", Long.class, productCode);

            String releaseCode = productCode + "-" + releaseSuffix;

            // Create release
            jdbcTemplate.update(
                    "INSERT INTO releases (product_id, code, description, status, released_at, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    productId,
                    releaseCode,
                    "Empty test release",
                    "RELEASED",
                    java.sql.Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)),
                    "test-user",
                    java.sql.Timestamp.from(Instant.now()));

            return releaseCode;
        }

        /**
         * Helper: Create a release with features and usage.
         */
        private String createReleaseWithFeatures() {
            // Create product with unique prefix
            long nonce = System.nanoTime();
            String productCode = "REL-STATS-PROD-" + nonce;
            String prefix = "S" + (nonce % 1000000); // Unique prefix
            jdbcTemplate.update(
                    "INSERT INTO products (code, prefix, name, description, image_url, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    productCode,
                    prefix,
                    "Release Stats Product " + nonce,
                    "Test product",
                    "http://example.com/release.png",
                    "test-user",
                    java.sql.Timestamp.from(Instant.now()));

            Long productId =
                    jdbcTemplate.queryForObject("SELECT id FROM products WHERE code = ?", Long.class, productCode);

            // Create release
            String releaseCode = productCode + "-REL";
            Instant releaseDate = Instant.now().minus(15, ChronoUnit.DAYS);

            jdbcTemplate.update(
                    "INSERT INTO releases (product_id, code, description, status, released_at, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    productId,
                    releaseCode,
                    "Test release with features",
                    "RELEASED",
                    java.sql.Timestamp.from(releaseDate),
                    "test-user",
                    java.sql.Timestamp.from(Instant.now()));

            Long releaseId =
                    jdbcTemplate.queryForObject("SELECT id FROM releases WHERE code = ?", Long.class, releaseCode);

            // Create features in this release
            String feature1 = "REL-FEAT-1";
            String feature2 = "REL-FEAT-2";

            jdbcTemplate.update(
                    "INSERT INTO features (code, title, description, status, product_id, release_id, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    feature1,
                    "Feature 1",
                    "Test feature 1",
                    "RELEASED",
                    productId,
                    releaseId,
                    "test-user",
                    java.sql.Timestamp.from(Instant.now()));

            jdbcTemplate.update(
                    "INSERT INTO features (code, title, description, status, product_id, release_id, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    feature2,
                    "Feature 2",
                    "Test feature 2",
                    "RELEASED",
                    productId,
                    releaseId,
                    "test-user",
                    java.sql.Timestamp.from(Instant.now()));

            // Create usage events for features
            Instant now = Instant.now();
            createUsageEvent(feature1, now.minus(5, ChronoUnit.DAYS));
            createUsageEvent(feature1, now.minus(1, ChronoUnit.DAYS));
            createUsageEvent(feature2, now);

            return releaseCode;
        }

        /**
         * Helper: Create release with features but no usage events.
         */
        private String createReleaseWithFeaturesButNoUsage() {
            // Create product
            long nonce = System.nanoTime();
            String productCode = "NO-USAGE-PROD-" + nonce;
            String prefix = "NU" + (nonce % 1000000);
            jdbcTemplate.update(
                    "INSERT INTO products (code, prefix, name, description, image_url, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    productCode,
                    prefix,
                    "No Usage Product " + nonce,
                    "Test product",
                    "http://example.com/no-usage.png",
                    "test-user",
                    java.sql.Timestamp.from(Instant.now()));

            Long productId =
                    jdbcTemplate.queryForObject("SELECT id FROM products WHERE code = ?", Long.class, productCode);

            // Create release
            String releaseCode = productCode + "-REL";
            jdbcTemplate.update(
                    "INSERT INTO releases (product_id, code, description, status, released_at, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    productId,
                    releaseCode,
                    "Release with no usage",
                    "RELEASED",
                    java.sql.Timestamp.from(Instant.now().minus(15, ChronoUnit.DAYS)),
                    "test-user",
                    java.sql.Timestamp.from(Instant.now()));

            Long releaseId =
                    jdbcTemplate.queryForObject("SELECT id FROM releases WHERE code = ?", Long.class, releaseCode);

            // Create features but don't create usage events
            jdbcTemplate.update(
                    "INSERT INTO features (code, title, description, status, product_id, release_id, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    "NO-USAGE-FEAT-1",
                    "Feature 1 No Usage",
                    "Test feature",
                    "RELEASED",
                    productId,
                    releaseId,
                    "test-user",
                    java.sql.Timestamp.from(Instant.now()));

            jdbcTemplate.update(
                    "INSERT INTO features (code, title, description, status, product_id, release_id, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    "NO-USAGE-FEAT-2",
                    "Feature 2 No Usage",
                    "Test feature",
                    "RELEASED",
                    productId,
                    releaseId,
                    "test-user",
                    java.sql.Timestamp.from(Instant.now()));

            return releaseCode;
        }

        /**
         * Helper: Create a usage event for a feature.
         */
        private void createUsageEvent(String featureCode, Instant timestamp) {
            jdbcTemplate.update(
                    "INSERT INTO feature_usage (user_id, feature_code, action_type, timestamp) "
                            + "VALUES (?, ?, ?, ?)",
                    "test-user-" + System.nanoTime(), // Unique user for each event
                    featureCode,
                    "FEATURE_VIEWED",
                    java.sql.Timestamp.from(timestamp));
        }
    }
}

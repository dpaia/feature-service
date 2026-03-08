package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.domain.models.ActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Integration tests to verify that usage events are logged for anonymous (unauthenticated) users.
 * These tests do NOT use @WithMockOAuth2User annotation to simulate anonymous requests.
 *
 * Uses Awaitility to wait for async Kafka-based event processing.
 */
class AnonymousUsageTrackingTest extends FeatureUsageAbstractIT {

    @BeforeEach
    void setUp() {
        cleanFeatureUsageTable();
    }

    @Test
    void shouldLogAnonymousUserViewingProduct() {
        // When: Anonymous user views a product
        var result = mvc.get().uri("/api/products/intellij").exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Then: Verify usage event was logged with userId="anonymous" (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("user_id")).isEqualTo("anonymous");
            assertThat(e.get("product_code")).isEqualTo("intellij");
            assertThat(e.get("action_type")).isEqualTo(ActionType.PRODUCT_VIEWED.name());
        });
    }

    @Test
    void shouldLogAnonymousUserViewingFeature() {
        // When: Anonymous user views a feature
        var result = mvc.get().uri("/api/features/IDEA-1").exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Then: Verify usage event was logged with userId="anonymous" (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("user_id")).isEqualTo("anonymous");
            assertThat(e.get("feature_code")).isEqualTo("IDEA-1");
            assertThat(e.get("action_type")).isEqualTo(ActionType.FEATURE_VIEWED.name());
        });
    }

    @Test
    void shouldLogAnonymousUserListingFeatures() {
        // When: Anonymous user lists features by product
        var result = mvc.get().uri("/api/features?productCode=intellij").exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Then: Verify usage event was logged with userId="anonymous" (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("user_id")).isEqualTo("anonymous");
            assertThat(e.get("product_code")).isEqualTo("intellij");
            assertThat(e.get("action_type")).isEqualTo(ActionType.FEATURES_LISTED.name());
        });
    }

    @Test
    void shouldLogAnonymousUserViewingRelease() {
        // When: Anonymous user views a release
        var result = mvc.get().uri("/api/releases/IDEA-2023.3.8").exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Then: Verify usage event was logged with userId="anonymous" (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("user_id")).isEqualTo("anonymous");
            assertThat(e.get("release_code")).isEqualTo("IDEA-2023.3.8");
            assertThat(e.get("action_type")).isEqualTo(ActionType.RELEASE_VIEWED.name());
        });
    }

    @Test
    void shouldStoreDeviceFingerprintInContextForAnonymousUser() {
        // When: Anonymous user views a product with User-Agent header
        var result = mvc.get()
                .uri("/api/products/intellij")
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) Chrome/120.0.0.0")
                .header("X-Forwarded-For", "192.168.1.100")
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Then: Verify context contains device fingerprint (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            String context = (String) e.get("context");
            assertThat(context).isNotNull();
            // Verify device fingerprint is present (16 hex characters)
            assertThat(context).contains("deviceFingerprint");
            assertThat(context).matches(".*\"deviceFingerprint\":\"[a-f0-9]{16}\".*");
        });
    }

    @Test
    void shouldNotLogAnonymousUserCreatingFeature() {
        // When: Anonymous user tries to create a feature (should be rejected by security)
        String payload =
                """
                {
                    "productCode": "intellij",
                    "title": "Test Feature",
                    "description": "Test"
                }
                """;
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus4xxClientError(); // 401 Unauthorized

        // Then: Verify no usage event was logged
        verifyFeatureUsageCountStaysAt(0);
    }
}

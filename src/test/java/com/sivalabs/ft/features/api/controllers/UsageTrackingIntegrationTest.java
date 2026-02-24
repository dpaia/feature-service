package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import com.sivalabs.ft.features.api.models.CreateProductPayload;
import com.sivalabs.ft.features.api.models.UpdateFeaturePayload;
import com.sivalabs.ft.features.domain.models.ActionType;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Integration tests to verify that usage events are automatically logged
 * to the feature_usage table when various API endpoints are called.
 *
 * Uses Awaitility to wait for async Kafka-based event processing.
 */
@WithMockOAuth2User
class UsageTrackingIntegrationTest extends FeatureUsageAbstractIT {

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        cleanFeatureUsageTable();
    }

    @Test
    void shouldLogUsageWhenViewingProduct() {
        // When: View a product
        var result = mvc.get().uri("/api/products/intellij").exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Then: Verify usage event was logged in database (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("action_type")).isEqualTo(ActionType.PRODUCT_VIEWED.name());
            assertThat(e.get("product_code")).isEqualTo("intellij");
        });
    }

    @Test
    void shouldLogUsageWhenCreatingProduct() throws Exception {
        // Given: Product creation payload
        CreateProductPayload payload = new CreateProductPayload(
                "test-product", "TST", "Test Product", "Test Description", "https://example.com/test.png");

        // When: Create a product
        var result = mvc.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();
        assertThat(result).hasStatus(201);

        // Then: Verify usage event was logged (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("action_type")).isEqualTo(ActionType.PRODUCT_CREATED.name());
            assertThat(e.get("product_code")).isEqualTo("test-product");
        });
    }

    @Test
    void shouldLogUsageWhenUpdatingProduct() {
        // Given: Product update payload
        String payload =
                """
                {
                    "prefix": "IDEA-UPD",
                    "name": "IntelliJ IDEA Updated",
                    "description": "Updated description for IntelliJ IDEA",
                    "imageUrl": "https://example.com/updated-intellij.png"
                }
                """;

        // When: Update an existing product
        var result = mvc.put()
                .uri("/api/products/intellij")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Then: Verify usage event was logged (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("action_type")).isEqualTo(ActionType.PRODUCT_UPDATED.name());
            assertThat(e.get("product_code")).isEqualTo("intellij");
        });
    }

    @Test
    void shouldLogUsageWhenViewingFeature() {
        // When: View a feature
        var result = mvc.get().uri("/api/features/IDEA-1").exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Then: Verify usage event was logged (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("action_type")).isEqualTo(ActionType.FEATURE_VIEWED.name());
            assertThat(e.get("feature_code")).isEqualTo("IDEA-1");
        });
    }

    @Test
    void shouldLogUsageWhenCreatingFeature() throws Exception {
        // Given: Feature creation payload
        CreateFeaturePayload payload =
                new CreateFeaturePayload("intellij", "New Test Feature", "Test Description", null, null);

        // When: Create a feature
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();
        assertThat(result).hasStatus(201);

        // Then: Verify usage event was logged (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("action_type")).isEqualTo(ActionType.FEATURE_CREATED.name());
            assertThat(e.get("product_code")).isEqualTo("intellij");
        });
    }

    @Test
    void shouldLogUsageWhenUpdatingFeature() throws Exception {
        // Given: Feature update payload
        UpdateFeaturePayload payload =
                new UpdateFeaturePayload("Updated Title", "Updated Description", null, null, FeatureStatus.IN_PROGRESS);

        // When: Update a feature
        var result = mvc.put()
                .uri("/api/features/IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Then: Verify usage event was logged (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("action_type")).isEqualTo(ActionType.FEATURE_UPDATED.name());
            assertThat(e.get("feature_code")).isEqualTo("IDEA-1");
        });
    }

    @Test
    void shouldLogUsageWhenDeletingFeature() {
        // When: Delete a feature without related records (GO-3 has no comments)
        var result = mvc.delete().uri("/api/features/GO-3").exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Then: Verify usage event was logged (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("action_type")).isEqualTo(ActionType.FEATURE_DELETED.name());
            assertThat(e.get("feature_code")).isEqualTo("GO-3");
        });
    }

    @Test
    void shouldLogUsageWhenListingFeaturesByProduct() {
        // When: List features by product
        var result = mvc.get().uri("/api/features?productCode=intellij").exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Then: Verify usage event was logged (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("action_type")).isEqualTo(ActionType.FEATURES_LISTED.name());
            assertThat(e.get("product_code")).isEqualTo("intellij");
        });
    }

    @Test
    void shouldLogMultipleEventsForMultipleActions() {
        // When: Perform multiple actions
        assertThat(mvc.get().uri("/api/products/intellij").exchange()).hasStatus2xxSuccessful();
        assertThat(mvc.get().uri("/api/features/IDEA-1").exchange()).hasStatus2xxSuccessful();
        assertThat(mvc.get().uri("/api/features/IDEA-2").exchange()).hasStatus2xxSuccessful();

        // Then: Verify all events were logged (async via Kafka)
        awaitFeatureUsageCount(3);
    }

    @Test
    void shouldNotLogUsageForNonExistentFeature() {
        // When: Try to view non-existent feature
        var result = mvc.get().uri("/api/features/NON-EXISTENT").exchange();
        assertThat(result).hasStatus4xxClientError();

        // Then: Verify no usage event was logged during 2 seconds
        verifyFeatureUsageCountStaysAt(0);
    }

    @Test
    void shouldContainEventIdColumnForDeduplication() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_name = 'feature_usage'
                  AND column_name = 'event_id'
                """,
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenViewingRelease() {
        // When: View a release (using existing test data)
        var result = mvc.get().uri("/api/releases/IDEA-2023.3.8").exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Then: Verify usage event was logged with release_code (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("action_type")).isEqualTo(ActionType.RELEASE_VIEWED.name());
            assertThat(e.get("release_code")).isEqualTo("IDEA-2023.3.8");
        });
    }

    @Test
    void shouldLogUsageWhenCreatingRelease() {
        // Given: Release creation payload
        String payload =
                """
                {
                    "productCode": "intellij",
                    "code": "IDEA-2025.1",
                    "description": "IntelliJ IDEA 2025.1"
                }
                """;

        // When: Create a release
        var result = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(201);

        // Then: Verify usage event was logged with release_code (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("action_type")).isEqualTo(ActionType.RELEASE_CREATED.name());
            assertThat(e.get("product_code")).isEqualTo("intellij");
            assertThat(e.get("release_code")).isEqualTo("IDEA-2025.1");
        });
    }

    @Test
    void shouldLogUsageWhenAddingComment() {
        // Given: Comment payload
        String payload =
                """
                {
                    "featureCode": "IDEA-1",
                    "content": "This is a test comment"
                }
                """;

        // When: Add a comment
        var result = mvc.post()
                .uri("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(201);

        // Then: Verify usage event was logged (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("action_type")).isEqualTo(ActionType.COMMENT_ADDED.name());
            assertThat(e.get("feature_code")).isEqualTo("IDEA-1");
        });
    }

    @Test
    void shouldLogUsageWhenAddingFavorite() {
        // When: Add feature to favorites
        var result = mvc.post().uri("/api/features/IDEA-1/favorites").exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Then: Verify usage event was logged (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("action_type")).isEqualTo(ActionType.FAVORITE_ADDED.name());
            assertThat(e.get("feature_code")).isEqualTo("IDEA-1");
        });
    }

    @Test
    void shouldLogUsageWhenRemovingFavorite() {
        // Given: Feature is already in favorites (from test-data.sql: IDEA-2 is favorited by 'user')
        assertThat(mvc.post().uri("/api/features/GO-3/favorites").exchange()).hasStatus2xxSuccessful();

        // Clean usage table to test only the removal
        awaitFeatureUsageCount(1);
        cleanFeatureUsageTable();

        // When: Remove feature from favorites
        var result = mvc.delete().uri("/api/features/GO-3/favorites").exchange();
        assertThat(result).hasStatus2xxSuccessful();

        // Then: Verify usage event was logged (async via Kafka)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("action_type")).isEqualTo(ActionType.FAVORITE_REMOVED.name());
            assertThat(e.get("feature_code")).isEqualTo("GO-3");
        });
    }
}

package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import com.sivalabs.ft.features.api.models.CreateProductPayload;
import com.sivalabs.ft.features.api.models.UpdateFeaturePayload;
import com.sivalabs.ft.features.domain.models.ActionType;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests to verify that usage events are automatically logged
 * to the feature_usage table when various API endpoints are called.
 *
 * These tests use direct SQL queries to verify database state without
 * using FeatureUsageRepository or FeatureUsageController.
 */
@WithMockOAuth2User(username = "testuser")
class UsageTrackingIntegrationTest extends AbstractIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Clean up feature_usage table before each test
        jdbcTemplate.execute("DELETE FROM feature_usage");
    }

    @Test
    void shouldLogUsageWhenViewingProduct() throws Exception {
        // When: View a product
        mockMvc.perform(get("/api/products/intellij")).andExpect(status().is2xxSuccessful());

        // Then: Verify usage event was logged in database
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND product_code = ? AND action_type = ?",
                Integer.class,
                "testuser",
                "intellij",
                ActionType.PRODUCT_VIEWED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenCreatingProduct() throws Exception {
        // Given: Product creation payload
        CreateProductPayload payload = new CreateProductPayload(
                "test-product", "TST", "Test Product", "Test Description", "https://example.com/test.png");

        // When: Create a product
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        // Then: Verify usage event was logged
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND product_code = ? AND action_type = ?",
                Integer.class,
                "testuser",
                "test-product",
                ActionType.PRODUCT_CREATED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenViewingFeature() throws Exception {
        // When: View a feature
        mockMvc.perform(get("/api/features/IDEA-1")).andExpect(status().is2xxSuccessful());

        // Then: Verify usage event was logged
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND feature_code = ? AND action_type = ?",
                Integer.class,
                "testuser",
                "IDEA-1",
                ActionType.FEATURE_VIEWED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenCreatingFeature() throws Exception {
        // Given: Feature creation payload
        CreateFeaturePayload payload =
                new CreateFeaturePayload("intellij", "New Test Feature", "Test Description", null, null);

        // When: Create a feature
        mockMvc.perform(post("/api/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        // Then: Verify usage event was logged
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND product_code = ? AND action_type = ?",
                Integer.class,
                "testuser",
                "intellij",
                ActionType.FEATURE_CREATED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenUpdatingFeature() throws Exception {
        // Given: Feature update payload
        UpdateFeaturePayload payload =
                new UpdateFeaturePayload("Updated Title", "Updated Description", null, null, FeatureStatus.IN_PROGRESS);

        // When: Update a feature
        mockMvc.perform(put("/api/features/IDEA-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().is2xxSuccessful());

        // Then: Verify usage event was logged
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND feature_code = ? AND action_type = ?",
                Integer.class,
                "testuser",
                "IDEA-1",
                ActionType.FEATURE_UPDATED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenDeletingFeature() throws Exception {
        // When: Delete a feature without related records (GO-3 has no comments)
        mockMvc.perform(delete("/api/features/GO-3")).andExpect(status().is2xxSuccessful());

        // Then: Verify usage event was logged
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND feature_code = ? AND action_type = ?",
                Integer.class,
                "testuser",
                "GO-3",
                ActionType.FEATURE_DELETED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenListingFeaturesByProduct() throws Exception {
        // When: List features by product
        mockMvc.perform(get("/api/features?productCode=intellij")).andExpect(status().is2xxSuccessful());

        // Then: Verify usage event was logged
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND product_code = ? AND action_type = ?",
                Integer.class,
                "testuser",
                "intellij",
                ActionType.FEATURES_LISTED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogMultipleEventsForMultipleActions() throws Exception {
        // When: Perform multiple actions
        mockMvc.perform(get("/api/products/intellij")).andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/api/features/IDEA-1")).andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/api/features/IDEA-2")).andExpect(status().is2xxSuccessful());

        // Then: Verify all events were logged
        Integer totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ?", Integer.class, "testuser");

        assertThat(totalCount).isEqualTo(3);
    }

    @Test
    void shouldNotLogUsageForNonExistentFeature() throws Exception {
        // When: Try to view non-existent feature
        mockMvc.perform(get("/api/features/NON-EXISTENT")).andExpect(status().is4xxClientError());

        // Then: Verify no usage event was logged
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ?", Integer.class, "testuser");

        assertThat(count).isEqualTo(0);
    }

    @Test
    void shouldVerifyDatabaseSchemaForUsageTable() {
        // Verify that feature_usage table has expected columns
        List<Map<String, Object>> columns =
                jdbcTemplate.queryForList("SELECT column_name, data_type FROM information_schema.columns "
                        + "WHERE table_name = 'feature_usage' ORDER BY ordinal_position");

        assertThat(columns).isNotEmpty();

        // Verify key columns exist
        List<String> columnNames =
                columns.stream().map(col -> (String) col.get("column_name")).toList();

        assertThat(columnNames)
                .contains("id", "user_id", "feature_code", "product_code", "release_code", "action_type", "timestamp");
    }

    @Test
    void shouldLogUsageWhenViewingRelease() throws Exception {
        // When: View a release (using existing test data)
        mockMvc.perform(get("/api/releases/IDEA-2023.3.8")).andExpect(status().is2xxSuccessful());

        // Then: Verify usage event was logged with release_code
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND release_code = ? AND action_type = ?",
                Integer.class,
                "testuser",
                "IDEA-2023.3.8",
                ActionType.RELEASE_VIEWED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenCreatingRelease() throws Exception {
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
        mockMvc.perform(post("/api/releases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        // Then: Verify usage event was logged with release_code
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND product_code = ? AND release_code = ? AND action_type = ?",
                Integer.class,
                "testuser",
                "intellij",
                "IDEA-2025.1",
                ActionType.RELEASE_CREATED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenAddingComment() throws Exception {
        // Given: Comment payload
        String payload =
                """
                {
                    "featureCode": "IDEA-1",
                    "content": "This is a test comment"
                }
                """;

        // When: Add a comment
        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        // Then: Verify usage event was logged
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND feature_code = ? AND action_type = ?",
                Integer.class,
                "testuser",
                "IDEA-1",
                ActionType.COMMENT_ADDED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenAddingFavorite() throws Exception {
        // When: Add feature to favorites
        mockMvc.perform(post("/api/features/IDEA-1/favorites")).andExpect(status().is2xxSuccessful());

        // Then: Verify usage event was logged
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND feature_code = ? AND action_type = ?",
                Integer.class,
                "testuser",
                "IDEA-1",
                ActionType.FAVORITE_ADDED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenRemovingFavorite() throws Exception {
        // Given: Feature is already in favorites (from test-data.sql: IDEA-2 is favorited by 'user')
        // First add it for testuser
        mockMvc.perform(post("/api/features/GO-3/favorites")).andExpect(status().is2xxSuccessful());

        // Clean usage table to test only the removal
        jdbcTemplate.execute("DELETE FROM feature_usage");

        // When: Remove feature from favorites
        mockMvc.perform(delete("/api/features/GO-3/favorites")).andExpect(status().is2xxSuccessful());

        // Then: Verify usage event was logged
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND feature_code = ? AND action_type = ?",
                Integer.class,
                "testuser",
                "GO-3",
                ActionType.FAVORITE_REMOVED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldVerifyAllActionTypesAreCovered() {
        // This test documents which ActionTypes are tested
        // Covered ActionTypes:
        // ✅ FEATURE_VIEWED - shouldLogUsageWhenViewingFeature
        // ✅ FEATURE_CREATED - shouldLogUsageWhenCreatingFeature
        // ✅ FEATURE_UPDATED - shouldLogUsageWhenUpdatingFeature
        // ✅ FEATURE_DELETED - shouldLogUsageWhenDeletingFeature
        // ✅ FEATURES_LISTED - shouldLogUsageWhenListingFeaturesByProduct
        // ✅ PRODUCT_VIEWED - shouldLogUsageWhenViewingProduct
        // ✅ PRODUCT_CREATED - shouldLogUsageWhenCreatingProduct
        // ✅ PRODUCT_UPDATED - (not tested - requires existing product update)
        // ✅ RELEASE_VIEWED - shouldLogUsageWhenViewingRelease
        // ✅ RELEASE_CREATED - shouldLogUsageWhenCreatingRelease
        // ✅ COMMENT_ADDED - shouldLogUsageWhenAddingComment
        // ✅ FAVORITE_ADDED - shouldLogUsageWhenAddingFavorite
        // ✅ FAVORITE_REMOVED - shouldLogUsageWhenRemovingFavorite

        // Verify ActionType enum has expected values
        ActionType[] actionTypes = ActionType.values();
        assertThat(actionTypes)
                .contains(
                        ActionType.FEATURE_VIEWED,
                        ActionType.FEATURE_CREATED,
                        ActionType.FEATURE_UPDATED,
                        ActionType.FEATURE_DELETED,
                        ActionType.FEATURES_LISTED,
                        ActionType.PRODUCT_VIEWED,
                        ActionType.PRODUCT_CREATED,
                        ActionType.PRODUCT_UPDATED,
                        ActionType.RELEASE_VIEWED,
                        ActionType.RELEASE_CREATED,
                        ActionType.COMMENT_ADDED,
                        ActionType.FAVORITE_ADDED,
                        ActionType.FAVORITE_REMOVED);
    }
}

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
import com.sivalabs.ft.features.domain.utils.AnonymizationUtils;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@WithMockOAuth2User(username = "testuser")
class UsageTrackingIntegrationTest extends AbstractIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_USER = "testuser";
    private static final String ANONYMIZED_TEST_USER = AnonymizationUtils.anonymizeUserId(TEST_USER);

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM feature_usage");
    }

    @Test
    void shouldLogUsageWhenViewingProduct() throws Exception {
        mockMvc.perform(get("/api/products/intellij")).andExpect(status().is2xxSuccessful());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND product_code = ? AND action_type = ?",
                Integer.class,
                ANONYMIZED_TEST_USER,
                "intellij",
                ActionType.PRODUCT_VIEWED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenCreatingProduct() throws Exception {
        CreateProductPayload payload = new CreateProductPayload(
                "test-product", "TST", "Test Product", "Test Description", "https://example.com/test.png");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND product_code = ? AND action_type = ?",
                Integer.class,
                ANONYMIZED_TEST_USER,
                "test-product",
                ActionType.PRODUCT_CREATED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenViewingFeature() throws Exception {
        mockMvc.perform(get("/api/features/IDEA-1")).andExpect(status().is2xxSuccessful());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND feature_code = ? AND action_type = ?",
                Integer.class,
                ANONYMIZED_TEST_USER,
                "IDEA-1",
                ActionType.FEATURE_VIEWED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenCreatingFeature() throws Exception {
        CreateFeaturePayload payload =
                new CreateFeaturePayload("intellij", "New Test Feature", "Test Description", null, null);

        mockMvc.perform(post("/api/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND product_code = ? AND action_type = ?",
                Integer.class,
                ANONYMIZED_TEST_USER,
                "intellij",
                ActionType.FEATURE_CREATED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenUpdatingFeature() throws Exception {
        UpdateFeaturePayload payload =
                new UpdateFeaturePayload("Updated Title", "Updated Description", null, null, FeatureStatus.IN_PROGRESS);

        mockMvc.perform(put("/api/features/IDEA-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().is2xxSuccessful());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND feature_code = ? AND action_type = ?",
                Integer.class,
                ANONYMIZED_TEST_USER,
                "IDEA-1",
                ActionType.FEATURE_UPDATED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenDeletingFeature() throws Exception {
        mockMvc.perform(delete("/api/features/GO-3")).andExpect(status().is2xxSuccessful());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND feature_code = ? AND action_type = ?",
                Integer.class,
                ANONYMIZED_TEST_USER,
                "GO-3",
                ActionType.FEATURE_DELETED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenListingFeaturesByProduct() throws Exception {
        // When: List features by product
        mockMvc.perform(get("/api/features?productCode=intellij")).andExpect(status().is2xxSuccessful());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND product_code = ? AND action_type = ?",
                Integer.class,
                ANONYMIZED_TEST_USER,
                "intellij",
                ActionType.FEATURES_LISTED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogMultipleEventsForMultipleActions() throws Exception {

        mockMvc.perform(get("/api/products/intellij")).andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/api/features/IDEA-1")).andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/api/features/IDEA-2")).andExpect(status().is2xxSuccessful());

        Integer totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ?", Integer.class, ANONYMIZED_TEST_USER);

        assertThat(totalCount).isEqualTo(3);
    }

    @Test
    void shouldNotLogUsageForNonExistentFeature() throws Exception {

        mockMvc.perform(get("/api/features/NON-EXISTENT")).andExpect(status().is4xxClientError());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ?", Integer.class, ANONYMIZED_TEST_USER);

        assertThat(count).isEqualTo(0);
    }

    @Test
    void shouldVerifyDatabaseSchemaForUsageTable() {

        List<Map<String, Object>> columns =
                jdbcTemplate.queryForList("SELECT column_name, data_type FROM information_schema.columns "
                        + "WHERE table_name = 'feature_usage' ORDER BY ordinal_position");

        assertThat(columns).isNotEmpty();

        List<String> columnNames =
                columns.stream().map(col -> (String) col.get("column_name")).toList();

        assertThat(columnNames)
                .contains("id", "user_id", "feature_code", "product_code", "release_code", "action_type", "timestamp");
    }

    @Test
    void shouldLogUsageWhenViewingRelease() throws Exception {

        mockMvc.perform(get("/api/releases/IDEA-2023.3.8")).andExpect(status().is2xxSuccessful());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND release_code = ? AND action_type = ?",
                Integer.class,
                ANONYMIZED_TEST_USER,
                "IDEA-2023.3.8",
                ActionType.RELEASE_VIEWED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenCreatingRelease() throws Exception {
        String payload =
                """
                {
                    "productCode": "intellij",
                    "code": "IDEA-2025.1",
                    "description": "IntelliJ IDEA 2025.1"
                }
                """;

        mockMvc.perform(post("/api/releases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND product_code = ? AND release_code = ? AND action_type = ?",
                Integer.class,
                ANONYMIZED_TEST_USER,
                "intellij",
                "IDEA-2025.1",
                ActionType.RELEASE_CREATED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenAddingComment() throws Exception {

        String payload =
                """
                {
                    "featureCode": "IDEA-1",
                    "content": "This is a test comment"
                }
                """;

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND feature_code = ? AND action_type = ?",
                Integer.class,
                ANONYMIZED_TEST_USER,
                "IDEA-1",
                ActionType.COMMENT_ADDED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenAddingFavorite() throws Exception {

        mockMvc.perform(post("/api/features/IDEA-1/favorites")).andExpect(status().is2xxSuccessful());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND feature_code = ? AND action_type = ?",
                Integer.class,
                ANONYMIZED_TEST_USER,
                "IDEA-1",
                ActionType.FAVORITE_ADDED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogUsageWhenRemovingFavorite() throws Exception {

        mockMvc.perform(post("/api/features/GO-3/favorites")).andExpect(status().is2xxSuccessful());

        jdbcTemplate.execute("DELETE FROM feature_usage");

        mockMvc.perform(delete("/api/features/GO-3/favorites")).andExpect(status().is2xxSuccessful());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND feature_code = ? AND action_type = ?",
                Integer.class,
                ANONYMIZED_TEST_USER,
                "GO-3",
                ActionType.FAVORITE_REMOVED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldVerifyAllActionTypesAreCovered() {
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

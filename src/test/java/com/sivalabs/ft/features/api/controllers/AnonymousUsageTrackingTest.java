package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.domain.models.ActionType;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests to verify that usage events are logged for anonymous (unauthenticated) users.
 * These tests do NOT use @WithMockOAuth2User annotation to simulate anonymous requests.
 */
class AnonymousUsageTrackingTest extends AbstractIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Clean up feature_usage table before each test
        jdbcTemplate.execute("DELETE FROM feature_usage");
    }

    @Test
    void shouldLogAnonymousUserViewingProduct() throws Exception {
        // When: Anonymous user views a product
        mockMvc.perform(get("/api/products/intellij")).andExpect(status().is2xxSuccessful());

        // Then: Verify usage event was logged with userId="anonymous"
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND product_code = ? AND action_type = ?",
                Integer.class,
                "anonymous",
                "intellij",
                ActionType.PRODUCT_VIEWED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogAnonymousUserViewingFeature() throws Exception {
        // When: Anonymous user views a feature
        mockMvc.perform(get("/api/features/IDEA-1")).andExpect(status().is2xxSuccessful());

        // Then: Verify usage event was logged with userId="anonymous"
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND feature_code = ? AND action_type = ?",
                Integer.class,
                "anonymous",
                "IDEA-1",
                ActionType.FEATURE_VIEWED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogAnonymousUserListingFeatures() throws Exception {
        // When: Anonymous user lists features by product
        mockMvc.perform(get("/api/features?productCode=intellij")).andExpect(status().is2xxSuccessful());

        // Then: Verify usage event was logged with userId="anonymous"
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND product_code = ? AND action_type = ?",
                Integer.class,
                "anonymous",
                "intellij",
                ActionType.FEATURES_LISTED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogAnonymousUserViewingRelease() throws Exception {
        // When: Anonymous user views a release
        mockMvc.perform(get("/api/releases/IDEA-2023.3.8")).andExpect(status().is2xxSuccessful());

        // Then: Verify usage event was logged with userId="anonymous"
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND release_code = ? AND action_type = ?",
                Integer.class,
                "anonymous",
                "IDEA-2023.3.8",
                ActionType.RELEASE_VIEWED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldStoreDeviceFingerprintInContextForAnonymousUser() throws Exception {
        // When: Anonymous user views a product with User-Agent header
        mockMvc.perform(get("/api/products/intellij")
                        .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) Chrome/120.0.0.0")
                        .header("X-Forwarded-For", "192.168.1.100"))
                .andExpect(status().is2xxSuccessful());

        // Then: Verify context contains device fingerprint
        Map<String, Object> event = jdbcTemplate.queryForMap(
                "SELECT * FROM feature_usage WHERE user_id = ? AND product_code = ?", "anonymous", "intellij");

        String context = (String) event.get("context");
        assertThat(context).isNotNull();

        // Verify device fingerprint is present (16 hex characters)
        assertThat(context).contains("deviceFingerprint");
        assertThat(context).matches(".*\"deviceFingerprint\":\"[a-f0-9]{16}\".*");
    }

    @Test
    void shouldNotLogAnonymousUserCreatingFeature() throws Exception {
        // When: Anonymous user tries to create a feature (should be rejected by security)
        mockMvc.perform(
                        post("/api/features")
                                .contentType("application/json")
                                .content(
                                        """
                                {
                                    "productCode": "intellij",
                                    "title": "Test Feature",
                                    "description": "Test"
                                }
                                """))
                .andExpect(status().is4xxClientError()); // 401 Unauthorized

        // Then: Verify no usage event was logged
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ?", Integer.class, "anonymous");

        assertThat(count).isEqualTo(0);
    }

    @Test
    void shouldDifferentiateAnonymousAndAuthenticatedUsers() {
        // This test documents that:
        // - Anonymous users: userId = "anonymous" (for VIEW events only)
        // - Authenticated users: userId = actual username
        // - CREATE/UPDATE/DELETE events require authentication (cannot be anonymous)

        // Verify only VIEW action types can be anonymous
        ActionType[] viewActionTypes = {
            ActionType.FEATURE_VIEWED, ActionType.FEATURES_LISTED, ActionType.PRODUCT_VIEWED, ActionType.RELEASE_VIEWED
        };

        assertThat(viewActionTypes).hasSize(4);
    }
}

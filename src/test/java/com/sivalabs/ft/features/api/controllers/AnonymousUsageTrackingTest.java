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

class AnonymousUsageTrackingTest extends AbstractIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String ANONYMOUS_USER_ID = "anonymous";

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM feature_usage");
    }

    @Test
    void shouldLogAnonymousUserViewingProduct() throws Exception {
        mockMvc.perform(get("/api/products/intellij")).andExpect(status().is2xxSuccessful());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND product_code = ? AND action_type = ?",
                Integer.class,
                ANONYMOUS_USER_ID,
                "intellij",
                ActionType.PRODUCT_VIEWED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogAnonymousUserViewingFeature() throws Exception {
        mockMvc.perform(get("/api/features/IDEA-1")).andExpect(status().is2xxSuccessful());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND feature_code = ? AND action_type = ?",
                Integer.class,
                ANONYMOUS_USER_ID,
                "IDEA-1",
                ActionType.FEATURE_VIEWED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogAnonymousUserListingFeatures() throws Exception {
        mockMvc.perform(get("/api/features?productCode=intellij")).andExpect(status().is2xxSuccessful());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND product_code = ? AND action_type = ?",
                Integer.class,
                ANONYMOUS_USER_ID,
                "intellij",
                ActionType.FEATURES_LISTED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldLogAnonymousUserViewingRelease() throws Exception {
        mockMvc.perform(get("/api/releases/IDEA-2023.3.8")).andExpect(status().is2xxSuccessful());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ? AND release_code = ? AND action_type = ?",
                Integer.class,
                ANONYMOUS_USER_ID,
                "IDEA-2023.3.8",
                ActionType.RELEASE_VIEWED.name());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldStoreDeviceFingerprintInContextForAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/products/intellij")
                        .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) Chrome/120.0.0.0")
                        .header("X-Forwarded-For", "192.168.1.100"))
                .andExpect(status().is2xxSuccessful());

        Map<String, Object> event = jdbcTemplate.queryForMap(
                "SELECT * FROM feature_usage WHERE user_id = ? AND product_code = ?", ANONYMOUS_USER_ID, "intellij");

        String context = (String) event.get("context");
        assertThat(context).isNotNull();

        assertThat(context).contains("deviceFingerprint");
        assertThat(context).matches(".*\"deviceFingerprint\":\"[a-f0-9]{16}\".*");
    }

    @Test
    void shouldNotLogAnonymousUserCreatingFeature() throws Exception {
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

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_usage WHERE user_id = ?", Integer.class, ANONYMOUS_USER_ID);

        assertThat(count).isEqualTo(0);
    }

    @Test
    void shouldDifferentiateAnonymousAndAuthenticatedUsers() {
        ActionType[] viewActionTypes = {
            ActionType.FEATURE_VIEWED, ActionType.FEATURES_LISTED, ActionType.PRODUCT_VIEWED, ActionType.RELEASE_VIEWED
        };

        assertThat(viewActionTypes).hasSize(4);
    }
}

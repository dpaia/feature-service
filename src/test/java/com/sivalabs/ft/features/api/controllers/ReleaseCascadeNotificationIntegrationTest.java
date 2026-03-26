package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.MockOAuth2UserContextFactory;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import com.sivalabs.ft.features.api.models.CreateReleasePayload;
import com.sivalabs.ft.features.domain.NotificationService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Integration tests for release cascade notifications (FAIL_TO_PASS)
 * Focuses on verifying transaction rollback when notification creation fails.
 */
class ReleaseCascadeNotificationIntegrationTest extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationService notificationService;

    private final MockOAuth2UserContextFactory contextFactory = new MockOAuth2UserContextFactory();

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM notifications");
        jdbcTemplate.execute("DELETE FROM features WHERE code LIKE 'TEST-%'");
        jdbcTemplate.execute("DELETE FROM releases WHERE code LIKE 'TEST-%'");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM notifications");
        jdbcTemplate.execute("DELETE FROM features WHERE code LIKE 'TEST-%'");
        jdbcTemplate.execute("DELETE FROM releases WHERE code LIKE 'TEST-%'");
    }

    private void setAuthenticationContext(String username) {
        WithMockOAuth2User mockUser = new WithMockOAuth2User() {
            @Override
            public Class<WithMockOAuth2User> annotationType() {
                return WithMockOAuth2User.class;
            }

            @Override
            public String value() {
                return username;
            }

            @Override
            public String username() {
                return username;
            }

            @Override
            public long id() {
                return username.hashCode();
            }

            @Override
            public String[] roles() {
                return new String[] {"USER"};
            }
        };
        SecurityContextHolder.setContext(contextFactory.createSecurityContext(mockUser));
    }

    @Test
    @DisplayName("Should create cascade notifications for all feature users when release status is changed to RELEASED")
    void shouldCreateCascadeNotificationsForAllFeatureUsersWhenReleaseStatusIsChangedToReleased() throws Exception {
        setAuthenticationContext("releaseManager");
        String releaseCode = "TEST-REL-100";
        CreateReleasePayload releasePayload =
                new CreateReleasePayload("intellij", releaseCode, "Test Release for Notifications");

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();

        String fullReleaseCode = "IDEA-" + releaseCode;
        CreateFeaturePayload feature =
                new CreateFeaturePayload("intellij", "Feature 1", "First feature", fullReleaseCode, "userA");

        setAuthenticationContext("userA");
        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(feature))
                .exchange();

        jdbcTemplate.execute("DELETE FROM notifications");

        setAuthenticationContext("releaseManager");
        mvc.put()
                .uri("/api/releases/{code}", fullReleaseCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"Planned\", \"status\": \"PLANNED\", \"releasedAt\": null}")
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", fullReleaseCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"In Progress\", \"status\": \"IN_PROGRESS\", \"releasedAt\": null}")
                .exchange();

        setAuthenticationContext("userB");
        String updatePayload = String.format(
                "{\"description\": \"Released\", \"status\": \"RELEASED\", \"releasedAt\": \"%s\"}",
                Instant.now().toString());

        var result = mvc.put()
                .uri("/api/releases/{code}", fullReleaseCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();
        Integer totalNotifications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalNotifications).isEqualTo(1);
    }

    @Test
    @DisplayName("Should rollback release status change when notification serialization fails")
    void shouldRollbackStatusChangeWhenNotificationSerializationFails() throws Exception {
        setAuthenticationContext("releaseManager");
        String releaseCode = "TEST-REL-ERR";
        CreateReleasePayload releasePayload =
                new CreateReleasePayload("intellij", releaseCode, "Test Release for Error");

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();

        String fullReleaseCode = "IDEA-" + releaseCode;
        mvc.put()
                .uri("/api/releases/{code}", fullReleaseCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"Planned\", \"status\": \"PLANNED\", \"releasedAt\": null}")
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", fullReleaseCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"In Progress\", \"status\": \"IN_PROGRESS\", \"releasedAt\": null}")
                .exchange();

        CreateFeaturePayload feature =
                new CreateFeaturePayload("intellij", "Feature 1", "D1", fullReleaseCode, "userA");
        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(feature))
                .exchange();

        // Induce JsonProcessingException during notification creation
        // This verifies that the catch block in ReleaseService correctly rethrows the exception
        com.fasterxml.jackson.core.JsonProcessingException jpe =
                new com.fasterxml.jackson.core.JsonParseException(null, "Simulated Failure");
        org.mockito.Mockito.doThrow(jpe).when(objectMapper).writeValueAsString(org.mockito.ArgumentMatchers.anyMap());

        String updatePayload = String.format(
                "{\"description\": \"Released\", \"status\": \"RELEASED\", \"releasedAt\": \"%s\"}",
                Instant.now().toString());

        var result = mvc.put()
                .uri("/api/releases/{code}", fullReleaseCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        // Verify database state: status and releasedAt should remain unchanged (rolled back)
        Map<String, Object> releaseInDb =
                jdbcTemplate.queryForMap("SELECT status, released_at FROM releases WHERE code = ?", fullReleaseCode);
        assertThat(releaseInDb.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(releaseInDb.get("released_at")).isNull();
    }
}

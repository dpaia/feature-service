package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.MockOAuth2UserContextFactory;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;

@Sql("/test-data.sql")
class ReleaseNotificationTest extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final MockOAuth2UserContextFactory contextFactory = new MockOAuth2UserContextFactory();

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM notifications");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM notifications");
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

    /**
     * Inserts a release with given status using the goland product (id=2).
     * Returns the release ID.
     */
    private Long insertRelease(String code, String status) {
        jdbcTemplate.update(
                "INSERT INTO releases (product_id, code, description, status, created_by, created_at)"
                        + " VALUES (2, ?, 'Test release description', ?, 'admin', NOW())",
                code,
                status);
        return jdbcTemplate.queryForObject("SELECT id FROM releases WHERE code = ?", Long.class, code);
    }

    private void insertFeature(String featureCode, Long releaseId, String createdBy, String assignedTo) {
        jdbcTemplate.update(
                "INSERT INTO features (product_id, release_id, code, title, description, status, created_by, assigned_to, created_at)"
                        + " VALUES (2, ?, ?, 'Test Feature', 'Test', 'NEW', ?, ?, NOW())",
                releaseId,
                featureCode,
                createdBy,
                assignedTo);
    }

    // ─── Cascade trigger tests ────────────────────────────────────────────────

    @Test
    @WithMockOAuth2User(username = "release-updater")
    void shouldSendCascadeNotificationsWhenTransitioningToReleased() throws Exception {
        Long releaseId = insertRelease("GO-TEST-INPROG", "IN_PROGRESS");
        insertFeature("GO-TF-1", releaseId, "feature-creator", "feature-assignee");

        var result = mvc.put()
                .uri("/api/releases/{code}", "GO-TEST-INPROG")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Test\",\"status\":\"RELEASED\"}")
                .exchange();

        assertThat(result).hasStatusOk();

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(count).isEqualTo(2);

        assertRecipientNotified("feature-creator", "GO-TEST-INPROG");
        assertRecipientNotified("feature-assignee", "GO-TEST-INPROG");
        assertUpdaterNotNotified("release-updater");
    }

    @Test
    @WithMockOAuth2User(username = "release-updater")
    void shouldSendCascadeNotificationsWhenTransitioningToDelayed() throws Exception {
        Long releaseId = insertRelease("GO-TEST-INPROG2", "IN_PROGRESS");
        insertFeature("GO-TF-2", releaseId, "feature-creator", "feature-assignee");

        var result = mvc.put()
                .uri("/api/releases/{code}", "GO-TEST-INPROG2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Test\",\"status\":\"DELAYED\"}")
                .exchange();

        assertThat(result).hasStatusOk();

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(count).isEqualTo(2);

        assertRecipientNotified("feature-creator", "GO-TEST-INPROG2");
        assertRecipientNotified("feature-assignee", "GO-TEST-INPROG2");
        assertUpdaterNotNotified("release-updater");
    }

    @Test
    @WithMockOAuth2User(username = "release-updater")
    void shouldSendCascadeNotificationsWhenTransitioningToCancelled() throws Exception {
        Long releaseId = insertRelease("GO-TEST-INPROG3", "IN_PROGRESS");
        insertFeature("GO-TF-3", releaseId, "feature-creator", "feature-assignee");

        var result = mvc.put()
                .uri("/api/releases/{code}", "GO-TEST-INPROG3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Test\",\"status\":\"CANCELLED\"}")
                .exchange();

        assertThat(result).hasStatusOk();

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(count).isEqualTo(2);

        assertRecipientNotified("feature-creator", "GO-TEST-INPROG3");
        assertRecipientNotified("feature-assignee", "GO-TEST-INPROG3");
        assertUpdaterNotNotified("release-updater");
    }

    @Test
    @WithMockOAuth2User(username = "release-updater")
    void shouldSendCascadeNotificationsWhenTransitioningToCompleted() throws Exception {
        Long releaseId = insertRelease("GO-TEST-RELEASED", "RELEASED");
        insertFeature("GO-TF-4", releaseId, "feature-creator", "feature-assignee");

        var result = mvc.put()
                .uri("/api/releases/{code}", "GO-TEST-RELEASED")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Test\",\"status\":\"COMPLETED\"}")
                .exchange();

        assertThat(result).hasStatusOk();

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(count).isEqualTo(2);

        assertRecipientNotified("feature-creator", "GO-TEST-RELEASED");
        assertRecipientNotified("feature-assignee", "GO-TEST-RELEASED");
        assertUpdaterNotNotified("release-updater");
    }

    // ─── No cascade for non-cascade statuses ─────────────────────────────────

    @Test
    @WithMockOAuth2User(username = "release-updater")
    void shouldNotSendCascadeNotificationsWhenTransitioningToPlanned() throws Exception {
        Long releaseId = insertRelease("GO-TEST-DRAFT", "DRAFT");
        insertFeature("GO-TF-5", releaseId, "feature-creator", "feature-assignee");

        var result = mvc.put()
                .uri("/api/releases/{code}", "GO-TEST-DRAFT")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Test\",\"status\":\"PLANNED\"}")
                .exchange();

        assertThat(result).hasStatusOk();

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(username = "release-updater")
    void shouldNotSendCascadeNotificationsWhenTransitioningToInProgress() throws Exception {
        Long releaseId = insertRelease("GO-TEST-PLANNED", "PLANNED");
        insertFeature("GO-TF-6", releaseId, "feature-creator", "feature-assignee");

        var result = mvc.put()
                .uri("/api/releases/{code}", "GO-TEST-PLANNED")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Test\",\"status\":\"IN_PROGRESS\"}")
                .exchange();

        assertThat(result).hasStatusOk();

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    // ─── Deduplication ───────────────────────────────────────────────────────

    @Test
    @WithMockOAuth2User(username = "release-updater")
    void shouldSendOnlyOneNotificationToUserWithMultipleFeatures() throws Exception {
        Long releaseId = insertRelease("GO-TEST-MULTI", "IN_PROGRESS");
        insertFeature("GO-TF-7", releaseId, "multi-user", "feature-assignee");
        insertFeature("GO-TF-8", releaseId, "multi-user", "feature-assignee2");

        var result = mvc.put()
                .uri("/api/releases/{code}", "GO-TEST-MULTI")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Test\",\"status\":\"RELEASED\"}")
                .exchange();

        assertThat(result).hasStatusOk();

        Integer multiUserCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "multi-user");
        assertThat(multiUserCount)
                .as("User with multiple features should receive only one notification")
                .isEqualTo(1);
    }

    // ─── No self-notification for updater ────────────────────────────────────

    @Test
    @WithMockOAuth2User(username = "release-updater")
    void shouldNotSendCascadeNotificationToUpdater() throws Exception {
        Long releaseId = insertRelease("GO-TEST-SELF", "IN_PROGRESS");
        insertFeature("GO-TF-9", releaseId, "release-updater", "feature-assignee");

        var result = mvc.put()
                .uri("/api/releases/{code}", "GO-TEST-SELF")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Test\",\"status\":\"RELEASED\"}")
                .exchange();

        assertThat(result).hasStatusOk();

        assertUpdaterNotNotified("release-updater");

        Integer assigneeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "feature-assignee");
        assertThat(assigneeCount).isEqualTo(1);
    }

    // ─── Invalid transition ───────────────────────────────────────────────────

    @Test
    @WithMockOAuth2User(username = "release-updater")
    void shouldRejectInvalidStatusTransition() throws Exception {
        insertRelease("GO-TEST-INVALID", "DRAFT");

        var result = mvc.put()
                .uri("/api/releases/{code}", "GO-TEST-INVALID")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Test\",\"status\":\"RELEASED\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(username = "release-updater")
    void shouldRejectTransitionFromEndState() throws Exception {
        insertRelease("GO-TEST-CANCELLED", "CANCELLED");

        var result = mvc.put()
                .uri("/api/releases/{code}", "GO-TEST-CANCELLED")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Test\",\"status\":\"RELEASED\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(username = "release-updater")
    void shouldRejectTransitionFromCompletedEndState() throws Exception {
        insertRelease("GO-TEST-COMPLETED", "COMPLETED");

        var result = mvc.put()
                .uri("/api/releases/{code}", "GO-TEST-COMPLETED")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Test\",\"status\":\"CANCELLED\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    // ─── Notification content ─────────────────────────────────────────────────

    @Test
    @WithMockOAuth2User(username = "release-updater")
    void shouldIncludeCorrectEventDetailsInCascadeNotification() throws Exception {
        Long releaseId = insertRelease("GO-TEST-DETAILS", "IN_PROGRESS");
        insertFeature("GO-TF-10", releaseId, "feature-creator", null);

        var result = mvc.put()
                .uri("/api/releases/{code}", "GO-TEST-DETAILS")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Test release description\",\"status\":\"RELEASED\"}")
                .exchange();

        assertThat(result).hasStatusOk();

        Map<String, Object> notification = jdbcTemplate.queryForMap(
                "SELECT * FROM notifications WHERE recipient_user_id = ? LIMIT 1", "feature-creator");

        assertThat(notification.get("event_type")).isEqualTo("RELEASE_UPDATED");
        assertThat(notification.get("link")).isEqualTo("/releases/GO-TEST-DETAILS");

        String eventDetails = (String) notification.get("event_details");
        Map<?, ?> details = objectMapper.readValue(eventDetails, Map.class);
        assertThat(details.get("releaseCode")).isEqualTo("GO-TEST-DETAILS");
        assertThat(details.get("previousStatus")).isEqualTo("IN_PROGRESS");
        assertThat(details.get("newStatus")).isEqualTo("RELEASED");
        assertThat(details.get("description")).isEqualTo("Test release description");
    }

    // ─── No cascade when release has no features ──────────────────────────────

    @Test
    @WithMockOAuth2User(username = "release-updater")
    void shouldNotSendNotificationsWhenReleaseHasNoFeatures() throws Exception {
        insertRelease("GO-TEST-NOFEAT", "IN_PROGRESS");

        var result = mvc.put()
                .uri("/api/releases/{code}", "GO-TEST-NOFEAT")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Test\",\"status\":\"RELEASED\"}")
                .exchange();

        assertThat(result).hasStatusOk();

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void assertRecipientNotified(String userId, String releaseCode) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList("SELECT * FROM notifications WHERE recipient_user_id = ?", userId);
        assertThat(rows).as("Expected notification for user " + userId).hasSize(1);
        assertThat(rows.get(0).get("link")).isEqualTo("/releases/" + releaseCode);
        assertThat(rows.get(0).get("event_type")).isEqualTo("RELEASE_UPDATED");
        assertThat(rows.get(0).get("delivery_status")).isEqualTo("PENDING");
        assertThat(rows.get(0).get("read")).isEqualTo(false);
    }

    private void assertUpdaterNotNotified(String updaterUserId) {
        Integer updaterCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, updaterUserId);
        assertThat(updaterCount)
                .as("Updater should not receive self-notification")
                .isEqualTo(0);
    }
}

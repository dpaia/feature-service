package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import com.sivalabs.ft.features.api.models.CreateReleasePayload;
import com.sivalabs.ft.features.api.models.UpdateReleasePayload;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Integration tests for release notification system
 * Tests cascade notifications when release status changes and status transition validation
 */
@Sql("/test-data.sql")
class ReleaseNotificationIntegrationTest extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Clean up tables before each test
        jdbcTemplate.execute("DELETE FROM notifications");
        jdbcTemplate.execute("DELETE FROM features WHERE code LIKE 'TEST-%'");
        jdbcTemplate.execute("DELETE FROM releases WHERE code LIKE 'INTELLIJ-TEST-%'");
    }

    @AfterEach
    void tearDown() {
        // Clean up tables after each test
        jdbcTemplate.execute("DELETE FROM notifications");
        jdbcTemplate.execute("DELETE FROM features WHERE code LIKE 'TEST-%'");
        jdbcTemplate.execute("DELETE FROM releases WHERE code LIKE 'INTELLIJ-TEST-%'");
    }

    @Test
    void shouldCreateCascadeNotificationsWhenReleaseStatusChangesToReleased() throws Exception {
        // Given - Create a release and features with different users
        String releaseCode = createTestRelease("TEST-RELEASE-1", "Test Release for Notifications");

        // Create features with different creators and assignees (none should be releasemanager)
        createTestFeature("Feature 1", releaseCode, "creator1", "assignee1");
        createTestFeature("Feature 2", releaseCode, "creator2", "assignee2");
        createTestFeature("Feature 3", releaseCode, "creator1", "assignee3"); // Same creator, different assignee

        // Clear any notifications from feature creation
        jdbcTemplate.execute("DELETE FROM notifications");

        // Transition through valid states first (as releasemanager)
        updateReleaseStatus(releaseCode, ReleaseStatus.PLANNED);
        updateReleaseStatus(releaseCode, ReleaseStatus.IN_PROGRESS);

        // Clear notifications from non-cascade transitions
        jdbcTemplate.execute("DELETE FROM notifications");

        // When - Update release status to RELEASED (cascade transition) as releasemanager
        UpdateReleasePayload updatePayload =
                new UpdateReleasePayload("Test Release - Now Released", ReleaseStatus.RELEASED, Instant.now());

        var result = mvc.put()
                .uri("/api/releases/{code}", releaseCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload))
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "releasemanager")))
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();

        // Then - Verify cascade notifications were created for all feature users (excluding updater)

        // Debug: Check what features exist in the release
        List<Map<String, Object>> features = jdbcTemplate.queryForList(
                "SELECT code, created_by, assigned_to FROM features WHERE release_id = (SELECT id FROM releases WHERE code = ?)",
                releaseCode);
        System.out.println("Features in release " + releaseCode + ": " + features);

        List<Map<String, Object>> notifications = jdbcTemplate.queryForList(
                "SELECT recipient_user_id, event_type, event_details, link FROM notifications ORDER BY recipient_user_id");

        System.out.println("Notifications created: " + notifications);

        // Should have notifications for: creator1, creator2, assignee1, assignee2, assignee3
        // (releasemanager excluded as updater, but releasemanager is not in the feature users anyway)
        assertThat(notifications).hasSize(5);

        // Verify all notifications are RELEASE_UPDATED type
        assertThat(notifications).allMatch(n -> "RELEASE_UPDATED".equals(n.get("event_type")));

        // Verify all notifications have correct link
        assertThat(notifications).allMatch(n -> ("/releases/" + releaseCode).equals(n.get("link")));

        // Verify recipients
        List<String> recipients = notifications.stream()
                .map(n -> (String) n.get("recipient_user_id"))
                .sorted()
                .toList();
        assertThat(recipients).containsExactly("assignee1", "assignee2", "assignee3", "creator1", "creator2");

        // Verify event details contain release information
        String eventDetails = (String) notifications.get(0).get("event_details");
        Map<String, Object> details = objectMapper.readValue(eventDetails, new TypeReference<>() {});
        assertThat(details.get("releaseCode")).isEqualTo(releaseCode);
        assertThat(details.get("newStatus")).isEqualTo("RELEASED");
        assertThat(details.get("previousStatus")).isEqualTo("IN_PROGRESS");
    }

    @Test
    void shouldCreateCascadeNotificationsForDelayedStatus() throws Exception {
        // Given - Create release and transition to IN_PROGRESS first
        String releaseCode = createTestRelease("TEST-RELEASE-2", "Test Release for Delay");
        createTestFeature("Feature 1", releaseCode, "creator1", "assignee1");

        // Transition to PLANNED then IN_PROGRESS as releasemanager
        updateReleaseStatus(releaseCode, ReleaseStatus.PLANNED);
        updateReleaseStatus(releaseCode, ReleaseStatus.IN_PROGRESS);

        // Clear notifications from previous transitions
        jdbcTemplate.execute("DELETE FROM notifications");

        // When - Update to DELAYED (should trigger cascade) - using differentuser so creator1 and assignee1 both get
        // notifications
        updateReleaseStatusWithUser(releaseCode, ReleaseStatus.DELAYED, "differentuser");

        // Then - Verify cascade notifications were created
        Integer notificationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE event_type = 'RELEASE_UPDATED'", Integer.class);
        assertThat(notificationCount).isEqualTo(2); // creator1 and assignee1

        // Verify recipients
        List<String> recipients = jdbcTemplate.queryForList(
                "SELECT recipient_user_id FROM notifications WHERE event_type = 'RELEASE_UPDATED' ORDER BY recipient_user_id",
                String.class);
        assertThat(recipients).containsExactly("assignee1", "creator1");

        // Verify event details
        Map<String, Object> notification = jdbcTemplate.queryForMap("SELECT event_details FROM notifications LIMIT 1");
        String eventDetails = (String) notification.get("event_details");
        Map<String, Object> details = objectMapper.readValue(eventDetails, new TypeReference<>() {});
        assertThat(details.get("newStatus")).isEqualTo("DELAYED");
        assertThat(details.get("previousStatus")).isEqualTo("IN_PROGRESS");
    }

    @Test
    void shouldCreateCascadeNotificationsForCancelledStatus() throws Exception {
        // Given - Create release and transition to IN_PROGRESS
        String releaseCode = createTestRelease("TEST-RELEASE-3", "Test Release for Cancellation");
        createTestFeature("Feature 1", releaseCode, "creator1", "assignee1");

        updateReleaseStatus(releaseCode, ReleaseStatus.PLANNED);
        updateReleaseStatus(releaseCode, ReleaseStatus.IN_PROGRESS);

        jdbcTemplate.execute("DELETE FROM notifications");

        // When - Update to CANCELLED (should trigger cascade) - using differentuser so creator1 and assignee1 both get
        // notifications
        updateReleaseStatusWithUser(releaseCode, ReleaseStatus.CANCELLED, "differentuser");

        // Then - Verify cascade notifications were created
        Integer notificationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE event_type = 'RELEASE_UPDATED'", Integer.class);
        assertThat(notificationCount).isEqualTo(2); // creator1 and assignee1

        // Verify recipients
        List<String> recipients = jdbcTemplate.queryForList(
                "SELECT recipient_user_id FROM notifications WHERE event_type = 'RELEASE_UPDATED' ORDER BY recipient_user_id",
                String.class);
        assertThat(recipients).containsExactly("assignee1", "creator1");
    }

    @Test
    void shouldCreateCascadeNotificationsForCompletedStatus() throws Exception {
        // Given - Create release and transition to RELEASED
        String releaseCode = createTestRelease("TEST-RELEASE-4", "Test Release for Completion");
        createTestFeature("Feature 1", releaseCode, "creator1", "assignee1");

        updateReleaseStatus(releaseCode, ReleaseStatus.PLANNED);
        updateReleaseStatus(releaseCode, ReleaseStatus.IN_PROGRESS);
        updateReleaseStatus(releaseCode, ReleaseStatus.RELEASED);

        jdbcTemplate.execute("DELETE FROM notifications");

        // When - Update to COMPLETED (should trigger cascade) - using differentuser so creator1 and assignee1 both get
        // notifications
        updateReleaseStatusWithUser(releaseCode, ReleaseStatus.COMPLETED, "differentuser");

        // Then - Verify cascade notifications were created
        Integer notificationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE event_type = 'RELEASE_UPDATED'", Integer.class);
        assertThat(notificationCount).isEqualTo(2); // creator1 and assignee1

        // Verify recipients
        List<String> recipients = jdbcTemplate.queryForList(
                "SELECT recipient_user_id FROM notifications WHERE event_type = 'RELEASE_UPDATED' ORDER BY recipient_user_id",
                String.class);
        assertThat(recipients).containsExactly("assignee1", "creator1");
    }

    @Test
    void shouldNotCreateCascadeNotificationsForNonCascadeStatuses() throws Exception {
        // Given - Create release with features
        String releaseCode = createTestRelease("TEST-RELEASE-5", "Test Release for Non-Cascade");
        createTestFeature("Feature 1", releaseCode, "creator1", "assignee1");

        jdbcTemplate.execute("DELETE FROM notifications");

        // When - Update to PLANNED (should NOT trigger cascade)
        updateReleaseStatus(releaseCode, ReleaseStatus.PLANNED);

        // Then - Verify no cascade notifications were created
        Integer notificationCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(notificationCount).isEqualTo(0);

        // When - Update to IN_PROGRESS (should NOT trigger cascade)
        updateReleaseStatus(releaseCode, ReleaseStatus.IN_PROGRESS);

        // Then - Verify still no cascade notifications
        notificationCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(notificationCount).isEqualTo(0);
    }

    @Test
    void shouldNotCreateSelfNotificationForReleaseUpdater() throws Exception {
        // Given - Create release and feature where creator1 is both feature creator and release updater
        String releaseCode = createTestRelease("TEST-RELEASE-6", "Test Release for Self-Notification");
        createTestFeature("Feature 1", releaseCode, "creator1", "assignee1");

        jdbcTemplate.execute("DELETE FROM notifications");

        // When - creator1 updates release status (should exclude self from notifications)
        updateReleaseStatusWithUser(releaseCode, ReleaseStatus.PLANNED, "creator1");
        updateReleaseStatusWithUser(releaseCode, ReleaseStatus.IN_PROGRESS, "creator1");
        updateReleaseStatusWithUser(releaseCode, ReleaseStatus.RELEASED, "creator1");

        // Then - Verify creator1 did NOT receive notification, but assignee1 did
        List<Map<String, Object>> notifications = jdbcTemplate.queryForList(
                "SELECT recipient_user_id FROM notifications WHERE event_type = 'RELEASE_UPDATED'");

        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).get("recipient_user_id")).isEqualTo("assignee1");
    }

    @Test
    void shouldNotCreateDuplicateNotificationsForUsersWithMultipleFeatures() throws Exception {
        // Given - Create release with multiple features for same user
        String releaseCode = createTestRelease("TEST-RELEASE-7", "Test Release for Duplicate Prevention");
        createTestFeature("Feature 1", releaseCode, "creator1", "assignee1");
        createTestFeature("Feature 2", releaseCode, "creator1", "assignee1"); // Same users
        createTestFeature("Feature 3", releaseCode, "creator1", "assignee2"); // Same creator, different assignee

        jdbcTemplate.execute("DELETE FROM notifications");

        // When - Update release status (using differentuser so all feature users get notifications)
        updateReleaseStatusWithUser(releaseCode, ReleaseStatus.PLANNED, "differentuser");
        updateReleaseStatusWithUser(releaseCode, ReleaseStatus.IN_PROGRESS, "differentuser");
        updateReleaseStatusWithUser(releaseCode, ReleaseStatus.RELEASED, "differentuser");

        // Then - Verify each user gets only one notification despite having multiple features
        List<Map<String, Object>> notifications =
                jdbcTemplate.queryForList("SELECT recipient_user_id, COUNT(*) as count FROM notifications "
                        + "WHERE event_type = 'RELEASE_UPDATED' GROUP BY recipient_user_id ORDER BY recipient_user_id");

        // Should have notifications for: creator1, assignee1, assignee2 (3 unique users)
        assertThat(notifications).hasSize(3); // creator1, assignee1, assignee2
        assertThat(notifications).allMatch(n -> ((Number) n.get("count")).intValue() == 1);

        // Verify the specific recipients
        List<String> recipients = notifications.stream()
                .map(n -> (String) n.get("recipient_user_id"))
                .sorted()
                .toList();
        assertThat(recipients).containsExactly("assignee1", "assignee2", "creator1");
    }

    @Test
    void shouldValidateStatusTransitions() throws Exception {
        // Given - Create release in DRAFT status
        String releaseCode = createTestRelease("TEST-RELEASE-8", "Test Release for Validation");

        // Test valid transitions
        assertThat(updateReleaseStatusExpectSuccess(releaseCode, ReleaseStatus.PLANNED))
                .isTrue();
        assertThat(updateReleaseStatusExpectSuccess(releaseCode, ReleaseStatus.IN_PROGRESS))
                .isTrue();
        assertThat(updateReleaseStatusExpectSuccess(releaseCode, ReleaseStatus.RELEASED))
                .isTrue();
        assertThat(updateReleaseStatusExpectSuccess(releaseCode, ReleaseStatus.COMPLETED))
                .isTrue();

        // Test invalid transition from end state
        assertThat(updateReleaseStatusExpectSuccess(releaseCode, ReleaseStatus.DRAFT))
                .isFalse();
    }

    @Test
    void shouldValidateInvalidStatusTransitions() throws Exception {
        // Given - Create release in DRAFT status
        String releaseCode = createTestRelease("TEST-RELEASE-9", "Test Release for Invalid Transitions");

        // Test invalid transitions from DRAFT
        assertThat(updateReleaseStatusExpectSuccess(releaseCode, ReleaseStatus.RELEASED))
                .isFalse();
        assertThat(updateReleaseStatusExpectSuccess(releaseCode, ReleaseStatus.COMPLETED))
                .isFalse();
        assertThat(updateReleaseStatusExpectSuccess(releaseCode, ReleaseStatus.DELAYED))
                .isFalse();
        assertThat(updateReleaseStatusExpectSuccess(releaseCode, ReleaseStatus.CANCELLED))
                .isFalse();
    }

    // Helper methods

    private String createTestRelease(String code, String description) throws Exception {
        // Note: Authentication context should be set by caller
        CreateReleasePayload payload = new CreateReleasePayload("intellij", code, description);

        var result = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "releasemanager")))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String location = result.getMvcResult().getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf("/") + 1);
    }

    private void createTestFeature(String title, String releaseCode, String creator, String assignee) throws Exception {
        CreateFeaturePayload payload =
                new CreateFeaturePayload("intellij", title, "Test feature", releaseCode, assignee);

        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", creator)))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    private void updateReleaseStatus(String releaseCode, ReleaseStatus status) throws Exception {
        updateReleaseStatusWithUser(releaseCode, status, "releasemanager");
    }

    private void updateReleaseStatusWithUser(String releaseCode, ReleaseStatus status, String username)
            throws Exception {
        UpdateReleasePayload updatePayload = new UpdateReleasePayload("Updated description", status, null);

        var result = mvc.put()
                .uri("/api/releases/{code}", releaseCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload))
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", username)))
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();
    }

    private boolean updateReleaseStatusExpectSuccess(String releaseCode, ReleaseStatus status) throws Exception {
        UpdateReleasePayload updatePayload = new UpdateReleasePayload("Updated description", status, null);

        var result = mvc.put()
                .uri("/api/releases/{code}", releaseCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload))
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "releasemanager")))
                .exchange();

        return result.getMvcResult().getResponse().getStatus() == 200;
    }
}

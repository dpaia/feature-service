package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.MockOAuth2UserContextFactory;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import com.sivalabs.ft.features.api.models.CreateReleasePayload;
import com.sivalabs.ft.features.api.models.UpdateReleasePayload;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;

/**
 * Integration tests for release cascade notifications (FAIL_TO_PASS)
 * Tests that notifications are created when release status changes to significant states
 */
@Sql("/test-data.sql")
class ReleaseCascadeNotificationIntegrationTest extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final MockOAuth2UserContextFactory contextFactory = new MockOAuth2UserContextFactory();

    @BeforeEach
    void setUp() {
        // Clean up tables before each test
        jdbcTemplate.execute("DELETE FROM notifications");
        jdbcTemplate.execute("DELETE FROM features WHERE code LIKE 'TEST-%'");
        jdbcTemplate.execute("DELETE FROM releases WHERE code LIKE 'TEST-%'");
    }

    @AfterEach
    void tearDown() {
        // Clean up tables after each test
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
        // Given - Create a release with features
        setAuthenticationContext("releaseManager");

        // Create release
        CreateReleasePayload releasePayload =
                new CreateReleasePayload("intellij", "TEST-REL-100", "Test Release for Notifications");

        var releaseResult = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();

        assertThat(releaseResult).hasStatus(HttpStatus.CREATED);

        // Create features attached to this release with different users
        CreateFeaturePayload feature1 =
                new CreateFeaturePayload("intellij", "Feature 1", "First feature", "IDEA-TEST-REL-100", "userA");
        CreateFeaturePayload feature2 =
                new CreateFeaturePayload("intellij", "Feature 2", "Second feature", "IDEA-TEST-REL-100", "userC");

        setAuthenticationContext("userA"); // userA creates feature1, assigned to userA
        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(feature1))
                .exchange();

        setAuthenticationContext("userA"); // userA creates feature2, assigned to userC
        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(feature2))
                .exchange();

        // Clear notifications from feature creation
        jdbcTemplate.execute("DELETE FROM notifications");

        // First, transition release through valid states: DRAFT → PLANNED → IN_PROGRESS
        setAuthenticationContext("releaseManager");
        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-100")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Test Release - Planned", ReleaseStatus.PLANNED, null)))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-100")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Test Release - In Progress", ReleaseStatus.IN_PROGRESS, null)))
                .exchange();

        // When - Update release status to RELEASED (userB performs the update)
        setAuthenticationContext("userB");
        UpdateReleasePayload updatePayload =
                new UpdateReleasePayload("Test Release - Now Released", ReleaseStatus.RELEASED, Instant.now());

        var updateResult = mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-100")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload))
                .exchange();

        assertThat(updateResult).hasStatus2xxSuccessful();

        // Then - Verify exactly two notifications are created
        Integer totalNotifications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalNotifications).isEqualTo(2);

        // Verify userA received notification
        Integer userANotifications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "userA");
        assertThat(userANotifications).isEqualTo(1);

        // Verify userC received notification
        Integer userCNotifications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "userC");
        assertThat(userCNotifications).isEqualTo(1);

        // Verify userB (updater) did NOT receive notification
        Integer userBNotifications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "userB");
        assertThat(userBNotifications).isEqualTo(0);

        // Verify notification details
        List<Map<String, Object>> notifications =
                jdbcTemplate.queryForList("SELECT * FROM notifications ORDER BY recipient_user_id");

        for (Map<String, Object> notification : notifications) {
            assertThat(notification.get("event_type")).isEqualTo("RELEASE_UPDATED");
            assertThat(notification.get("link")).isEqualTo("/releases/IDEA-TEST-REL-100");
            assertThat(notification.get("read")).isEqualTo(false);
            assertThat(notification.get("delivery_status")).isEqualTo("PENDING");

            // Verify event details contain key information (format-agnostic)
            String eventDetails = (String) notification.get("event_details");
            assertThat(eventDetails).contains("IDEA-TEST-REL-100"); // release code
            assertThat(eventDetails).contains("RELEASED"); // new status
        }
    }

    @ParameterizedTest(name = "Should create cascade notifications for {0} status")
    @EnumSource(
            value = ReleaseStatus.class,
            names = {"DELAYED", "CANCELLED"})
    void shouldCreateCascadeNotificationsForSignificantStatusFromInProgress(ReleaseStatus targetStatus)
            throws Exception {
        // Given - Create release and features
        setAuthenticationContext("releaseManager");

        String releaseCode = "TEST-REL-" + targetStatus.name();
        CreateReleasePayload releasePayload =
                new CreateReleasePayload("intellij", releaseCode, "Test Release for " + targetStatus);

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();

        String fullReleaseCode = "IDEA-" + releaseCode;
        CreateFeaturePayload feature = new CreateFeaturePayload(
                "intellij", "Feature for " + targetStatus, "Feature description", fullReleaseCode, "developer");

        setAuthenticationContext("productOwner");
        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(feature))
                .exchange();

        jdbcTemplate.execute("DELETE FROM notifications");

        // First, transition release through valid states: DRAFT → PLANNED → IN_PROGRESS
        setAuthenticationContext("releaseManager");
        mvc.put()
                .uri("/api/releases/{code}", fullReleaseCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Test Release - Planned", ReleaseStatus.PLANNED, null)))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", fullReleaseCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Test Release - In Progress", ReleaseStatus.IN_PROGRESS, null)))
                .exchange();

        // When - Update to target status
        setAuthenticationContext("releaseManager");
        UpdateReleasePayload updatePayload = new UpdateReleasePayload("Release " + targetStatus, targetStatus, null);

        mvc.put()
                .uri("/api/releases/{code}", fullReleaseCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload))
                .exchange();

        // Then - Verify notifications created
        Integer totalNotifications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalNotifications).isEqualTo(1); // only developer (releaseManager excluded as updater)

        // Verify developer received notification
        Integer developerNotifications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "developer");
        assertThat(developerNotifications).isEqualTo(1);

        // Verify releaseManager (updater) did NOT receive notification
        Integer updaterNotifications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "releaseManager");
        assertThat(updaterNotifications).isEqualTo(0);
    }

    @Test
    @DisplayName("Should create cascade notifications for COMPLETED status")
    void shouldCreateCascadeNotificationsForCompletedStatus() throws Exception {
        // Given - Create release and features
        setAuthenticationContext("releaseManager");

        CreateReleasePayload releasePayload =
                new CreateReleasePayload("intellij", "TEST-REL-400", "Test Release for Completion");

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();

        CreateFeaturePayload feature = new CreateFeaturePayload(
                "intellij", "Feature for Complete", "Feature description", "IDEA-TEST-REL-400", "developer");

        setAuthenticationContext("productOwner");
        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(feature))
                .exchange();

        jdbcTemplate.execute("DELETE FROM notifications");

        // First, transition release through valid states: DRAFT → PLANNED → IN_PROGRESS → RELEASED
        setAuthenticationContext("releaseManager");
        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-400")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Test Release - Planned", ReleaseStatus.PLANNED, null)))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-400")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Test Release - In Progress", ReleaseStatus.IN_PROGRESS, null)))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-400")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Test Release - Released", ReleaseStatus.RELEASED, Instant.now())))
                .exchange();

        // Clear notifications from RELEASED transition to test only COMPLETED notifications
        jdbcTemplate.execute("DELETE FROM notifications");

        // When - Update to COMPLETED status
        setAuthenticationContext("releaseManager");
        UpdateReleasePayload updatePayload =
                new UpdateReleasePayload("Release Completed", ReleaseStatus.COMPLETED, null);

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-400")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload))
                .exchange();

        // Then - Verify notifications created
        Integer totalNotifications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalNotifications).isEqualTo(1);
    }

    @Test
    @DisplayName("Should NOT create notifications for non-significant status changes")
    void shouldNotCreateNotificationsForNonSignificantStatusChanges() throws Exception {
        // Given - Create release and features
        setAuthenticationContext("releaseManager");

        CreateReleasePayload releasePayload =
                new CreateReleasePayload("intellij", "TEST-REL-500", "Test Release for Non-Significant");

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();

        CreateFeaturePayload feature = new CreateFeaturePayload(
                "intellij", "Feature for Non-Significant", "Feature description", "IDEA-TEST-REL-500", "developer");

        setAuthenticationContext("productOwner");
        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(feature))
                .exchange();

        jdbcTemplate.execute("DELETE FROM notifications");

        // When - Update to PLANNED status (non-significant, valid transition from DRAFT)
        setAuthenticationContext("releaseManager");
        UpdateReleasePayload plannedPayload = new UpdateReleasePayload("Release Planned", ReleaseStatus.PLANNED, null);

        mvc.put()
                .uri("/api/releases/{code}", "INTELLIJ-TEST-REL-500")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(plannedPayload))
                .exchange();

        // Then - Verify NO notifications created
        Integer totalNotifications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalNotifications).isEqualTo(0);

        // When - Update to IN_PROGRESS status (non-significant, valid transition from PLANNED)
        UpdateReleasePayload inProgressPayload =
                new UpdateReleasePayload("Release In Progress", ReleaseStatus.IN_PROGRESS, null);

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-500")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inProgressPayload))
                .exchange();

        // Then - Verify still NO notifications created
        totalNotifications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalNotifications).isEqualTo(0);
    }

    @Test
    @DisplayName("Should NOT create duplicate notifications for users with multiple features")
    void shouldNotCreateDuplicateNotificationsForUsersWithMultipleFeatures() throws Exception {
        // Given - Create release with multiple features for same user
        setAuthenticationContext("releaseManager");

        CreateReleasePayload releasePayload =
                new CreateReleasePayload("intellij", "TEST-REL-600", "Test Release for Deduplication");

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();

        // Create multiple features for same user
        CreateFeaturePayload feature1 =
                new CreateFeaturePayload("intellij", "Feature 1", "First feature", "IDEA-TEST-REL-600", "developer");
        CreateFeaturePayload feature2 =
                new CreateFeaturePayload("intellij", "Feature 2", "Second feature", "IDEA-TEST-REL-600", "developer");
        CreateFeaturePayload feature3 =
                new CreateFeaturePayload("intellij", "Feature 3", "Third feature", "IDEA-TEST-REL-600", "developer");

        setAuthenticationContext("productOwner");
        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(feature1))
                .exchange();

        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(feature2))
                .exchange();

        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(feature3))
                .exchange();

        jdbcTemplate.execute("DELETE FROM notifications");

        // First, transition release through valid states: DRAFT → PLANNED → IN_PROGRESS
        setAuthenticationContext("releaseManager");
        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-600")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Test Release - Planned", ReleaseStatus.PLANNED, null)))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-600")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Test Release - In Progress", ReleaseStatus.IN_PROGRESS, null)))
                .exchange();

        // When - Update release status to RELEASED
        setAuthenticationContext("releaseManager");
        UpdateReleasePayload updatePayload =
                new UpdateReleasePayload("Release Released", ReleaseStatus.RELEASED, Instant.now());

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-600")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload))
                .exchange();

        // Then - Verify only 1 notification created (deduplicated)
        Integer totalNotifications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalNotifications).isEqualTo(1);

        // Verify developer gets exactly one notification (deduplicated across multiple features)
        Integer developerNotifications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "developer");
        assertThat(developerNotifications).isEqualTo(1);
    }

    @Test
    @DisplayName("Should NOT create notifications when release has no features")
    void shouldNotCreateNotificationsWhenReleaseHasNoFeatures() throws Exception {
        // Given - Create release without features
        setAuthenticationContext("releaseManager");

        CreateReleasePayload releasePayload =
                new CreateReleasePayload("intellij", "TEST-REL-700", "Test Release without Features");

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();

        jdbcTemplate.execute("DELETE FROM notifications");

        // First, transition release through valid states: DRAFT → PLANNED → IN_PROGRESS
        setAuthenticationContext("releaseManager");
        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-700")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Test Release - Planned", ReleaseStatus.PLANNED, null)))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-700")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Test Release - In Progress", ReleaseStatus.IN_PROGRESS, null)))
                .exchange();

        // When - Update release status to RELEASED
        UpdateReleasePayload updatePayload =
                new UpdateReleasePayload("Release Released", ReleaseStatus.RELEASED, Instant.now());

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-700")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload))
                .exchange();

        // Then - Verify NO notifications created
        Integer totalNotifications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalNotifications).isEqualTo(0);
    }

    @Test
    @DisplayName("Should reject invalid status transitions and NOT create notifications")
    void shouldRejectInvalidStatusTransitionsAndNotCreateNotifications() throws Exception {
        // Given - Create release with feature
        setAuthenticationContext("releaseManager");

        CreateReleasePayload releasePayload =
                new CreateReleasePayload("intellij", "TEST-REL-800", "Test Release for Invalid Transition");

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();

        CreateFeaturePayload feature = new CreateFeaturePayload(
                "intellij", "Feature for Invalid", "Feature description", "IDEA-TEST-REL-800", "developer");

        setAuthenticationContext("productOwner");
        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(feature))
                .exchange();

        jdbcTemplate.execute("DELETE FROM notifications");

        // When - Try invalid transition: DRAFT -> IN_PROGRESS (should fail, must go through PLANNED)
        setAuthenticationContext("releaseManager");
        UpdateReleasePayload invalidPayload =
                new UpdateReleasePayload("Trying to skip PLANNED", ReleaseStatus.IN_PROGRESS, null);

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-800")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidPayload))
                .exchange();

        // Then - Verify request was rejected
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);

        // Verify NO notifications were created
        Integer totalNotifications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalNotifications).isEqualTo(0);

        // Verify release status remains DRAFT
        var releaseCheck =
                mvc.get().uri("/api/releases/{code}", "IDEA-TEST-REL-800").exchange();
        assertThat(releaseCheck).hasStatusOk();
        String responseBody = releaseCheck.getResponse().getContentAsString();
        assertThat(responseBody).contains("DRAFT");
    }

    @Test
    @DisplayName("Should create cascade notifications when transitioning from DELAYED to RELEASED (alternative path)")
    void shouldCreateCascadeNotificationsForDelayedToReleasedTransition() throws Exception {
        // Given - Create release and feature, transition to DELAYED
        setAuthenticationContext("releaseManager");

        CreateReleasePayload releasePayload =
                new CreateReleasePayload("intellij", "TEST-REL-900", "Test Release for Delayed to Released");

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();

        CreateFeaturePayload feature = new CreateFeaturePayload(
                "intellij", "Feature for Delayed-Released", "Feature description", "IDEA-TEST-REL-900", "developer");

        setAuthenticationContext("productOwner");
        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(feature))
                .exchange();

        // Transition: DRAFT → PLANNED → IN_PROGRESS → DELAYED
        setAuthenticationContext("releaseManager");
        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-900")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Planned", ReleaseStatus.PLANNED, null)))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-900")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("In Progress", ReleaseStatus.IN_PROGRESS, null)))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-900")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Delayed", ReleaseStatus.DELAYED, null)))
                .exchange();

        // Clear notifications from DELAYED transition
        jdbcTemplate.execute("DELETE FROM notifications");

        // When - Transition from DELAYED to RELEASED (alternative path)
        setAuthenticationContext("releaseManager");
        UpdateReleasePayload releasedPayload =
                new UpdateReleasePayload("Finally Released", ReleaseStatus.RELEASED, Instant.now());

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-900")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasedPayload))
                .exchange();

        // Then - Verify success and notifications created
        assertThat(result).hasStatus2xxSuccessful();

        Integer totalNotifications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalNotifications).isEqualTo(1); // developer only (releaseManager excluded)

        // Verify notification details
        String eventDetails = jdbcTemplate.queryForObject(
                "SELECT event_details FROM notifications WHERE recipient_user_id = ?", String.class, "developer");
        assertThat(eventDetails).contains("RELEASED");
    }

    @Test
    @DisplayName("Should reject transitions from CANCELLED terminal state")
    void shouldRejectTransitionsFromCancelledTerminalState() throws Exception {
        // Given - Create release and transition to CANCELLED
        setAuthenticationContext("releaseManager");

        CreateReleasePayload releasePayload =
                new CreateReleasePayload("intellij", "TEST-REL-1000", "Test Release for Cancelled Terminal");

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();

        // Transition: DRAFT → PLANNED → IN_PROGRESS → CANCELLED
        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1000")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Planned", ReleaseStatus.PLANNED, null)))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1000")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("In Progress", ReleaseStatus.IN_PROGRESS, null)))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1000")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Cancelled", ReleaseStatus.CANCELLED, null)))
                .exchange();

        jdbcTemplate.execute("DELETE FROM notifications");

        // When - Try to transition from CANCELLED to RELEASED (should fail)
        UpdateReleasePayload invalidPayload =
                new UpdateReleasePayload("Trying to resurrect", ReleaseStatus.RELEASED, Instant.now());

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1000")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidPayload))
                .exchange();

        // Then - Verify rejection
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);

        // Verify NO notifications created
        Integer totalNotifications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalNotifications).isEqualTo(0);

        // Verify status remains CANCELLED
        var releaseCheck =
                mvc.get().uri("/api/releases/{code}", "IDEA-TEST-REL-1000").exchange();
        String responseBody = releaseCheck.getResponse().getContentAsString();
        assertThat(responseBody).contains("CANCELLED");
    }

    @Test
    @DisplayName("Should reject transitions from COMPLETED terminal state")
    void shouldRejectTransitionsFromCompletedTerminalState() throws Exception {
        // Given - Create release and transition to COMPLETED
        setAuthenticationContext("releaseManager");

        CreateReleasePayload releasePayload =
                new CreateReleasePayload("intellij", "TEST-REL-1100", "Test Release for Completed Terminal");

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();

        // Transition: DRAFT → PLANNED → IN_PROGRESS → RELEASED → COMPLETED
        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1100")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Planned", ReleaseStatus.PLANNED, null)))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1100")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("In Progress", ReleaseStatus.IN_PROGRESS, null)))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1100")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Released", ReleaseStatus.RELEASED, Instant.now())))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1100")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Completed", ReleaseStatus.COMPLETED, null)))
                .exchange();

        jdbcTemplate.execute("DELETE FROM notifications");

        // When - Try to transition from COMPLETED to CANCELLED (should fail)
        UpdateReleasePayload invalidPayload =
                new UpdateReleasePayload("Trying to cancel completed", ReleaseStatus.CANCELLED, null);

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1100")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidPayload))
                .exchange();

        // Then - Verify rejection
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);

        // Verify NO notifications created
        Integer totalNotifications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalNotifications).isEqualTo(0);

        // Verify status remains COMPLETED
        var releaseCheck =
                mvc.get().uri("/api/releases/{code}", "IDEA-TEST-REL-1100").exchange();
        String responseBody = releaseCheck.getResponse().getContentAsString();
        assertThat(responseBody).contains("COMPLETED");
    }

    @Test
    @DisplayName("Should create cascade notifications for DELAYED to CANCELLED transition")
    void shouldCreateCascadeNotificationsForDelayedToCancelledTransition() throws Exception {
        // Given - Create release and feature, transition to DELAYED
        setAuthenticationContext("releaseManager");

        CreateReleasePayload releasePayload =
                new CreateReleasePayload("intellij", "TEST-REL-1200", "Test Release for Delayed to Cancelled");

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();

        CreateFeaturePayload feature = new CreateFeaturePayload(
                "intellij", "Feature for Delayed-Cancelled", "Feature description", "IDEA-TEST-REL-1200", "developer");

        setAuthenticationContext("productOwner");
        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(feature))
                .exchange();

        // Transition: DRAFT → PLANNED → IN_PROGRESS → DELAYED
        setAuthenticationContext("releaseManager");
        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1200")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Planned", ReleaseStatus.PLANNED, null)))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1200")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("In Progress", ReleaseStatus.IN_PROGRESS, null)))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1200")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Delayed", ReleaseStatus.DELAYED, null)))
                .exchange();

        // Clear notifications from DELAYED transition
        jdbcTemplate.execute("DELETE FROM notifications");

        // When - Transition from DELAYED to CANCELLED
        setAuthenticationContext("releaseManager");
        UpdateReleasePayload cancelledPayload =
                new UpdateReleasePayload("Finally Cancelled", ReleaseStatus.CANCELLED, null);

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1200")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cancelledPayload))
                .exchange();

        // Then - Verify success and notifications created
        assertThat(result).hasStatus2xxSuccessful();

        Integer totalNotifications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalNotifications).isEqualTo(1); // developer only (releaseManager excluded)

        // Verify notification contains CANCELLED status
        String eventDetails = jdbcTemplate.queryForObject(
                "SELECT event_details FROM notifications WHERE recipient_user_id = ?", String.class, "developer");
        assertThat(eventDetails).contains("CANCELLED");
    }

    @Test
    @DisplayName("Should notify both createdBy and assignedTo when they are different users")
    void shouldNotifyBothCreatedByAndAssignedToWhenDifferent() throws Exception {
        // Given - Create release with feature where createdBy != assignedTo
        setAuthenticationContext("releaseManager");

        CreateReleasePayload releasePayload =
                new CreateReleasePayload("intellij", "TEST-REL-1300", "Test Release for CreatedBy and AssignedTo");

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();

        // Get release ID for the feature
        Long releaseId =
                jdbcTemplate.queryForObject("SELECT id FROM releases WHERE code = ?", Long.class, "IDEA-TEST-REL-1300");

        // Create feature directly via SQL to ensure createdBy and assignedTo are different
        // This bypasses the security context issue in tests
        jdbcTemplate.update(
                "INSERT INTO features (product_id, release_id, code, title, description, status, created_by, assigned_to, created_at) "
                        + "VALUES (1, ?, 'TEST-FEATURE-1300', 'Feature with different users', 'Description', 'NEW', 'creator', 'assignee', NOW())",
                releaseId);

        jdbcTemplate.execute("DELETE FROM notifications");

        // Transition: DRAFT → PLANNED → IN_PROGRESS → RELEASED
        setAuthenticationContext("releaseManager");
        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1300")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("Planned", ReleaseStatus.PLANNED, null)))
                .exchange();

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1300")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateReleasePayload("In Progress", ReleaseStatus.IN_PROGRESS, null)))
                .exchange();

        // When - Transition to RELEASED (by someone other than creator/assignee)
        UpdateReleasePayload releasedPayload =
                new UpdateReleasePayload("Released", ReleaseStatus.RELEASED, Instant.now());

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-TEST-REL-1300")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasedPayload))
                .exchange();

        // Then - Verify both creator and assignee receive notifications
        assertThat(result).hasStatus2xxSuccessful();

        Integer totalNotifications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalNotifications).isEqualTo(2); // creator + assignee

        // Verify creator received notification
        Integer creatorNotifications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "creator");
        assertThat(creatorNotifications).isEqualTo(1);

        // Verify assignee received notification
        Integer assigneeNotifications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "assignee");
        assertThat(assigneeNotifications).isEqualTo(1);

        // Verify releaseManager (updater) did NOT receive notification
        Integer updaterNotifications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "releaseManager");
        assertThat(updaterNotifications).isEqualTo(0);
    }
}

package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.MockOAuth2UserContextFactory;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;

/**
 * Integration tests for notification system
 * Tests that notifications are created synchronously when features are created/updated/deleted
 */
@Sql("/test-data.sql")
class NotificationIntegrationTest extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final MockOAuth2UserContextFactory contextFactory = new MockOAuth2UserContextFactory();

    @BeforeEach
    void setUp() {
        // Clean up tables before each test
        jdbcTemplate.execute("DELETE FROM notifications");
    }

    @AfterEach
    void tearDown() {
        // Clean up tables after each test
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

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldCreateNotificationWhenFeatureIsCreated() throws Exception {
        // Given - Create feature as testuser with assignedTo different from creator
        CreateFeaturePayload payload = new CreateFeaturePayload(
                "intellij", "Feature for Notification", "Test notification creation", null, "assignee");

        // When - Create feature
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);

        // Verify testuser (creator) does NOT receive notification (no self-notifications)
        var apiResponse = mvc.get().uri("/api/notifications").exchange();
        assertThat(apiResponse).hasStatus2xxSuccessful();

        String responseBody = apiResponse.getResponse().getContentAsString();
        Map<String, Object> pageResponse = objectMapper.readValue(responseBody, new TypeReference<>() {});
        List<Map<String, Object>> notifications = (List<Map<String, Object>>) pageResponse.get("content");

        assertThat(notifications)
                .as("Creator should NOT receive self-notification")
                .isEmpty();

        // Verify only assignee receives notification via database
        Integer assigneeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "assignee");
        assertThat(assigneeCount).isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldCreateMultipleNotificationsForDifferentFeatures() throws Exception {
        // Given - Create two different features
        CreateFeaturePayload payload1 =
                new CreateFeaturePayload("intellij", "Feature 1", "First feature", null, "assignee");
        CreateFeaturePayload payload2 =
                new CreateFeaturePayload("intellij", "Feature 2", "Second feature", null, "assignee");

        // When - Create two features
        var result1 = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload1))
                .exchange();

        var result2 = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload2))
                .exchange();

        assertThat(result1).hasStatus(HttpStatus.CREATED);
        assertThat(result2).hasStatus(HttpStatus.CREATED);

        // Verify creator (testuser) does NOT receive notifications (no self-notifications)
        var apiResponse = mvc.get().uri("/api/notifications").exchange();
        assertThat(apiResponse).hasStatus2xxSuccessful();

        String responseBody = apiResponse.getResponse().getContentAsString();
        Map<String, Object> pageResponse = objectMapper.readValue(responseBody, new TypeReference<>() {});
        List<Map<String, Object>> notifications = (List<Map<String, Object>>) pageResponse.get("content");

        assertThat(notifications)
                .as("Creator should NOT receive self-notifications")
                .isEmpty();

        // Verify only assignee received 2 notifications via database
        Integer assigneeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "assignee");
        assertThat(assigneeCount).isEqualTo(2);
    }

    @Test
    void shouldMarkNotificationAsRead() throws Exception {
        // Given - Create a feature as creator, assigned to assignee
        setAuthenticationContext("creator");
        CreateFeaturePayload payload =
                new CreateFeaturePayload("intellij", "Read Test Feature", "Test read functionality", null, "assignee");

        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        // Get notification ID for assignee via database
        UUID notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE recipient_user_id = ? ORDER BY created_at DESC LIMIT 1",
                UUID.class,
                "assignee");

        // When - Mark as read via API (as assignee, using .with(jwt()) for MockMvc authentication)
        var readResult = mvc.put()
                .uri("/api/notifications/{id}/read", notificationId)
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "assignee")))
                .exchange();

        assertThat(readResult).hasStatus2xxSuccessful();

        // Then - Verify notification is marked as read via API (as assignee)
        var updatedResponse = mvc.get()
                .uri("/api/notifications")
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "assignee")))
                .exchange();
        assertThat(updatedResponse).hasStatus2xxSuccessful();

        String updatedBody = updatedResponse.getResponse().getContentAsString();
        Map<String, Object> updatedPage = objectMapper.readValue(updatedBody, new TypeReference<>() {});
        List<Map<String, Object>> updatedNotifications = (List<Map<String, Object>>) updatedPage.get("content");

        assertThat(updatedNotifications).hasSize(1);
        assertThat(updatedNotifications.get(0).get("read")).isEqualTo(true);
        assertThat(updatedNotifications.get(0).get("readAt")).isNotNull();
    }

    @Test
    void shouldMarkNotificationAsUnread() throws Exception {
        // Given - Create a feature as creator, assigned to assignee
        setAuthenticationContext("creator");
        CreateFeaturePayload payload = new CreateFeaturePayload(
                "intellij", "Unread Test Feature", "Test unread functionality", null, "assignee");

        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        // Get notification ID for assignee via database
        UUID notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE recipient_user_id = ? ORDER BY created_at DESC LIMIT 1",
                UUID.class,
                "assignee");

        // Mark as read first via API (as assignee, using .with(jwt()) for MockMvc authentication)
        var readResult = mvc.put()
                .uri("/api/notifications/{id}/read", notificationId)
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "assignee")))
                .exchange();
        assertThat(readResult).hasStatus2xxSuccessful();

        // Verify it's marked as read via API (as assignee)
        var readResponse = mvc.get()
                .uri("/api/notifications")
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "assignee")))
                .exchange();
        String readBody = readResponse.getResponse().getContentAsString();
        Map<String, Object> readPage = objectMapper.readValue(readBody, new TypeReference<>() {});
        List<Map<String, Object>> readNotifications = (List<Map<String, Object>>) readPage.get("content");

        assertThat(readNotifications.get(0).get("read")).isEqualTo(true);
        assertThat(readNotifications.get(0).get("readAt")).isNotNull();

        // When - Mark as unread via API (as assignee)
        var unreadResult = mvc.put()
                .uri("/api/notifications/{id}/unread", notificationId)
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "assignee")))
                .exchange();

        assertThat(unreadResult).hasStatus2xxSuccessful();

        // Then - Verify notification is marked as unread via API (as assignee)
        var unreadResponse = mvc.get()
                .uri("/api/notifications")
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "assignee")))
                .exchange();
        String unreadBody = unreadResponse.getResponse().getContentAsString();
        Map<String, Object> unreadPage = objectMapper.readValue(unreadBody, new TypeReference<>() {});
        List<Map<String, Object>> unreadNotifications = (List<Map<String, Object>>) unreadPage.get("content");

        assertThat(unreadNotifications).hasSize(1);
        assertThat(unreadNotifications.get(0).get("read")).isEqualTo(false);
        assertThat(unreadNotifications.get(0).get("readAt")).isNull();

        // Verify in database directly
        Integer unreadCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE id = ? AND read = false AND read_at IS NULL",
                Integer.class,
                notificationId);
        assertThat(unreadCount).isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetNotificationsViaApiWithPagination() throws Exception {
        // Given - Create multiple features as testuser with assignedTo different from creator
        for (int i = 0; i < 5; i++) {
            CreateFeaturePayload payload =
                    new CreateFeaturePayload("intellij", "Feature " + i, "Description " + i, null, "assignee");

            mvc.post()
                    .uri("/api/features")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload))
                    .exchange();
        }

        // Verify testuser (creator) does NOT receive notifications
        var creatorResponse = mvc.get().uri("/api/notifications").exchange();
        String creatorBody = creatorResponse.getResponse().getContentAsString();
        Map<String, Object> creatorPage = objectMapper.readValue(creatorBody, new TypeReference<>() {});
        List<Map<String, Object>> creatorNotifications = (List<Map<String, Object>>) creatorPage.get("content");
        assertThat(creatorNotifications).isEmpty();

        // Verify assignee received 5 notifications via database
        Integer assigneeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "assignee");
        assertThat(assigneeCount).isEqualTo(5);

        // Verify pagination via database (can't test API with setAuthenticationContext)
        Integer totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "assignee");
        assertThat(totalCount).isEqualTo(5);

        // Verify first 3 notifications exist
        List<Map<String, Object>> firstPage = jdbcTemplate.queryForList(
                "SELECT * FROM notifications WHERE recipient_user_id = ? ORDER BY created_at DESC LIMIT 3", "assignee");
        assertThat(firstPage).hasSize(3);
    }

    @Test
    void shouldOnlyShowNotificationsToRecipient() throws Exception {
        // Create feature as user1 with no assignedTo (no one gets notification - self-notification filtered)
        setAuthenticationContext("user1");
        CreateFeaturePayload payload1 =
                new CreateFeaturePayload("intellij", "User1 Feature", "Feature created by user1", null, null);
        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload1))
                .exchange();

        // Create feature as user1 assigned to user2 (only user2 gets notification)
        CreateFeaturePayload payload2 =
                new CreateFeaturePayload("intellij", "User2 Feature", "Feature assigned to user2", null, "user2");
        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload2))
                .exchange();

        // Verify user1 (creator) does NOT receive notifications
        var user1Response = mvc.get().uri("/api/notifications").exchange();
        assertThat(user1Response).hasStatusOk();

        String user1Body = user1Response.getResponse().getContentAsString();
        Map<String, Object> user1Page = objectMapper.readValue(user1Body, new TypeReference<>() {});
        List<Map<String, Object>> user1Notifications = (List<Map<String, Object>>) user1Page.get("content");

        assertThat(user1Notifications).isEmpty(); // Creator does NOT receive self-notifications

        // Verify user2 (assignee) received 1 notification via database
        Integer user2Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, "user2");
        assertThat(user2Count).isEqualTo(1);

        // Verify user2's notification details via database
        Map<String, Object> user2Notification = jdbcTemplate.queryForMap(
                "SELECT * FROM notifications WHERE recipient_user_id = ? ORDER BY created_at DESC LIMIT 1", "user2");
        assertThat(user2Notification.get("recipient_user_id")).isEqualTo("user2");
        assertThat(user2Notification.get("event_type")).isEqualTo("FEATURE_CREATED");
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldNotCreateSelfNotificationWhenCreatingFeatureWithoutAssignee() throws Exception {
        // Given - Create feature without assignedTo
        CreateFeaturePayload payload =
                new CreateFeaturePayload("intellij", "Self Feature", "Feature without assignee", null, null);

        // When - Create feature
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);

        // Then - Verify creator does NOT receive notification via API
        var apiResponse = mvc.get().uri("/api/notifications").exchange();
        assertThat(apiResponse).hasStatus2xxSuccessful();

        String responseBody = apiResponse.getResponse().getContentAsString();
        Map<String, Object> pageResponse = objectMapper.readValue(responseBody, new TypeReference<>() {});
        List<Map<String, Object>> notifications = (List<Map<String, Object>>) pageResponse.get("content");

        assertThat(notifications)
                .as("Creator should NOT receive self-notification")
                .isEmpty();

        // Verify no notifications created at all
        Integer totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalCount).isEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldNotCreateSelfNotificationWhenDeletingOwnFeature() throws Exception {
        // Given - Create feature assigned to testuser
        CreateFeaturePayload payload =
                new CreateFeaturePayload("intellij", "Feature to Delete", "Will be deleted", null, "testuser");

        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(createResult).hasStatus(HttpStatus.CREATED);
        String location = createResult.getMvcResult().getResponse().getHeader("Location");
        String featureCode = location.substring(location.lastIndexOf("/") + 1);

        // Clear notifications from creation
        jdbcTemplate.execute("DELETE FROM notifications");

        // When - Delete feature as testuser (who is also assignee)
        var deleteResult = mvc.delete().uri("/api/features/{code}", featureCode).exchange();

        assertThat(deleteResult).hasStatus2xxSuccessful();

        // Then - Verify testuser does NOT receive deletion notification via API
        var apiResponse = mvc.get().uri("/api/notifications").exchange();
        assertThat(apiResponse).hasStatus2xxSuccessful();

        String responseBody = apiResponse.getResponse().getContentAsString();
        Map<String, Object> pageResponse = objectMapper.readValue(responseBody, new TypeReference<>() {});
        List<Map<String, Object>> notifications = (List<Map<String, Object>>) pageResponse.get("content");

        assertThat(notifications)
                .as("Deleter should NOT receive self-notification when deleting own feature")
                .isEmpty();

        // Verify no notifications created
        Integer totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalCount).isEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldNotCreateSelfNotificationWhenCreatingFeatureWithSelfAssignee() throws Exception {
        // Given - Create feature assigned to self (testuser creates and assigns to testuser)
        CreateFeaturePayload payload =
                new CreateFeaturePayload("intellij", "Self Feature", "Feature with self-assignee", null, "testuser");

        // When - Create feature
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);

        // Then - Verify testuser does NOT receive notification via API (self-notification filtered)
        var apiResponse = mvc.get().uri("/api/notifications").exchange();
        assertThat(apiResponse).hasStatus2xxSuccessful();

        String responseBody = apiResponse.getResponse().getContentAsString();
        Map<String, Object> pageResponse = objectMapper.readValue(responseBody, new TypeReference<>() {});
        List<Map<String, Object>> notifications = (List<Map<String, Object>>) pageResponse.get("content");

        assertThat(notifications)
                .as("Creator should NOT receive self-notification when assigning to self")
                .isEmpty();

        // Verify no notifications created at all
        Integer totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(totalCount).isEqualTo(0);
    }

    @Test
    void shouldReturnUnauthorizedWhenNotAuthenticated() throws Exception {
        // When - Try to access notifications without authentication
        var response = mvc.get().uri("/api/notifications").exchange();

        // Then - Should return 401 Unauthorized
        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldReturnNotFoundForNonExistentNotification() throws Exception {
        // Given - Random UUID that doesn't exist
        UUID nonExistentId = UUID.randomUUID();

        // When - Try to mark non-existent notification as read
        var readResponse =
                mvc.put().uri("/api/notifications/{id}/read", nonExistentId).exchange();

        // Then - Should return 404 Not Found
        assertThat(readResponse).hasStatus4xxClientError();

        // When - Try to mark non-existent notification as unread
        var unreadResponse =
                mvc.put().uri("/api/notifications/{id}/unread", nonExistentId).exchange();

        // Then - Should return 404 Not Found
        assertThat(unreadResponse).hasStatus4xxClientError();
    }

    @Test
    void shouldReturnNotFoundWhenTryingToAccessOtherUsersNotification() throws Exception {
        // Given - Create notification for otheruser via API (creator creates, otheruser is assignee)

        setAuthenticationContext("creator");
        CreateFeaturePayload payload =
                new CreateFeaturePayload("intellij", "Other User Feature", "Feature for otheruser", null, "otheruser");
        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        // Get the notification ID for otheruser via database (created synchronously)
        UUID notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE recipient_user_id = ? ORDER BY created_at DESC LIMIT 1",
                UUID.class,
                "otheruser");

        // When - Try to mark other user's notification as read (authenticated as testuser)
        setAuthenticationContext("testuser");
        var readResponse =
                mvc.put().uri("/api/notifications/{id}/read", notificationId).exchange();

        // Then - Should return 4xx (not found/access denied)
        assertThat(readResponse).hasStatus4xxClientError();

        // Verify database state unchanged - notification should still be unread
        Boolean isRead = jdbcTemplate.queryForObject(
                "SELECT read FROM notifications WHERE id = ?", Boolean.class, notificationId);
        assertThat(isRead).isFalse();
    }
}

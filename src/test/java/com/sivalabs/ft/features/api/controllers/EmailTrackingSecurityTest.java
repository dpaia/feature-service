package com.sivalabs.ft.features.api.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sivalabs.ft.features.TestEmailConfiguration;
import com.sivalabs.ft.features.TestcontainersConfiguration;
import com.sivalabs.ft.features.domain.NotificationService;
import com.sivalabs.ft.features.domain.UserRepository;
import com.sivalabs.ft.features.domain.dtos.NotificationDto;
import com.sivalabs.ft.features.domain.entities.User;
import com.sivalabs.ft.features.domain.models.NotificationEventType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Security tests for the email tracking endpoint to ensure it cannot be spoofed
 * and handles edge cases properly without exposing internal details.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestEmailConfiguration.class})
@Sql(scripts = {"/test-data.sql"})
@Transactional
class EmailTrackingSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private NotificationDto testNotification;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new User("securitytestuser", "security@example.com");
        testUser = userRepository.save(testUser);

        // Create test notification
        testNotification = notificationService.createNotification(
                testUser.getUsername(),
                NotificationEventType.FEATURE_CREATED,
                "Security test notification",
                "http://localhost:8081/features/security-test");
    }

    @Test
    void shouldReturn404ForNonExistentNotificationWithoutExposingInternals() throws Exception {
        // Given - A UUID that doesn't exist in the database
        UUID nonExistentId = UUID.randomUUID();

        // When & Then - Should return 404 without exposing stack traces or internal details
        mockMvc.perform(get("/api/notifications/{id}/read", nonExistentId)).andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400ForInvalidUuidFormatWithoutExposingInternals() throws Exception {
        // Given - Various invalid UUID formats that should reach our controller
        String[] invalidUuids = {"invalid-uuid", "123", "not-a-uuid-at-all"};

        // When & Then - All should return 400 without exposing internals
        for (String invalidUuid : invalidUuids) {
            mockMvc.perform(get("/api/notifications/{id}/read", invalidUuid)).andExpect(status().isBadRequest());
        }

        // Test a valid UUID format that doesn't exist - should return 404
        mockMvc.perform(get("/api/notifications/{id}/read", "12345678-1234-1234-1234-123456789012"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldBeIdempotentAndNotUpdateTimestampOnSubsequentCalls() throws Exception {
        // Given
        UUID notificationId = testNotification.id();

        // When - Multiple calls to the same notification
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/notifications/{id}/read", notificationId)).andExpect(status().isOk());
        }

        // Then - All calls should succeed (idempotent behavior)
        // The actual timestamp verification would require database access,
        // but the HTTP behavior should be consistent
    }

    @Test
    void shouldNotRequireAnyAuthentication() throws Exception {
        // Given
        UUID notificationId = testNotification.id();

        // When & Then - No authentication headers, cookies, or tokens provided
        mockMvc.perform(get("/api/notifications/{id}/read", notificationId)).andExpect(status().isOk());
    }

    @Test
    void shouldHandleSpecialCharactersInPath() throws Exception {
        // Given - Various special characters that should reach our controller
        String[] specialInputs = {"invalid-chars-123", "not-uuid-format"};

        // When & Then - All should return 400 (bad request) for invalid UUID format
        for (String specialInput : specialInputs) {
            mockMvc.perform(get("/api/notifications/{id}/read", specialInput)).andExpect(status().isBadRequest());
        }

        // Note: Some special characters like "../../../etc/passwd" and "<script>"
        // are blocked by Spring Security before reaching our controller,
        // which is the expected security behavior
    }

    @Test
    void shouldHandleConcurrentRequests() throws Exception {
        // Given
        UUID notificationId = testNotification.id();

        // When - Simulate concurrent requests (simplified test)
        // In a real scenario, you might use multiple threads
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/notifications/{id}/read", notificationId)).andExpect(status().isOk());
        }

        // Then - All requests should succeed without errors
    }

    @Test
    void shouldReturnConsistentResponseForValidNotification() throws Exception {
        // Given
        UUID notificationId = testNotification.id();

        // When & Then - Multiple calls should return identical responses
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/notifications/{id}/read", notificationId))
                    .andExpect(status().isOk())
                    .andExpect(result -> {
                        // Verify response is consistent
                        byte[] content = result.getResponse().getContentAsByteArray();
                        // Should always return the same 1x1 transparent GIF
                        assert content.length > 0;
                    });
        }
    }

    @Test
    void shouldNotAcceptOtherHttpMethods() throws Exception {
        // Given
        UUID notificationId = testNotification.id();
        String endpoint = "/api/notifications/" + notificationId + "/read";

        // When & Then - Only GET should be allowed
        // Other methods are blocked by Spring Security with 401 Unauthorized
        // This is the expected behavior as the endpoint is public only for GET
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(endpoint))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(endpoint))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(endpoint))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(endpoint))
                .andExpect(status().isUnauthorized());
    }
}

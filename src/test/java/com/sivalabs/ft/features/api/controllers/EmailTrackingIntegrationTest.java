package com.sivalabs.ft.features.api.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestEmailConfiguration.class})
@Sql(scripts = {"/test-data.sql"})
@Transactional
class EmailTrackingIntegrationTest {

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
        testUser = new User("testuser", "test@example.com");
        testUser = userRepository.save(testUser);

        // Create test notification using the service
        testNotification = notificationService.createNotification(
                testUser.getUsername(),
                NotificationEventType.FEATURE_CREATED,
                "Test feature created",
                "http://localhost:8081/features/123");
    }

    @Test
    void shouldReturnTransparentGifWhenTrackingValidNotification() throws Exception {
        // Given
        UUID notificationId = testNotification.id();

        // When & Then
        mockMvc.perform(get("/api/notifications/{id}/read", notificationId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("image/gif")))
                .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"));
    }

    @Test
    void shouldReturn404WhenTrackingNonExistentNotification() throws Exception {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        mockMvc.perform(get("/api/notifications/{id}/read", nonExistentId)).andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400WhenTrackingWithInvalidUuid() throws Exception {
        // Given
        String invalidUuid = "invalid-uuid";

        // When & Then
        mockMvc.perform(get("/api/notifications/{id}/read", invalidUuid)).andExpect(status().isBadRequest());
    }

    @Test
    void shouldBeIdempotentWhenCalledMultipleTimes() throws Exception {
        // Given
        UUID notificationId = testNotification.id();

        // When - First call
        mockMvc.perform(get("/api/notifications/{id}/read", notificationId)).andExpect(status().isOk());

        // When - Second call (should still return 200 OK)
        mockMvc.perform(get("/api/notifications/{id}/read", notificationId)).andExpect(status().isOk());
    }

    @Test
    void shouldReturnCorrectGifContent() throws Exception {
        // Given
        UUID notificationId = testNotification.id();

        // Expected 1x1 transparent GIF bytes
        byte[] expectedGif = new byte[] {
            0x47,
            0x49,
            0x46,
            0x38,
            0x39,
            0x61,
            0x01,
            0x00,
            0x01,
            0x00,
            (byte) 0x80,
            0x00,
            0x00,
            (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0xFF,
            0x00,
            0x00,
            0x00,
            0x21,
            (byte) 0xF9,
            0x04,
            0x01,
            0x00,
            0x00,
            0x00,
            0x00,
            0x2C,
            0x00,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x01,
            0x00,
            0x00,
            0x02,
            0x02,
            0x04,
            0x01,
            0x00,
            0x3B
        };

        // When & Then
        mockMvc.perform(get("/api/notifications/{id}/read", notificationId))
                .andExpect(status().isOk())
                .andExpect(content().bytes(expectedGif));
    }

    @Test
    void shouldNotRequireAuthentication() throws Exception {
        // Given
        UUID notificationId = testNotification.id();

        // When & Then - No authentication headers provided
        mockMvc.perform(get("/api/notifications/{id}/read", notificationId)).andExpect(status().isOk());
    }

    @Test
    void shouldHandleSecurityTestCases() throws Exception {
        // Test with valid notification ID
        UUID validId = testNotification.id();
        mockMvc.perform(get("/api/notifications/{id}/read", validId)).andExpect(status().isOk());

        // Test with non-existent notification ID (should return 404, not expose internals)
        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(get("/api/notifications/{id}/read", nonExistentId)).andExpect(status().isNotFound());

        // Test with invalid UUID format (should return 400, not expose internals)
        mockMvc.perform(get("/api/notifications/{id}/read", "invalid-uuid")).andExpect(status().isBadRequest());
    }
}

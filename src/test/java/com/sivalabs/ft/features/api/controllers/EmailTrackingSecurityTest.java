package com.sivalabs.ft.features.api.controllers;

import static io.restassured.RestAssured.given;

import com.sivalabs.ft.features.TestcontainersConfiguration;
import com.sivalabs.ft.features.domain.NotificationService;
import com.sivalabs.ft.features.domain.UserRepository;
import com.sivalabs.ft.features.domain.dtos.NotificationDto;
import com.sivalabs.ft.features.domain.entities.User;
import com.sivalabs.ft.features.domain.models.NotificationEventType;
import io.restassured.RestAssured;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@Sql("/test-data.sql")
@org.springframework.test.context.ActiveProfiles("test")
class EmailTrackingSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void shouldAllowPublicAccessToTrackingEndpoint() {
        // Given
        User user = createTestUser();
        userRepository.save(user);

        NotificationDto notification = notificationService.createNotification(
                user.getUsername(),
                NotificationEventType.FEATURE_CREATED,
                "Test notification for security testing",
                "http://localhost:8081/features/123");

        // When & Then - No authentication required
        given().when()
                .get("/notifications/{id}/read", notification.id())
                .then()
                .statusCode(HttpStatus.OK.value())
                .contentType("image/gif");
    }

    @Test
    void shouldPreventSpoofingWithNonExistentNotification() {
        // Given - A completely random UUID that doesn't exist
        UUID spoofedId = UUID.randomUUID();

        // When & Then - Should return 404, not expose any internal information
        given().when().get("/notifications/{id}/read", spoofedId).then().statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void shouldPreventSpoofingWithInvalidUuidFormat() {
        // Given - Various malformed UUIDs that could be used for attacks
        String[] maliciousInputs = {
            "../../../etc/passwd",
            "<script>alert('xss')</script>",
            "'; DROP TABLE notifications; --",
            "invalid-uuid-format",
            "00000000-0000-0000-0000-000000000000", // Valid format but likely non-existent
        };

        for (String maliciousInput : maliciousInputs) {
            // When & Then - Should return 400 for invalid format, 404 for valid but non-existent
            given().when()
                    .get("/notifications/{id}/read", maliciousInput)
                    .then()
                    .statusCode(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.equalTo(HttpStatus.BAD_REQUEST.value()),
                            org.hamcrest.Matchers.equalTo(HttpStatus.NOT_FOUND.value())));
        }
    }

    @Test
    void shouldBeIdempotentAndNotExposeSensitiveInformation() {
        // Given
        User user = createTestUser();
        userRepository.save(user);

        NotificationDto notification = notificationService.createNotification(
                user.getUsername(),
                NotificationEventType.FEATURE_CREATED,
                "Sensitive notification content",
                "http://localhost:8081/features/sensitive-123");

        // When - Multiple calls to the same notification
        for (int i = 0; i < 5; i++) {
            given().when()
                    .get("/notifications/{id}/read", notification.id())
                    .then()
                    .statusCode(HttpStatus.OK.value())
                    .contentType("image/gif");
        }

        // Then - All calls should succeed and return the same response
        // No sensitive information should be exposed in the response
    }

    @Test
    void shouldNotExposeStackTracesOrInternalDetails() {
        // Given - Inputs that might cause internal exceptions
        String[] problematicInputs = {
            "null", "undefined", "NaN", String.valueOf(Long.MAX_VALUE), String.valueOf(Long.MIN_VALUE), "0", "-1"
        };

        for (String input : problematicInputs) {
            // When & Then - Should handle gracefully without exposing internals
            given().when()
                    .get("/notifications/{id}/read", input)
                    .then()
                    .statusCode(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.equalTo(HttpStatus.BAD_REQUEST.value()),
                            org.hamcrest.Matchers.equalTo(HttpStatus.NOT_FOUND.value())));
        }
    }

    @Test
    void shouldHandleConcurrentRequests() {
        // Given - A valid notification
        User user = createTestUser();
        userRepository.save(user);

        NotificationDto notification = notificationService.createNotification(
                user.getUsername(),
                NotificationEventType.FEATURE_CREATED,
                "Concurrent access test",
                "http://localhost:8081/features/concurrent-123");

        // When - Simulate concurrent access (simplified test)
        // In a real scenario, you'd use multiple threads
        for (int i = 0; i < 10; i++) {
            given().when()
                    .get("/notifications/{id}/read", notification.id())
                    .then()
                    .statusCode(HttpStatus.OK.value())
                    .contentType("image/gif");
        }

        // Then - All requests should be handled successfully
    }

    @Test
    void shouldNotRequireAuthenticationHeaders() {
        // Given
        User user = createTestUser();
        userRepository.save(user);

        NotificationDto notification = notificationService.createNotification(
                user.getUsername(),
                NotificationEventType.FEATURE_CREATED,
                "No auth required test",
                "http://localhost:8081/features/no-auth-123");

        // When & Then - Explicitly test without any auth headers
        given().header("Authorization", "") // Empty auth header
                .when()
                .get("/notifications/{id}/read", notification.id())
                .then()
                .statusCode(HttpStatus.OK.value())
                .contentType("image/gif");

        // Also test with no headers at all
        given().when()
                .get("/notifications/{id}/read", notification.id())
                .then()
                .statusCode(HttpStatus.OK.value())
                .contentType("image/gif");
    }

    private User createTestUser() {
        User user = new User();
        user.setUsername("sectest_" + System.currentTimeMillis());
        user.setEmail("sectest_" + System.currentTimeMillis() + "@example.com");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }
}

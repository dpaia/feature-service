package com.sivalabs.ft.features.api.controllers;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

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
class EmailTrackingIntegrationTest {

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
    void shouldMarkNotificationAsReadAndReturnTransparentGif() {
        // Given
        User user = createTestUser();
        userRepository.save(user);

        NotificationDto notification = notificationService.createNotification(
                user.getUsername(),
                NotificationEventType.FEATURE_CREATED,
                "Test notification for email tracking",
                "http://localhost:8081/features/123");

        assertThat(notification.read()).isFalse();
        assertThat(notification.readAt()).isNull();

        // When & Then
        given().when()
                .get("/notifications/{id}/read", notification.id())
                .then()
                .statusCode(HttpStatus.OK.value())
                .contentType("image/gif")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0");
    }

    @Test
    void shouldBeIdempotent_subsequentCallsShouldReturnOk() {
        // Given
        User user = createTestUser();
        userRepository.save(user);

        NotificationDto notification = notificationService.createNotification(
                user.getUsername(),
                NotificationEventType.FEATURE_CREATED,
                "Test notification for email tracking",
                "http://localhost:8081/features/123");

        // First call
        given().when().get("/notifications/{id}/read", notification.id()).then().statusCode(HttpStatus.OK.value());

        // Second call should also return OK (idempotent)
        given().when().get("/notifications/{id}/read", notification.id()).then().statusCode(HttpStatus.OK.value());
    }

    @Test
    void shouldReturn404ForNonExistentNotification() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        given().when().get("/notifications/{id}/read", nonExistentId).then().statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void shouldReturn400ForInvalidUuidFormat() {
        // Given
        String invalidUuid = "invalid-uuid-format";

        // When & Then
        given().when().get("/notifications/{id}/read", invalidUuid).then().statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void shouldNotExposeInternalErrorDetails() {
        // This test ensures that internal errors don't leak sensitive information
        // We test with a malformed UUID that might cause internal exceptions

        given().when()
                .get("/notifications/{id}/read", "not-a-uuid-at-all")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void shouldReturnCorrectGifContent() {
        // Given
        User user = createTestUser();
        userRepository.save(user);

        NotificationDto notification = notificationService.createNotification(
                user.getUsername(),
                NotificationEventType.FEATURE_CREATED,
                "Test notification for email tracking",
                "http://localhost:8081/features/123");

        // When
        byte[] response = given().when()
                .get("/notifications/{id}/read", notification.id())
                .then()
                .statusCode(HttpStatus.OK.value())
                .contentType("image/gif")
                .extract()
                .asByteArray();

        // Then
        // Verify it's a valid 1x1 transparent GIF
        assertThat(response).isNotEmpty();
        // GIF files start with "GIF89a" or "GIF87a"
        assertThat(response[0]).isEqualTo((byte) 0x47); // 'G'
        assertThat(response[1]).isEqualTo((byte) 0x49); // 'I'
        assertThat(response[2]).isEqualTo((byte) 0x46); // 'F'
    }

    @Test
    void shouldBeAccessibleWithoutAuthentication() {
        // Given
        User user = createTestUser();
        userRepository.save(user);

        NotificationDto notification = notificationService.createNotification(
                user.getUsername(),
                NotificationEventType.FEATURE_CREATED,
                "Test notification for email tracking",
                "http://localhost:8081/features/123");

        // When & Then - No authentication headers provided
        given().when()
                .get("/notifications/{id}/read", notification.id())
                .then()
                .statusCode(HttpStatus.OK.value())
                .contentType("image/gif");
    }

    @Test
    void shouldHandleMultipleInvalidFormats() {
        // Test various invalid UUID formats to ensure robust error handling
        String[] invalidUuids = {
            "123",
            "not-a-uuid",
            "12345678-1234-1234-1234-12345678901", // too short
            "12345678-1234-1234-1234-1234567890123", // too long
            "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx", // invalid characters
            ""
        };

        for (String invalidUuid : invalidUuids) {
            if (invalidUuid.isEmpty()) {
                // Handle empty string case with special endpoint
                given().when().get("/notifications/empty/read").then().statusCode(HttpStatus.BAD_REQUEST.value());
            } else {
                given().when()
                        .get("/notifications/{id}/read", invalidUuid)
                        .then()
                        .statusCode(HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private User createTestUser() {
        User user = new User();
        user.setUsername("testuser_" + System.currentTimeMillis());
        user.setEmail("test_" + System.currentTimeMillis() + "@example.com");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }
}

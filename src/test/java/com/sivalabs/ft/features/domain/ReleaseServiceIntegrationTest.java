package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

@DisplayName("ReleaseService API Integration Tests")
@Sql(scripts = {"/test-data.sql"})
class ReleaseServiceIntegrationTest extends AbstractIT {

    @Test
    @DisplayName("Should create release and validate status transitions")
    @WithMockOAuth2User(username = "user")
    void testCompleteReleaseLifecycle() {
        // Create a new release in DRAFT status
        var createPayload = String.format(
                """
            {
                "productCode": "intellij",
                "code": "LIFECYCLE-TEST-1",
                "description": "Test release lifecycle",
                "plannedReleaseDate": "%s",
                "releaseOwner": "test.user"
            }
            """,
                Instant.now().plus(30, ChronoUnit.DAYS));

        var createResponse = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();

        assertThat(createResponse).hasStatus2xxSuccessful();

        // Verify it was created by fetching it
        var getResponse =
                mvc.get().uri("/api/releases/{code}", "IDEA-LIFECYCLE-TEST-1").exchange();

        assertThat(getResponse)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("DRAFT");

        // Transition DRAFT -> PLANNED (valid)
        var updateToPlanned =
                """
            {
                "description": "Updated description",
                "status": "PLANNED"
            }
            """;

        var updateResponse = mvc.put()
                .uri("/api/releases/{code}", "IDEA-LIFECYCLE-TEST-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateToPlanned)
                .exchange();

        assertThat(updateResponse).hasStatus2xxSuccessful();

        // Verify status changed to PLANNED
        getResponse =
                mvc.get().uri("/api/releases/{code}", "IDEA-LIFECYCLE-TEST-1").exchange();

        assertThat(getResponse)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("PLANNED");

        // Try invalid transition PLANNED -> COMPLETED (should fail)
        var invalidUpdate =
                """
            {
                "description": "Invalid transition",
                "status": "COMPLETED"
            }
            """;

        var invalidResponse = mvc.put()
                .uri("/api/releases/{code}", "IDEA-LIFECYCLE-TEST-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidUpdate)
                .exchange();

        assertThat(invalidResponse).hasStatus4xxClientError();

        // Valid transition PLANNED -> IN_PROGRESS
        var updateToInProgress =
                """
            {
                "description": "Starting development",
                "status": "IN_PROGRESS"
            }
            """;

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-LIFECYCLE-TEST-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateToInProgress)
                .exchange();

        // Verify status is IN_PROGRESS
        getResponse =
                mvc.get().uri("/api/releases/{code}", "IDEA-LIFECYCLE-TEST-1").exchange();

        assertThat(getResponse)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("IN_PROGRESS");

        // Valid transition IN_PROGRESS -> COMPLETED
        var updateToCompleted =
                """
            {
                "description": "Released",
                "status": "COMPLETED",
                "releasedAt": "%s"
            }
            """
                        .formatted(Instant.now());

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-LIFECYCLE-TEST-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateToCompleted)
                .exchange();

        // Verify status is COMPLETED
        getResponse =
                mvc.get().uri("/api/releases/{code}", "IDEA-LIFECYCLE-TEST-1").exchange();

        assertThat(getResponse)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Should find overdue releases")
    @WithMockOAuth2User(username = "user")
    void testFindOverdueReleases() {
        // Create an overdue release
        String pastDate = Instant.now().minus(10, ChronoUnit.DAYS).toString();
        var createPayload = String.format(
                """
            {
                "productCode": "intellij",
                "code": "OVERDUE-API-TEST",
                "description": "Overdue test",
                "plannedReleaseDate": "%s",
                "releaseOwner": "test.user"
            }
            """,
                pastDate);

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();

        // Update to IN_PROGRESS (not completed)
        var updatePayload =
                """
            {
                "description": "In progress",
                "status": "IN_PROGRESS"
            }
            """;

        mvc.put()
                .uri("/api/releases/{code}", "IDEA-OVERDUE-API-TEST")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();

        // Query overdue releases
        var result = mvc.get().uri("/api/releases/overdue?page=0&size=100").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content")
                .asArray()
                .anySatisfy(item -> assertThat(item.toString()).contains("OVERDUE-API-TEST"));
    }

    @Test
    @DisplayName("Should find at-risk releases")
    @WithMockOAuth2User(username = "user")
    void testFindAtRiskReleases() {
        // Create release 5 days in future
        String futureDate = Instant.now().plus(5, ChronoUnit.DAYS).toString();
        var payload = String.format(
                """
            {
                "productCode": "intellij",
                "code": "ATRISK-API-TEST",
                "description": "At-risk test",
                "plannedReleaseDate": "%s",
                "releaseOwner": "test.user"
            }
            """,
                futureDate);

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        // Query with 7-day threshold
        var result = mvc.get().uri("/api/releases/at-risk?daysThreshold=7").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content")
                .asArray()
                .anySatisfy(item -> assertThat(item.toString()).contains("ATRISK-API-TEST"));
    }

    @Test
    @DisplayName("Should filter by status with pagination")
    void testFindByStatusWithPagination() {
        var result = mvc.get()
                .uri("/api/releases/by-status?status=DRAFT&page=0&size=10&sort=createdAt&direction=desc")
                .exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content[*].status")
                .asArray()
                .allMatch(status -> status.equals("DRAFT"));
    }

    @Test
    @DisplayName("Should filter by owner")
    @WithMockOAuth2User(username = "user")
    void testFindByOwner() {
        // Create release with specific owner
        var payload =
                """
            {
                "productCode": "intellij",
                "code": "OWNER-API-TEST",
                "description": "Owner filter test",
                "releaseOwner": "specific.owner"
            }
            """;

        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        var result =
                mvc.get().uri("/api/releases/by-owner?owner=specific.owner").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content[*].releaseOwner")
                .asArray()
                .allMatch(owner -> owner.equals("specific.owner"));
    }

    @Test
    @DisplayName("Should filter by date range")
    void testFindByDateRange() {
        // Use a broader date range to include test data from test-data.sql
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end = Instant.parse("2024-12-31T23:59:59Z");

        var result = mvc.get()
                .uri("/api/releases/by-date-range?startDate={start}&endDate={end}", start, end)
                .exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content")
                .asArray()
                .anySatisfy(item -> assertThat(item.toString()).contains("plannedReleaseDate"));
    }

    @Test
    @DisplayName("Should use multi-filter query")
    void testFindWithMultipleFilters() {
        // Use date range that includes the test data releases
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end = Instant.parse("2024-12-31T23:59:59Z");

        var result = mvc.get()
                .uri("/api/releases?productCode=intellij&status=DRAFT&startDate={start}&endDate={end}", start, end)
                .exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content[*].status")
                .asArray()
                .anySatisfy(status -> assertThat(status.toString()).isEqualTo("DRAFT"));
    }
}

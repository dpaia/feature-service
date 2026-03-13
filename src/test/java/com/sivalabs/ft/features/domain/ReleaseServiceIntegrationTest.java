package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

@DisplayName("ReleaseService API Integration Tests")
@Sql(scripts = {"/test-data.sql"})
class ReleaseServiceIntegrationTest extends AbstractIT {

    @Test
    @DisplayName("Should find overdue releases")
    void testFindOverdueReleases() {
        // Query overdue releases - IDEA-OVERDUE-1 is already in test-data.sql
        var result = mvc.get().uri("/api/releases/overdue?page=0&size=100").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content")
                .asArray()
                .anySatisfy(item -> assertThat(item.toString()).contains("IDEA-OVERDUE-1"));
    }

    @Test
    @DisplayName("Should find at-risk releases")
    void testFindAtRiskReleases() {
        // Query with 7-day threshold - IDEA-AT-RISK-1 is already in test-data.sql
        var result = mvc.get().uri("/api/releases/at-risk?daysThreshold=7").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content")
                .asArray()
                .anySatisfy(item -> assertThat(item.toString()).contains("IDEA-AT-RISK-1"));
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
    void testFindByOwner() {
        // GO-OWNED-1 has owner 'owner@example.com' in test-data.sql
        var result =
                mvc.get().uri("/api/releases/by-owner?owner=owner@example.com").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content[*].owner")
                .asArray()
                .allMatch(owner -> owner.equals("owner@example.com"));
    }

    @Test
    @DisplayName("Should filter by date range")
    void testFindByDateRange() {
        // Use a broader date range to include test data from test-data.sql
        // IDEA-OVERDUE-1 is 2020-01-01
        Instant start = Instant.parse("2020-01-01T00:00:00Z");
        Instant end = Instant.parse("2030-12-31T23:59:59Z");

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
}

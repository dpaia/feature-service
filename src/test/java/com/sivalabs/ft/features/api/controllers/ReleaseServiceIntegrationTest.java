package com.sivalabs.ft.features.api.controllers;

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
}

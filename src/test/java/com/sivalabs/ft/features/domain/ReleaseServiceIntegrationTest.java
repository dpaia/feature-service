package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReleaseServiceIntegrationTest extends AbstractIT {

    @Test
    @DisplayName("Should use multi-filter query")
    void testFindWithMultipleFilters() {
        // Use date range that includes the test data releases
        Instant start = Instant.parse("2020-01-01T00:00:00Z");
        Instant end = Instant.parse("2020-12-31T23:59:59Z");

        var result = mvc.get()
                .uri(
                        "/api/releases?productCode=intellij&status=IN_PROGRESS&startDate={start}&endDate={end}",
                        start,
                        end)
                .exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.content[*].status")
                .asArray()
                .anySatisfy(status -> assertThat(status.toString()).isEqualTo("IN_PROGRESS"));
    }
}

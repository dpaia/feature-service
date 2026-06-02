package com.sivalabs.ft.features.api.controllers;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertWith;
import static org.awaitility.Awaitility.await;

import com.sivalabs.ft.features.AbstractIT;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

public class FeatureUsageAbstractIT extends AbstractIT {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected void createFeatureUsageViaAPI(String actionType, String featureCode, String productCode) {
        String requestBody = String.format(
                """
                {
                    "actionType": "%s",
                    "featureCode": "%s",
                    "productCode": "%s"
                }
                """,
                actionType, featureCode, productCode);

        var result = mvc.post()
                .uri("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();

        assertThat(result).hasStatus(202).hasBodyTextEqualTo("").doesNotContainHeader("Location");
    }

    protected void cleanFeatureUsageTable() {
        jdbcTemplate.execute("DELETE FROM feature_usage");
    }

    protected Integer getFeatureUsageCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feature_usage", Integer.class);
    }

    protected void assertFeatureUsageCount(int expectedCount) {
        assertThat(getFeatureUsageCount()).isEqualTo(expectedCount);
    }

    protected void awaitFeatureUsageCount(int expectedCount) {
        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertFeatureUsageCount(expectedCount);
        });
    }

    protected Map<String, Object> awaitFeatureUsageCreated() {
        awaitFeatureUsageCount(1);
        return jdbcTemplate.queryForMap("SELECT * FROM feature_usage");
    }

    protected void awaitFeatureUsageCreated(Consumer<Map<String, Object>> assertions) {
        assertWith(awaitFeatureUsageCreated(), assertions);
    }

    protected void verifyFeatureUsageCountStaysAt(int expectedCount) {
        // For negative tests: assert condition stays true throughout a time window
        await().during(3, SECONDS).atMost(10, SECONDS).untilAsserted(() -> {
            assertFeatureUsageCount(expectedCount);
        });
    }
}

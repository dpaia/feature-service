package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.sivalabs.ft.features.TestcontainersConfiguration;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// Suppress unchecked warnings for casting of json responses
/** @noinspection unchecked */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@WithMockOAuth2User
@Sql(scripts = {"/risk-level-boundary-test-data.sql"})
public class RoadmapRiskLevelBoundaryIntegrationTest {

    @Autowired
    protected MockMvcTester mvc;

    @Test
    void shouldReturnMediumRiskWhenExactlyTenPercentFeaturesAreOnHold() {
        var result = mvc.get().uri("/api/roadmap").exchange();

        assertThat(result).hasStatusOk().bodyJson().convertTo(Map.class).satisfies(response -> {
            List<Map<String, Object>> roadmapItems = (List<Map<String, Object>>) response.get("roadmapItems");
            assertThat(roadmapItems).hasSize(1);

            Map<String, Object> item = roadmapItems.get(0);
            Map<String, Object> progressMetrics = (Map<String, Object>) item.get("progressMetrics");
            Map<String, Object> healthIndicators = (Map<String, Object>) item.get("healthIndicators");

            assertThat(progressMetrics.get("totalFeatures")).isEqualTo(10);
            assertThat(progressMetrics.get("onHoldFeatures")).isEqualTo(1);

            // When exactly 10% of features are on-hold, risk level should be MEDIUM
            assertThat(healthIndicators.get("riskLevel"))
                    .as("Risk level should be MEDIUM when exactly 10%% of features are on-hold")
                    .isEqualTo("MEDIUM");
        });
    }

    @Test
    void shouldExportCsvWithMediumRiskWhenExactlyTenPercentFeaturesAreOnHold() {
        var result = mvc.get().uri("/api/roadmap/export?format=CSV").exchange();

        assertThat(result).hasStatusOk().body().asString().satisfies(content -> {
            String[] lines = content.split("\n");
            assertThat(lines.length).isEqualTo(2); // Header + 1 data row

            // Risk Level is the last (17th) column in the CSV
            String[] dataRow = lines[1].split(",");
            assertThat(dataRow[dataRow.length - 1].trim())
                    .as("CSV Risk Level column should be MEDIUM when exactly 10%% of features are on-hold")
                    .isEqualTo("MEDIUM");
        });
    }
}

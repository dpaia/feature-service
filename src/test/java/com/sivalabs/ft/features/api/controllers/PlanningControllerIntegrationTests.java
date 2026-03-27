package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PlanningControllerIntegrationTests extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM favorite_features");
        jdbcTemplate.execute("DELETE FROM comments");
        jdbcTemplate.execute("DELETE FROM features");
        jdbcTemplate.execute("DELETE FROM releases");
        jdbcTemplate.execute("DELETE FROM products");

        jdbcTemplate.update(
                """
                INSERT INTO products (id, code, prefix, name, description, image_url, disabled, created_by, created_at) VALUES
                (1, 'intellij', 'IDEA', 'IntelliJ IDEA', 'JetBrains IDE for Java',
                    'https://resources.jetbrains.com/storage/products/company/brand/logos/IntelliJ_IDEA.png', false, 'admin', '2024-03-01 00:00:00'),
                (2, 'goland', 'GO', 'GoLand', 'JetBrains IDE for Go',
                    'https://resources.jetbrains.com/storage/products/company/brand/logos/GoLand.png', false, 'admin', '2024-03-01 00:00:00')
                """);
        jdbcTemplate.update(
                """
                INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
                (1, 1, 'OLD-DRAFT-1', 'Old draft 1', 'DRAFT', 'admin', '2023-01-01 00:00:00'),
                (2, 1, 'OLD-DRAFT-2', 'Old draft 2', 'DRAFT', 'admin', '2023-02-01 00:00:00')
                """);
        jdbcTemplate.update(
                """
                INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
                (4, 1, 'INPROG-OVERDUE-1', 'In-progress overdue 1', 'IN_PROGRESS', 'admin', '2023-11-01 00:00:00'),
                (5, 1, 'INPROG-OVERDUE-2', 'In-progress overdue 2', 'IN_PROGRESS', 'admin', '2023-12-01 00:00:00')
                """);
    }

    @Test
    void shouldGetAllFieldsInHealthReport() throws Exception {
        var result = mvc.get().uri("/api/planning/health").exchange();

        assertThat(result).hasStatus2xxSuccessful();
        assertThat(result.getResponse().getContentType()).isEqualTo("application/json");
        var responseBody = result.getResponse().getContentAsString();
        final Map<String, Object> healthReport = objectMapper.readValue(responseBody, new TypeReference<>() {});

        assertThat(healthReport).containsKeys("releasesByStatus", "atRiskReleases", "planningAccuracy");

        Map<String, Object> byStatus = (Map<String, Object>) healthReport.get("releasesByStatus");
        assertThat(byStatus).containsKeys("DRAFT", "IN_PROGRESS");
    }

    @Test
    void shouldGetAllFieldsInTrendsReport() throws Exception {
        var result = mvc.get().uri("/api/planning/trends").exchange();
        assertThat(result).hasStatus2xxSuccessful();
        assertThat(result.getResponse().getContentType()).isEqualTo("application/json");
        var responseBody = result.getResponse().getContentAsString();
        final Map<String, Object> trendsReport = objectMapper.readValue(responseBody, new TypeReference<>() {});

        assertThat(trendsReport).containsKeys("releasesCompleted", "averageReleaseDuration", "planningAccuracyTrend");

        Map<String, Object> releasesCompleted = (Map<String, Object>) trendsReport.get("releasesCompleted");
        assertThat(releasesCompleted).containsKeys("trend", "total");

        Map<String, Object> duration = (Map<String, Object>) trendsReport.get("averageReleaseDuration");
        assertThat(duration).containsKeys("trend", "current");

        Map<String, Object> accuracyTrend = (Map<String, Object>) trendsReport.get("planningAccuracyTrend");
        assertThat(accuracyTrend).containsKey("onTimeDelivery");
    }

    @Test
    void shouldReturnAllRequiredCapacityFields() throws Exception {
        var result = mvc.get().uri("/api/planning/capacity").exchange();
        assertThat(result).hasStatus2xxSuccessful();
        assertThat(result.getResponse().getContentType()).isEqualTo("application/json");
        var responseBody = result.getResponse().getContentAsString();
        final Map<String, Object> capacityReport = objectMapper.readValue(responseBody, new TypeReference<>() {});

        assertThat(capacityReport)
                .containsKeys("overallCapacity", "workloadByOwner", "commitments", "overallocationWarnings");
    }
}

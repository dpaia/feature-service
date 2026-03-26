package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PlanningHistoryControllerGoldenTests extends AbstractIT {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetPlanningHistoryWithDefaultPagination() throws Exception {
        var result = mvc.get().uri("/api/planning-history").exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        // Verify pagination structure - MUST contain "number", NOT "page"
        assertThat(response).containsKeys("content", "totalElements", "totalPages", "size", "number");
        assertThat(response).doesNotContainKey("page");
        assertThat(response).doesNotContainKey("pageable");

        assertThat(response.get("size")).isEqualTo(20);
        assertThat(response.get("number")).isEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetPlanningHistoryWithCustomPagination() throws Exception {
        var result = mvc.get()
                .uri("/api/planning-history?page=0&size=5&sort=changedAt,asc")
                .exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        assertThat(response.get("size")).isEqualTo(5);
        assertThat(response.get("number")).isEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldHandlePaginationBeyondTotalPages() throws Exception {
        var result = mvc.get().uri("/api/planning-history?page=1000&size=20").exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        assertThat(response.get("number")).isEqualTo(1000);
        List<?> content = (List<?>) response.get("content");
        assertThat(content).isEmpty();
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetPlanningHistorySecondPage() throws Exception {
        // Request page 1 with size 2 (there are several records in test-data.sql)
        var result =
                mvc.get().uri("/api/planning-history?page=1&size=2&sort=id,asc").exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        assertThat(response.get("size")).isEqualTo(2);
        assertThat(response.get("number")).isEqualTo(1);

        List<?> content = (List<?>) response.get("content");
        assertThat(content).hasSize(2);

        // Check "first" and "last" fields
        assertThat(response.get("first")).isEqualTo(false);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetFeatureHistoryWithCorrectPaginationStructure() throws Exception {
        // IDEA-1 exists in test-data.sql
        var result = mvc.get().uri("/api/features/IDEA-1/history").exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        assertThat(response).containsKeys("content", "totalElements", "totalPages", "size", "number");
        assertThat(response).doesNotContainKey("page");
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetReleaseHistoryWithCorrectPaginationStructure() throws Exception {
        // IDEA-2023.3.8 exists in test-data.sql
        var result = mvc.get().uri("/api/releases/IDEA-2023.3.8/history").exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        assertThat(response).containsKeys("content", "totalElements", "totalPages", "size", "number");
        assertThat(response).doesNotContainKey("page");
        assertThat(response).doesNotContainKey("pageable");
    }
}

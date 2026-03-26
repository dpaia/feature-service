package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class PlanningHistoryControllerTests extends AbstractIT {
    @Autowired
    private ObjectMapper objectMapper;

    record EntityInfo(String code, Long id) {}

    // ==================== Helper Methods ====================

    private EntityInfo createFeatureForHistoryTest(String title) throws Exception {
        var payload = String.format(
                """
                        {
                            "productCode": "intellij",
                            "releaseCode": "IDEA-2023.3.8",
                            "title": "%s",
                            "description": "Test feature for planning history",
                            "assignedTo": "developer"
                        }
                        """,
                title);

        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);

        String location = result.getMvcResult().getResponse().getHeader("Location");
        String code = location.substring(location.lastIndexOf("/") + 1);

        // Fetch the feature to get its ID
        var getResult = mvc.get().uri("/api/features/{code}", code).exchange();
        assertThat(getResult).hasStatusOk();
        String responseBody = getResult.getMvcResult().getResponse().getContentAsString();
        FeatureDto featureDto = objectMapper.readValue(responseBody, FeatureDto.class);

        return new EntityInfo(code, featureDto.id());
    }

    private void updateFeatureForHistoryTest(String featureCode, String newTitle, String newDescription) {
        var payload = String.format(
                """
                        {
                            "title": "%s",
                            "description": "%s",
                            "assignedTo": "developer",
                            "status": "IN_PROGRESS"
                        }
                        """,
                newTitle, newDescription);

        var result = mvc.put()
                .uri("/api/features/{code}", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).hasStatusOk();
    }

    @Test
    void shouldGetAllPlanningHistory() {
        var result = mvc.get().uri("/api/planning-history").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(6));
    }

    @Test
    void shouldFilterPlanningHistoryByEntityType() {
        var result = mvc.get().uri("/api/planning-history?entityType=FEATURE").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(4));
    }

    @Test
    void shouldFilterPlanningHistoryByEntityCode() {
        var result = mvc.get().uri("/api/planning-history?entityCode=IDEA-1").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(3));
    }

    @Test
    void shouldFilterPlanningHistoryByChangeType() {
        var result = mvc.get().uri("/api/planning-history?changeType=CREATED").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(3));
    }

    @Test
    void shouldFilterPlanningHistoryByChangedBy() {
        var result = mvc.get().uri("/api/planning-history?changedBy=admin").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(2));
    }

    @Test
    void shouldReturnPagedResult() {
        var result = mvc.get().uri("/api/planning-history?page=0&size=2").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content.size()")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isEqualTo(2));
        assertThat(result)
                .bodyJson()
                .extractingPath("$.size")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isEqualTo(2));
        assertThat(result).bodyJson().extractingPath("$.first").isEqualTo(true);
    }

    @Test
    void shouldReturnEmptyPageWhenNoResults() {
        var result = mvc.get()
                .uri("/api/planning-history?entityCode=NONEXISTENT-CODE")
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isEqualTo(0));
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content.size()")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isEqualTo(0));
    }

    @Test
    void shouldGetFeatureHistory() {
        var result = mvc.get().uri("/api/features/{code}/history", "IDEA-1").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(3));
    }

    @Test
    void shouldGetReleaseHistory() {
        var result =
                mvc.get().uri("/api/releases/{code}/history", "IDEA-2023.3.8").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(2));
    }

    @Test
    void shouldReturnEmptyHistoryForUnknownCode() {
        var result =
                mvc.get().uri("/api/features/{code}/history", "UNKNOWN-999").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isEqualTo(0));
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldTrackFeatureCreation() {
        var payload =
                """
            {
                "productCode": "intellij",
                "releaseCode": "IDEA-2023.3.8",
                "title": "History Test Feature",
                "description": "Feature for testing history tracking"
            }
            """;

        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(createResult).hasStatus(HttpStatus.CREATED);

        String location = createResult.getMvcResult().getResponse().getHeader("Location");
        String featureCode = location.substring(location.lastIndexOf("/") + 1);

        var historyResult =
                mvc.get().uri("/api/features/{code}/history", featureCode).exchange();
        assertThat(historyResult).hasStatusOk();
        assertThat(historyResult)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(1));
        assertThat(historyResult)
                .bodyJson()
                .extractingPath("$.content[0].changeType")
                .isEqualTo("CREATED");
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldTrackFeatureStatusChange() {
        var payload =
                """
            {
                "title": "Feature for status change",
                "status": "IN_PROGRESS"
            }
            """;

        mvc.put()
                .uri("/api/features/{code}", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        var historyResult = mvc.get()
                .uri("/api/planning-history?entityCode=IDEA-2&changeType=STATUS_CHANGED")
                .exchange();
        assertThat(historyResult).hasStatusOk();
        assertThat(historyResult)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(1));
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldTrackFeatureDeletion() {
        mvc.delete().uri("/api/features/{code}", "IDEA-2").exchange();

        var historyResult = mvc.get()
                .uri("/api/planning-history?entityCode=IDEA-2&changeType=DELETED")
                .exchange();
        assertThat(historyResult).hasStatusOk();
        assertThat(historyResult)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(1));
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldTrackAllUpdatedFields() throws Exception {
        EntityInfo feature = createFeatureForHistoryTest("All Fields Update Test");

        var payload =
                """
                {
                    "title": "New Title",
                    "description": "New Description",
                    "assignedTo": "new-developer",
                    "status": "IN_PROGRESS",
                    "releaseCode": "IDEA-2024.2.3",
                    "plannedCompletionAt": "2026-12-31T23:59:59Z",
                    "actualCompletionAt": "2026-12-25T10:00:00Z",
                    "featurePlanningStatus": "DONE",
                    "featureOwner": "New Owner",
                    "blockageReason": "None anymore"
                }
                """;

        var updateResult = mvc.put()
                .uri("/api/features/{code}", feature.code())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(updateResult).hasStatusOk();

        var historyResult =
                mvc.get().uri("/api/features/{code}/history", feature.code()).exchange();
        assertThat(historyResult).hasStatusOk();

        String responseBody = historyResult.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");

        // 1 (CREATED) + 1 (MOVED) + 1 (STATUS_CHANGED) + 1 (ASSIGNED) + 7 (UPDATED) = 11
        assertThat(response.get("totalElements")).isEqualTo(11);

        // Verify UPDATED fields
        assertFieldChange(content, "UPDATED", "title", "New Title");
        assertFieldChange(content, "UPDATED", "description", "New Description");
        assertFieldChange(content, "UPDATED", "plannedCompletionAt", "2026-12-31T23:59:59Z");
        assertFieldChange(content, "UPDATED", "actualCompletionAt", "2026-12-25T10:00:00Z");
        assertFieldChange(content, "UPDATED", "featurePlanningStatus", "DONE");
        assertFieldChange(content, "UPDATED", "featureOwner", "New Owner");
        assertFieldChange(content, "UPDATED", "blockageReason", "None anymore");

        // Verify specific transition types
        assertFieldChange(content, "STATUS_CHANGED", "status", "IN_PROGRESS");
        assertFieldChange(content, "ASSIGNED", "assignedTo", "new-developer");
        assertFieldChange(content, "MOVED", "releaseCode", "IDEA-2024.2.3");
    }

    private void assertFieldChange(
            List<Map<String, Object>> content, String changeType, String fieldName, String newValue) {
        content.stream()
                .filter(h -> changeType.equals(h.get("changeType")) && fieldName.equals(h.get("fieldName")))
                .findFirst()
                .ifPresentOrElse(h -> assertThat(h.get("newValue")).isEqualTo(newValue), () -> {
                    throw new AssertionError(String.format("History entry not found for %s:%s", changeType, fieldName));
                });
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetFeatureHistoryByCode() throws Exception {
        // Create and update a feature to generate history
        EntityInfo feature = createFeatureForHistoryTest("Feature for History Test");
        updateFeatureForHistoryTest(feature.code(), "Updated Title", "Updated description for history test");

        var result =
                mvc.get().uri("/api/features/{code}/history", feature.code()).exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");

        // Expected: CREATED + MOVED (release cleared) + STATUS_CHANGED + UPDATED (title) + UPDATED (description) = 5
        assertThat(response.get("totalElements")).isEqualTo(5);

        // Verify CREATED record exists
        assertThat(content.stream().anyMatch(h -> "CREATED".equals(h.get("changeType"))))
                .isTrue();

        // Verify UPDATED title record exists
        assertFieldChange(content, "UPDATED", "title", "Updated Title");

        // Verify UPDATED description record exists
        assertFieldChange(content, "UPDATED", "description", "Updated description for history test");
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldNotRecordHistoryWhenNoFieldsChanged() throws Exception {
        EntityInfo feature = createFeatureForHistoryTest("No Change Test");

        // Update with same values
        var payload =
                """
                {
                    "title": "No Change Test",
                    "description": "Test feature for planning history",
                    "assignedTo": "developer",
                    "status": "NEW",
                    "releaseCode": "IDEA-2023.3.8"
                }
                """;
        var updateResult = mvc.put()
                .uri("/api/features/{code}", feature.code())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(updateResult).hasStatusOk();

        var result =
                mvc.get().uri("/api/features/{code}/history", feature.code()).exchange();
        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");

        // Only CREATED should be there (size 1)
        assertThat(content).hasSize(1);
    }
}

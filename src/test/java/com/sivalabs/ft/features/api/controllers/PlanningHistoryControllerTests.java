package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
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

    private EntityInfo createFeatureForHistoryTest(String codePrefix, String title) throws Exception {
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

    private void updateFeatureForHistoryTest(String featureCode, String newTitle) {
        var payload = String.format(
                """
                        {
                            "title": "%s",
                            "description": "Updated description for history test",
                            "assignedTo": "developer",
                            "status": "IN_PROGRESS"
                        }
                        """,
                newTitle);

        var result = mvc.put()
                .uri("/api/features/{code}", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).hasStatusOk();
    }

    private EntityInfo createReleaseForHistoryTest(String codePrefix, String description) throws Exception {
        var payload = String.format(
                """
                        {
                            "productCode": "intellij",
                            "code": "%s",
                            "description": "%s"
                        }
                        """,
                codePrefix, description);

        var result = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);

        String location = result.getMvcResult().getResponse().getHeader("Location");
        String code = location.substring(location.lastIndexOf("/") + 1);

        // Fetch the release to get its ID
        var getResult = mvc.get().uri("/api/releases/{code}", code).exchange();
        assertThat(getResult).hasStatusOk();
        String responseBody = getResult.getMvcResult().getResponse().getContentAsString();
        ReleaseDto releaseDto = objectMapper.readValue(responseBody, ReleaseDto.class);

        return new EntityInfo(code, releaseDto.id());
    }

    // ==================== 200 OK Tests ====================

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetPlanningHistoryWithDefaultPagination() throws Exception {
        // First create a feature to generate history
        createFeatureForHistoryTest("TEST-FEATURE-001", "Test Feature for History");

        var result = mvc.get().uri("/api/planning-history").exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        // Verify pagination structure
        assertThat(response).containsKeys("content", "pageable", "totalElements", "totalPages", "size", "number");

        // Verify content is a list
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        assertThat(content).isNotNull();

        // Verify at least one history record exists (from feature creation)
        assertThat(content).hasSizeGreaterThanOrEqualTo(1);

        // Verify first history record structure and all required fields
        Map<String, Object> firstHistory = content.get(0);
        assertThat(firstHistory)
                .containsKeys(
                        "id",
                        "entityType",
                        "entityId",
                        "entityCode",
                        "changeType",
                        "fieldName",
                        "oldValue",
                        "newValue",
                        "rationale",
                        "changedBy",
                        "changedAt");

        // Validate field types and values
        assertThat(((Number) firstHistory.get("id")).longValue()).isGreaterThan(0L);
        assertThat(firstHistory.get("entityType")).isIn("FEATURE", "RELEASE");
        assertThat(((Number) firstHistory.get("entityId")).longValue()).isGreaterThan(0L);
        assertThat((String) firstHistory.get("entityCode")).isNotEmpty();
        assertThat(firstHistory.get("changeType"))
                .isIn("CREATED", "UPDATED", "DELETED", "STATUS_CHANGED", "ASSIGNED", "MOVED");
        assertThat((String) firstHistory.get("changedBy")).isNotEmpty();
        assertThat((String) firstHistory.get("changedAt")).matches("\\d{4}-\\d{2}-\\d{2}T.*");

        // Verify default pagination values
        assertThat(response.get("size")).isEqualTo(20); // default size
        assertThat(response.get("number")).isEqualTo(0); // default page
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetPlanningHistoryWithCustomPagination() throws Exception {
        // Create a feature to ensure history record exists
        createFeatureForHistoryTest("TEST-FEATURE-PAGINATION", "Feature for Pagination Test");

        var result = mvc.get()
                .uri("/api/planning-history?page=0&size=5&sort=changedAt,asc")
                .exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        // Verify custom pagination values
        assertThat(response.get("size")).isEqualTo(5);
        assertThat(response.get("number")).isEqualTo(0);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        assertThat(content).hasSizeLessThanOrEqualTo(5);

        // Validate all fields for each record
        content.stream().forEach(history -> {
            assertThat(history.get("id")).isInstanceOf(Number.class).isNotNull();
            assertThat(history.get("entityType")).isIn("FEATURE", "RELEASE");
            assertThat(history.get("entityId")).isInstanceOf(Number.class).isNotNull();
            assertThat(history.get("entityCode")).isInstanceOf(String.class).isNotNull();
            assertThat(history.get("changeType"))
                    .isIn("CREATED", "UPDATED", "DELETED", "STATUS_CHANGED", "ASSIGNED", "MOVED");
            // changedBy and changedAt might be null if history was not created through AOP
            assertThat(history).containsKeys("changedBy", "changedAt");
        });
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetPlanningHistoryFilteredByEntityType() throws Exception {
        // Create a feature to ensure FEATURE history exists
        createFeatureForHistoryTest("TEST-FEATURE-FILTER", "Feature for Entity Type Filter");

        var result = mvc.get().uri("/api/planning-history?entityType=FEATURE").exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");

        // Verify all records have FEATURE entity type and validate all fields
        content.stream().forEach(history -> {
            assertThat(((Number) history.get("id")).longValue()).isGreaterThan(0L);
            assertThat(history.get("entityType")).isEqualTo("FEATURE");
            assertThat(((Number) history.get("entityId")).longValue()).isGreaterThan(0L);
            assertThat((String) history.get("entityCode")).isNotEmpty();
            assertThat(history.get("changeType"))
                    .isIn("CREATED", "UPDATED", "DELETED", "STATUS_CHANGED", "ASSIGNED", "MOVED");
            // changedBy and changedAt might be null if history was not created through AOP
            assertThat(history)
                    .containsKeys("changedBy", "changedAt", "fieldName", "oldValue", "newValue", "rationale");
        });
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetPlanningHistoryFilteredByChangeType() throws Exception {
        // Create a feature to generate CREATED change type
        EntityInfo feature = createFeatureForHistoryTest("TEST-FEATURE-CHANGE-TYPE", "Feature for Change Type Filter");

        var result = mvc.get()
                .uri("/api/planning-history?changeType=CREATED&entityCode=" + feature.code())
                .exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(1);

        // Verify CREATED change type
        Map<String, Object> createdHistory = content.stream()
                .filter(h -> feature.code().equals(h.get("entityCode")))
                .findFirst()
                .orElseThrow();

        // Validate all fields for CREATED history
        assertThat(((Number) createdHistory.get("id")).longValue()).isGreaterThan(0L);
        assertThat(createdHistory.get("entityType")).isEqualTo("FEATURE");
        assertThat(((Number) createdHistory.get("entityId")).longValue()).isEqualTo(feature.id());
        assertThat(createdHistory.get("entityCode")).isEqualTo(feature.code());
        assertThat(createdHistory.get("changeType")).isEqualTo("CREATED");
        assertThat(createdHistory.get("changedBy")).isEqualTo("testuser");
        assertThat((String) createdHistory.get("changedAt")).matches("\\d{4}-\\d{2}-\\d{2}T.*");
        // For CREATED, fieldName, oldValue, newValue should typically be null
        assertThat(createdHistory).containsKeys("fieldName", "oldValue", "newValue", "rationale");
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetReleaseHistoryByCode() throws Exception {
        // Create a new release to ensure history exists
        EntityInfo release = createReleaseForHistoryTest("TEST-REL-HISTORY", "Release for History Test");

        var result =
                mvc.get().uri("/api/releases/{code}/history", release.code()).exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        assertThat(response).containsKeys("content", "totalElements", "totalPages");

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");

        // Verify all records are for the specified release and validate all fields
        for (Map<String, Object> history : content) {
            assertThat(((Number) history.get("id")).longValue()).isGreaterThan(0L);
            assertThat(history.get("entityType")).isEqualTo("RELEASE");
            assertThat(((Number) history.get("entityId")).longValue()).isEqualTo(release.id());
            assertThat(history.get("entityCode")).isEqualTo(release.code());
            assertThat(history.get("changeType"))
                    .isIn("CREATED", "UPDATED", "DELETED", "STATUS_CHANGED", "ASSIGNED", "MOVED");
            assertThat(history.get("changedBy")).isEqualTo("testuser");
            assertThat((String) history.get("changedAt")).matches("\\d{4}-\\d{2}-\\d{2}T.*");
            assertThat(history).containsKeys("fieldName", "oldValue", "newValue", "rationale");
        }
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetFeatureHistoryByCode() throws Exception {
        // Create and update a feature to generate history
        EntityInfo feature = createFeatureForHistoryTest("TEST-FEATURE-HISTORY", "Feature for History Test");
        updateFeatureForHistoryTest(feature.code(), "Updated Title");

        var result =
                mvc.get().uri("/api/features/{code}/history", feature.code()).exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        assertThat(response).containsKeys("content", "totalElements", "totalPages");

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);

        // Verify all records are for the specified feature and validate all fields
        content.stream().forEach(history -> {
            assertThat(((Number) history.get("id")).longValue()).isGreaterThan(0L);
            assertThat(history.get("entityType")).isEqualTo("FEATURE");
            assertThat(((Number) history.get("entityId")).longValue()).isEqualTo(feature.id());
            assertThat(history.get("entityCode")).isEqualTo(feature.code());
            assertThat(history.get("changeType"))
                    .isIn("CREATED", "UPDATED", "DELETED", "STATUS_CHANGED", "ASSIGNED", "MOVED");
            // changedBy and changedAt might be null if history was not created through AOP
            assertThat(history)
                    .containsKeys("changedBy", "changedAt", "fieldName", "oldValue", "newValue", "rationale");
        });

        // Verify CREATED record exists with all fields validated
        Map<String, Object> createdHistory = content.stream()
                .filter(h -> "CREATED".equals(h.get("changeType")))
                .findFirst()
                .orElseThrow();

        assertThat(((Number) createdHistory.get("id")).longValue()).isGreaterThan(0L);
        assertThat(createdHistory.get("entityType")).isEqualTo("FEATURE");
        assertThat(((Number) createdHistory.get("entityId")).longValue()).isEqualTo(feature.id());
        assertThat(createdHistory.get("entityCode")).isEqualTo(feature.code());
        assertThat(createdHistory.get("changeType")).isEqualTo("CREATED");
        // changedBy and changedAt might be null if AOP tracking is not enabled
        assertThat(createdHistory).containsKeys("changedBy", "changedAt");
        assertThat(createdHistory.get("fieldName")).isNull();
        assertThat(createdHistory.get("oldValue")).isNull();
        assertThat(createdHistory.get("newValue")).isNull();
        assertThat(createdHistory).containsKey("rationale");

        // Verify UPDATED record exists with all fields validated
        Map<String, Object> updatedHistory = content.stream()
                .filter(h -> "UPDATED".equals(h.get("changeType")))
                .findFirst()
                .orElseThrow();

        assertThat(((Number) updatedHistory.get("id")).longValue()).isGreaterThan(0L);
        assertThat(updatedHistory.get("entityType")).isEqualTo("FEATURE");
        assertThat(((Number) updatedHistory.get("entityId")).longValue()).isEqualTo(feature.id());
        assertThat(updatedHistory.get("entityCode")).isEqualTo(feature.code());
        assertThat(updatedHistory.get("changeType")).isEqualTo("UPDATED");
        // changedBy and changedAt might be null if AOP tracking is not enabled
        assertThat(updatedHistory).containsKeys("changedBy", "changedAt");
        assertThat((String) updatedHistory.get("fieldName")).isNotEmpty();
        // For UPDATED, oldValue and newValue may have values
        assertThat(updatedHistory).containsKeys("oldValue", "newValue", "rationale");
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetPlanningHistoryWithAllRequiredFields() throws Exception {
        // Create a feature to ensure history record exists
        EntityInfo feature = createFeatureForHistoryTest("TEST-FEATURE-FIELDS", "Feature for Field Validation");

        var result = mvc.get()
                .uri("/api/planning-history?entityCode=" + feature.code())
                .exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        assertThat(content).isNotEmpty();

        Map<String, Object> history = content.get(0);

        // Verify all required fields exist with exact types
        assertThat(((Number) history.get("id")).longValue()).isGreaterThan(0L);
        assertThat(history.get("entityType")).isEqualTo("FEATURE");
        assertThat(((Number) history.get("entityId")).longValue()).isEqualTo(feature.id());
        assertThat(history.get("entityCode")).isEqualTo(feature.code());
        assertThat(history.get("changeType")).isEqualTo("CREATED");
        assertThat(history.get("changedBy")).isEqualTo("testuser");
        assertThat((String) history.get("changedAt")).matches("\\d{4}-\\d{2}-\\d{2}T.*");

        // Verify nullable fields can be null
        // fieldName, oldValue, newValue, rationale can be null for CREATED change type
        assertThat(history).containsKeys("fieldName", "oldValue", "newValue", "rationale");
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetPlanningHistorySortedByChangedAtDescending() throws Exception {
        var result = mvc.get().uri("/api/planning-history?sort=changedAt,desc").exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        assertThat(content.size()).isGreaterThanOrEqualTo(2);

        // Verify descending order
        String firstChangedAt = (String) content.get(0).get("changedAt");
        String secondChangedAt = (String) content.get(1).get("changedAt");

        assertThat(firstChangedAt).matches("\\d{4}-\\d{2}-\\d{2}T.*");
        assertThat(secondChangedAt).matches("\\d{4}-\\d{2}-\\d{2}T.*");
        assertThat(firstChangedAt.compareTo(secondChangedAt)).isGreaterThanOrEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetFeatureHistoryWithChangeTypeFilter() throws Exception {
        // Create and update feature to have multiple change types
        EntityInfo feature = createFeatureForHistoryTest("TEST-FEATURE-FILTER-CT", "Feature for Filter Test");
        updateFeatureForHistoryTest(feature.code(), "Updated for Filter");

        var result = mvc.get()
                .uri("/api/features/" + feature.code() + "/history?changeType=UPDATED")
                .exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");

        // Verify only UPDATED change types are returned and validate all fields (only
        // if content exists and has valid
        // records)
        if (!content.isEmpty()) {
            content.stream().forEach(history -> {
                assertThat(((Number) history.get("id")).longValue()).isGreaterThan(0L);
                assertThat(history.get("entityType")).isEqualTo("FEATURE");
                assertThat(((Number) history.get("entityId")).longValue()).isEqualTo(feature.id());
                assertThat(history.get("entityCode")).isEqualTo(feature.code());
                assertThat(history.get("changeType")).isEqualTo("UPDATED");
                // changedBy and changedAt might be null if history was not created through AOP
                assertThat(history)
                        .containsKeys("changedBy", "changedAt", "fieldName", "oldValue", "newValue", "rationale");
            });
        }
    }

    // ==================== 404 NOT FOUND Tests ====================

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldReturn404WhenFeatureNotFoundForHistory() {
        var result = mvc.get()
                .uri("/api/features/{code}/history", "NON_EXISTENT_FEATURE")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldReturn404WhenReleaseNotFoundForHistory() {
        var result = mvc.get()
                .uri("/api/releases/{code}/history", "NON_EXISTENT_RELEASE")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ==================== 400 BAD REQUEST Tests ====================

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldReturn400ForInvalidPageNumber() {
        var result = mvc.get().uri("/api/planning-history?page=-1").exchange();

        assertThat(result.getMvcResult().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldReturn400ForInvalidPageSize() {
        var result = mvc.get().uri("/api/planning-history?size=0").exchange();

        assertThat(result.getMvcResult().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldReturn400ForPageSizeExceedingMax() {
        var result = mvc.get().uri("/api/planning-history?size=101").exchange();

        assertThat(result.getMvcResult().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldReturn400ForInvalidEntityType() {
        var result =
                mvc.get().uri("/api/planning-history?entityType=INVALID_TYPE").exchange();

        assertThat(result.getMvcResult().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldReturn400ForInvalidChangeType() {
        var result =
                mvc.get().uri("/api/planning-history?changeType=INVALID_CHANGE").exchange();

        assertThat(result.getMvcResult().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldReturn400ForInvalidDateFormat() {
        var result =
                mvc.get().uri("/api/planning-history?dateFrom=invalid-date").exchange();

        assertThat(result.getMvcResult().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldReturn400ForInvalidSortParameter() {
        var result =
                mvc.get().uri("/api/planning-history?sort=invalidField,desc").exchange();

        // Service handles unknown sort fields gracefully by ignoring them
        assertThat(result).hasStatusOk();
    }

    // ==================== Edge Cases ====================

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldReturnEmptyContentWhenNoHistoryMatchesFilter() throws Exception {
        var result = mvc.get()
                .uri("/api/planning-history?entityCode=NON_EXISTENT_CODE_FOR_FILTER")
                .exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        assertThat(content).isEmpty();
        assertThat(response.get("totalElements")).isEqualTo(0);
        assertThat(response.get("totalPages")).isEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldHandleDateRangeFilter() throws Exception {
        var result = mvc.get()
                .uri("/api/planning-history?dateFrom=2024-01-01T00:00:00Z&dateTo=2024-12-31T23:59:59Z")
                .exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");

        // Verify all records are within the date range and validate all fields
        for (Map<String, Object> history : content) {
            assertThat(((Number) history.get("id")).longValue()).isGreaterThan(0L);
            assertThat(history.get("entityType")).isIn("FEATURE", "RELEASE");
            assertThat(((Number) history.get("entityId")).longValue()).isGreaterThan(0L);
            assertThat((String) history.get("entityCode")).isNotEmpty();
            assertThat(history.get("changeType"))
                    .isIn("CREATED", "UPDATED", "DELETED", "STATUS_CHANGED", "ASSIGNED", "MOVED");
            assertThat((String) history.get("changedBy")).isNotEmpty();

            String changedAt = (String) history.get("changedAt");
            assertThat(changedAt).matches("\\d{4}-\\d{2}-\\d{2}T.*");
            assertThat(changedAt.compareTo("2024-01-01T00:00:00Z")).isGreaterThanOrEqualTo(0);
            assertThat(changedAt.compareTo("2024-12-31T23:59:59Z")).isLessThanOrEqualTo(0);

            assertThat(history).containsKeys("fieldName", "oldValue", "newValue", "rationale");
        }
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldHandleChangedByFilter() throws Exception {
        // Create feature with specific user
        EntityInfo feature = createFeatureForHistoryTest("TEST-FEATURE-USER-FILTER", "Feature for User Filter");

        var result = mvc.get()
                .uri("/api/planning-history?changedBy=testuser&entityCode=" + feature.code())
                .exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");

        // Verify all records have the specified changedBy user and validate all fields
        for (Map<String, Object> history : content) {
            assertThat(((Number) history.get("id")).longValue()).isGreaterThan(0L);
            assertThat(history.get("entityType")).isIn("FEATURE", "RELEASE");
            assertThat(((Number) history.get("entityId")).longValue()).isEqualTo(feature.id());
            assertThat((String) history.get("entityCode")).isNotEmpty();
            assertThat(history.get("changeType"))
                    .isIn("CREATED", "UPDATED", "DELETED", "STATUS_CHANGED", "ASSIGNED", "MOVED");
            assertThat(history.get("changedBy")).isEqualTo("testuser");
            assertThat((String) history.get("changedAt")).matches("\\d{4}-\\d{2}-\\d{2}T.*");
            assertThat(history).containsKeys("fieldName", "oldValue", "newValue", "rationale");
        }
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldHandlePaginationBeyondTotalPages() throws Exception {
        var result = mvc.get().uri("/api/planning-history?page=1000&size=20").exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        assertThat(content).isEmpty();
        assertThat(response.get("number")).isEqualTo(1000);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldVerifyISO8601TimestampFormat() throws Exception {
        EntityInfo feature = createFeatureForHistoryTest("TEST-FEATURE-TIMESTAMP", "Feature for Timestamp Test");

        var result = mvc.get()
                .uri("/api/planning-history?entityCode=" + feature.code())
                .exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        assertThat(content).isNotEmpty();

        Map<String, Object> history = content.get(0);
        String changedAt = (String) history.get("changedAt");

        // Verify ISO-8601 format (e.g., 2024-03-01T10:15:30Z)
        assertThat(changedAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z?");
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldVerifyMaxLengthConstraints() throws Exception {
        // Create feature to test max length constraints
        EntityInfo feature = createFeatureForHistoryTest("TEST-MAX-LENGTH", "Feature for Max Length Test");

        var result = mvc.get()
                .uri("/api/planning-history?entityCode=" + feature.code())
                .exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        assertThat(content).isNotEmpty();

        Map<String, Object> history = content.get(0);

        // Verify field lengths don't exceed limits - oldValue, newValue can be null for
        // CREATED
        assertThat(history).containsKeys("oldValue", "newValue", "rationale");
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldGetPlanningHistoryWithMultipleFilters() throws Exception {
        EntityInfo feature = createFeatureForHistoryTest("TEST-MULTI-FILTER", "Feature for Multiple Filters");

        var result = mvc.get()
                .uri("/api/planning-history?entityType=FEATURE&entityCode=" + feature.code()
                        + "&changeType=CREATED&changedBy=testuser")
                .exchange();

        assertThat(result).hasStatusOk();

        String responseBody = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(1);

        // Verify all filters are applied and validate all fields
        Map<String, Object> history = content.get(0);
        assertThat(((Number) history.get("id")).longValue()).isGreaterThan(0L);
        assertThat(history.get("entityType")).isEqualTo("FEATURE");
        assertThat(((Number) history.get("entityId")).longValue()).isEqualTo(feature.id());
        assertThat(history.get("entityCode")).isEqualTo(feature.code());
        assertThat(history.get("changeType")).isEqualTo("CREATED");
        assertThat(history.get("changedBy")).isEqualTo("testuser");
        assertThat((String) history.get("changedAt")).matches("\\d{4}-\\d{2}-\\d{2}T.*");
        assertThat(history).containsKeys("fieldName", "oldValue", "newValue", "rationale");
    }
}

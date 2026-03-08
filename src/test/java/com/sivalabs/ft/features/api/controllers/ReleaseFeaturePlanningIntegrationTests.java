package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("Release Feature Planning Integration Tests")
class ReleaseFeaturePlanningIntegrationTests extends AbstractIT {
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should preserve existing values when partial update")
    @WithMockOAuth2User(username = "testuser")
    void shouldPreserveExistingValuesWhenPartialUpdate() throws JsonMappingException, JsonProcessingException {
        var featureCode = "IDEA-8";
        // Assign and move to IN_PROGRESS with specific values
        var assignPayload = String.format(
                """
        {
        "featureCode": "%s",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "existingowner",
        "notes": "existing notes"
        }
        """,
                featureCode);
        var assignResult = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        assertThat(assignResult).hasStatus2xxSuccessful();
        var inProgressResult = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"IN_PROGRESS\"}")
                .exchange();
        assertThat(inProgressResult).hasStatus2xxSuccessful();
        // Verify initial IN_PROGRESS state via GET with exact field matching
        var getInitialResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var initialResponseBody =
                new String(getInitialResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> initialFeatures =
                objectMapper.readValue(initialResponseBody, new TypeReference<>() {});
        var initialFeature = initialFeatures.stream()
                .filter(f -> featureCode.equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(initialFeature.get("planningStatus")).isEqualTo("IN_PROGRESS");
        assertThat(initialFeature.get("featureOwner")).isEqualTo("existingowner");
        assertThat(initialFeature.get("notes")).isEqualTo("existing notes");
        // Partial update - only update notes (should preserve owner)
        var updatePayload =
                """
        {
        "planningStatus": "IN_PROGRESS",
        "notes": "updated notes only"
        }
        """;
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        // Verify the partial update (owner should be preserved, notes updated)
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(responseBody)
                .contains("IN_PROGRESS")
                .contains("existingowner") // Should be preserved
                .contains("updated notes only"); // Should be updated
    }

    @Test
    @DisplayName("Should allow same status transition with field updates")
    @WithMockOAuth2User(username = "testuser")
    void shouldAllowSameStatusTransitionWithUpdates() throws JsonMappingException, JsonProcessingException {
        var featureCode = "GO-3";
        // Assign and move to IN_PROGRESS
        var assignPayload = String.format(
                """
        {
        "featureCode": "%s",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "owner",
        "notes": "initial notes"
        }
        """,
                featureCode);
        var assignResult = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        assertThat(assignResult).hasStatus2xxSuccessful();
        var inProgressResult = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"IN_PROGRESS\"}")
                .exchange();
        assertThat(inProgressResult).hasStatus2xxSuccessful();
        // Verify initial state via GET with exact field matching
        var getInitialResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var initialResponseBody =
                new String(getInitialResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> initialFeatures =
                objectMapper.readValue(initialResponseBody, new TypeReference<>() {});
        var initialFeature = initialFeatures.stream()
                .filter(f -> featureCode.equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(initialFeature.get("planningStatus")).isEqualTo("IN_PROGRESS");
        assertThat(initialFeature.get("notes")).isEqualTo("initial notes");
        // Update with same status but different notes and date
        var updatePayload =
                """
        {
        "plannedCompletionDate": "2025-01-15",
        "planningStatus": "IN_PROGRESS",
        "notes": "updated notes and timeline"
        }
        """;
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        // Verify the update - dates, status, notes
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> features = objectMapper.readValue(responseBody, new TypeReference<>() {});
        var feature = features.stream()
                .filter(f -> featureCode.equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(feature.get("planningStatus")).isEqualTo("IN_PROGRESS");
        assertThat(feature.get("plannedCompletionDate")).isEqualTo("2025-01-15");
        assertThat(feature.get("notes")).isEqualTo("updated notes and timeline");
    }

    @Test
    @DisplayName("Should preserve planned date and blockage reason when partial update")
    @WithMockOAuth2User(username = "testuser")
    void shouldPreservePlanningFieldsWhenPartialUpdate() throws JsonMappingException, JsonProcessingException {
        var featureCode = "IDEA-6";
        var assignPayload = String.format(
                """
        {
        "featureCode": "%s",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "owner",
        "notes": "initial notes"
        }
        """,
                featureCode);
        var assignResult = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        assertThat(assignResult).hasStatus2xxSuccessful();
        var blockedPayload =
                """
        {
        "planningStatus": "BLOCKED",
        "blockageReason": "Waiting on dependency",
        "notes": "blocked notes"
        }
        """;
        var blockedResult = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(blockedPayload)
                .exchange();
        assertThat(blockedResult).hasStatus2xxSuccessful();
        var updatePayload =
                """
        {
        "planningStatus": "BLOCKED",
        "notes": "updated notes only"
        }
        """;
        var updateResult = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(updateResult).hasStatus2xxSuccessful();
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> features = objectMapper.readValue(responseBody, new TypeReference<>() {});
        var feature = features.stream()
                .filter(f -> featureCode.equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(feature.get("planningStatus")).isEqualTo("BLOCKED");
        assertThat(feature.get("plannedCompletionDate")).isEqualTo("2024-12-31");
        assertThat(feature.get("featureOwner")).isEqualTo("owner");
        assertThat(feature.get("blockageReason")).isEqualTo("Waiting on dependency");
        assertThat(feature.get("notes")).isEqualTo("updated notes only");
    }

    @Test
    @DisplayName("Should update feature planning")
    @WithMockOAuth2User(username = "user")
    void shouldUpdateFeaturePlanning() throws JsonMappingException, JsonProcessingException {
        // First assign a feature
        var assignPayload =
                """
        {
        "featureCode": "IDEA-4",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "Initial assignment"
        }
        """;
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        // Then update the planning
        var updatePayload =
                """
        {
        "plannedCompletionDate": "2024-11-30",
        "planningStatus": "IN_PROGRESS",
        "featureOwner": "jane.doe",
        "notes": "Updated planning details"
        }
        """;
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-4")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        // Fetch the feature and assert with exact field matching
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> features = objectMapper.readValue(responseBody, new TypeReference<>() {});
        var feature = features.stream()
                .filter(f -> "IDEA-4".equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(feature.get("code")).isEqualTo("IDEA-4");
        assertThat(feature.get("plannedCompletionDate")).isEqualTo("2024-11-30");
        assertThat(feature.get("planningStatus")).isEqualTo("IN_PROGRESS");
        assertThat(feature.get("featureOwner")).isEqualTo("jane.doe");
        assertThat(feature.get("notes")).isEqualTo("Updated planning details");
        assertThat(feature.get("blockageReason")).isNull();
    }

    @Test
    @DisplayName("Should allow transition from BLOCKED to IN_PROGRESS")
    @WithMockOAuth2User(username = "testuser")
    void shouldAllowTransitionFromBlockedToInProgress() throws JsonMappingException, JsonProcessingException {
        var featureCode = "IDEA-3";
        // Assign and move to BLOCKED
        var assignPayload = String.format(
                """
        {
        "featureCode": "%s",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "owner",
        "notes": "notes"
        }
        """,
                featureCode);
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        // Move to BLOCKED
        var blockedResult = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"BLOCKED\", \"blockageReason\": \"Waiting for dependencies\"}")
                .exchange();
        assertThat(blockedResult).hasStatus2xxSuccessful();
        // Verify BLOCKED status via GET with exact field matching
        var getBlockedResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var blockedResponseBody =
                new String(getBlockedResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> blockedFeatures =
                objectMapper.readValue(blockedResponseBody, new TypeReference<>() {});
        var blockedFeature = blockedFeatures.stream()
                .filter(f -> featureCode.equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(blockedFeature.get("planningStatus")).isEqualTo("BLOCKED");
        assertThat(blockedFeature.get("blockageReason")).isEqualTo("Waiting for dependencies");
        // Move from BLOCKED to IN_PROGRESS
        var updatePayload = """
        {
        "planningStatus": "IN_PROGRESS"
        }
        """;
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        // Verify status change with exact field matching
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> features = objectMapper.readValue(responseBody, new TypeReference<>() {});
        var feature = features.stream()
                .filter(f -> featureCode.equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(feature.get("planningStatus")).isEqualTo("IN_PROGRESS");
        assertThat(feature.get("blockageReason")).isNull();
    }

    @Test
    @DisplayName("Should allow transition from IN_PROGRESS to DONE")
    @WithMockOAuth2User(username = "testuser")
    void shouldAllowTransitionFromInProgressToDone() throws JsonMappingException, JsonProcessingException {
        var featureCode = "IDEA-4";
        // Assign and move to IN_PROGRESS
        var assignPayload = String.format(
                """
        {
        "featureCode": "%s",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "owner",
        "notes": "notes"
        }
        """,
                featureCode);
        var assignResult = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        assertThat(assignResult).hasStatus2xxSuccessful();
        var inProgressResult = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"IN_PROGRESS\"}")
                .exchange();
        assertThat(inProgressResult).hasStatus2xxSuccessful();
        // Verify IN_PROGRESS status via GET with exact field matching
        var getInProgressResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var inProgressResponseBody =
                new String(getInProgressResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> inProgressFeatures =
                objectMapper.readValue(inProgressResponseBody, new TypeReference<>() {});
        var inProgressFeature = inProgressFeatures.stream()
                .filter(f -> featureCode.equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(inProgressFeature.get("planningStatus")).isEqualTo("IN_PROGRESS");
        // Move from IN_PROGRESS to DONE
        var updatePayload = """
        {
        "planningStatus": "DONE"
        }
        """;
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        // Verify status change
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> features = objectMapper.readValue(responseBody, new TypeReference<>() {});
        var feature = features.stream()
                .filter(f -> featureCode.equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(feature.get("planningStatus")).isEqualTo("DONE");
    }

    @Test
    @DisplayName("Should allow transition from IN_PROGRESS to BLOCKED with blockage reason")
    @WithMockOAuth2User(username = "testuser")
    void shouldAllowTransitionFromInProgressToBlocked() throws JsonMappingException, JsonProcessingException {
        var featureCode = "IDEA-5";
        // Assign and move to IN_PROGRESS
        var assignPayload = String.format(
                """
        {
        "featureCode": "%s",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "owner",
        "notes": "notes"
        }
        """,
                featureCode);
        var assignResult = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        assertThat(assignResult).hasStatus2xxSuccessful();
        var inProgressResult = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"IN_PROGRESS\"}")
                .exchange();
        assertThat(inProgressResult).hasStatus2xxSuccessful();
        // Verify IN_PROGRESS status via GET with exact field matching
        var getInProgressResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var inProgressResponseBody =
                new String(getInProgressResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> inProgressFeatures =
                objectMapper.readValue(inProgressResponseBody, new TypeReference<>() {});
        var inProgressFeature = inProgressFeatures.stream()
                .filter(f -> featureCode.equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(inProgressFeature.get("planningStatus")).isEqualTo("IN_PROGRESS");
        // Move from IN_PROGRESS to BLOCKED
        var updatePayload =
                """
        {
        "planningStatus": "BLOCKED",
        "blockageReason": "Waiting for dependencies"
        }
        """;
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        // Verify status and blockage reason
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> features = objectMapper.readValue(responseBody, new TypeReference<>() {});
        var feature = features.stream()
                .filter(f -> featureCode.equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(feature.get("planningStatus")).isEqualTo("BLOCKED");
        assertThat(feature.get("blockageReason")).isEqualTo("Waiting for dependencies");
    }

    // Planning Status Transition Tests
    @Test
    @DisplayName("Should allow NOT_STARTED → IN_PROGRESS transition")
    @WithMockOAuth2User(username = "user")
    void shouldAllowNotStartedToInProgressTransition() throws JsonMappingException, JsonProcessingException {
        // Assign feature with NOT_STARTED status
        var assignPayload =
                """
        {
        "featureCode": "IDEA-3",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "Assignment"
        }
        """;
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        // Transition to IN_PROGRESS
        var updatePayload = """
        {
        "planningStatus": "IN_PROGRESS"
        }
        """;
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        var feature = getFeatureFromRelease("IDEA-2023.3.8", "IDEA-3");
        assertThat(feature.get("planningStatus")).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("Should allow NOT_STARTED → BLOCKED transition")
    @WithMockOAuth2User(username = "user")
    void shouldAllowNotStartedToBlockedTransition() throws JsonMappingException, JsonProcessingException {
        var assignPayload =
                """
        {
        "featureCode": "IDEA-4",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "Assignment"
        }
        """;
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        var updatePayload =
                """
        {
        "planningStatus": "BLOCKED",
        "blockageReason": "Waiting for dependencies"
        }
        """;
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-4")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        var feature = getFeatureFromRelease("IDEA-2023.3.8", "IDEA-4");
        assertThat(feature.get("planningStatus")).isEqualTo("BLOCKED");
        assertThat(feature.get("blockageReason")).isEqualTo("Waiting for dependencies");
    }

    @Test
    @DisplayName("Should allow IN_PROGRESS → DONE transition")
    @WithMockOAuth2User(username = "user")
    void shouldAllowInProgressToDoneTransition() throws JsonMappingException, JsonProcessingException {
        // Assign and set to IN_PROGRESS
        var assignPayload =
                """
        {
        "featureCode": "IDEA-5",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "Assignment"
        }
        """;
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-5")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"IN_PROGRESS\"}")
                .exchange();
        // Transition to DONE
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-5")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"DONE\"}")
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        var feature = getFeatureFromRelease("IDEA-2023.3.8", "IDEA-5");
        assertThat(feature.get("planningStatus")).isEqualTo("DONE");
    }

    @Test
    @DisplayName("Should allow IN_PROGRESS → BLOCKED transition")
    @WithMockOAuth2User(username = "user")
    void shouldAllowInProgressToBlockedTransition() throws JsonMappingException, JsonProcessingException {
        var assignPayload =
                """
        {
        "featureCode": "IDEA-6",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "Assignment"
        }
        """;
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-6")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"IN_PROGRESS\"}")
                .exchange();
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-6")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"BLOCKED\", \"blockageReason\": \"Bug discovered\"}")
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        var feature = getFeatureFromRelease("IDEA-2023.3.8", "IDEA-6");
        assertThat(feature.get("planningStatus")).isEqualTo("BLOCKED");
    }

    @Test
    @DisplayName("Should allow IN_PROGRESS → NOT_STARTED transition")
    @WithMockOAuth2User(username = "user")
    void shouldAllowInProgressToNotStartedTransition() throws JsonMappingException, JsonProcessingException {
        var assignPayload =
                """
        {
        "featureCode": "IDEA-7",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "Assignment"
        }
        """;
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"IN_PROGRESS\"}")
                .exchange();
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"NOT_STARTED\"}")
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        var feature = getFeatureFromRelease("IDEA-2023.3.8", "IDEA-7");
        assertThat(feature.get("planningStatus")).isEqualTo("NOT_STARTED");
    }

    @Test
    @DisplayName("Should allow BLOCKED → IN_PROGRESS transition")
    @WithMockOAuth2User(username = "user")
    void shouldAllowBlockedToInProgressTransition() throws JsonMappingException, JsonProcessingException {
        // First assign GO-3 to release
        var assignPayload =
                """
        {
        "featureCode": "GO-3",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "Assignment"
        }
        """;
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "GO-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"BLOCKED\", \"blockageReason\": \"Waiting\"}")
                .exchange();
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "GO-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"IN_PROGRESS\"}")
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        var feature = getFeatureFromRelease("IDEA-2023.3.8", "GO-3");
        assertThat(feature.get("planningStatus")).isEqualTo("IN_PROGRESS");
        assertThat(feature.get("blockageReason")).isNull();
    }

    @Test
    @DisplayName("Should allow BLOCKED → NOT_STARTED transition")
    @WithMockOAuth2User(username = "user")
    void shouldAllowBlockedToNotStartedTransition() throws JsonMappingException, JsonProcessingException {
        var assignPayload =
                """
        {
        "featureCode": "IDEA-8",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "Assignment"
        }
        """;
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-8")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"BLOCKED\", \"blockageReason\": \"Waiting\"}")
                .exchange();
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-8")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"NOT_STARTED\"}")
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        var feature = getFeatureFromRelease("IDEA-2023.3.8", "IDEA-8");
        assertThat(feature.get("planningStatus")).isEqualTo("NOT_STARTED");
    }

    @Test
    @DisplayName("Should allow DONE → NOT_STARTED transition")
    @WithMockOAuth2User(username = "user")
    void shouldAllowDoneToNotStartedTransition() throws JsonMappingException, JsonProcessingException {
        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"IN_PROGRESS\"}")
                .exchange();
        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"DONE\"}")
                .exchange();
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"NOT_STARTED\"}")
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        var feature = getFeatureFromRelease("IDEA-2023.3.8", "IDEA-2");
        assertThat(feature.get("planningStatus")).isEqualTo("NOT_STARTED");
    }

    @Test
    @DisplayName("Should allow DONE → IN_PROGRESS transition")
    @WithMockOAuth2User(username = "user")
    void shouldAllowDoneToInProgressTransition() throws JsonMappingException, JsonProcessingException {
        // Need a new feature for this test - let's create a release without IDEA-2023.3.8
        // Since all unassigned features are used, we'll use IDEA-1 which is already assigned
        // Actually, let's use the existing IDEA-1 and move it through states
        // First, let's get a fresh feature - for this we need to use  existing assignment pattern
        // Let's patch IDEA-1 which is already in IDEA-2023.3.8
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"IN_PROGRESS\"}")
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"DONE\"}")
                .exchange();
        var finalResult = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"IN_PROGRESS\"}")
                .exchange();
        assertThat(finalResult).hasStatus2xxSuccessful();
        var feature = getFeatureFromRelease("IDEA-2023.3.8", "IDEA-1");
        assertThat(feature.get("planningStatus")).isEqualTo("IN_PROGRESS");
    }

    // Helper method to fetch feature from release
    private Map<String, Object> getFeatureFromRelease(String releaseCode, String featureCode)
            throws JsonMappingException, JsonProcessingException {
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", releaseCode)
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> features = objectMapper.readValue(responseBody, new TypeReference<>() {});
        return features.stream()
                .filter(f -> featureCode.equals(f.get("code")))
                .findFirst()
                .orElseThrow();
    }
}

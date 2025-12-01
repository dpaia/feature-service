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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@DisplayName("Release Feature Planning Integration Tests")
class ReleaseFeaturePlanningIntegrationTests extends AbstractIT {
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should assign feature to release")
    @WithMockOAuth2User(username = "user")
    void shouldAssignFeatureToRelease() throws JsonMappingException, JsonProcessingException {
        var payload =
                """
        {
        "featureCode": "IDEA-3",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "Initial assignment"
        }
        """;
        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        // Fetch the feature and assert all planning fields with exact matches
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> features = objectMapper.readValue(responseBody, new TypeReference<>() {});
        var feature = features.stream()
                .filter(f -> "IDEA-3".equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(feature.get("code")).isEqualTo("IDEA-3");
        assertThat(feature.get("plannedCompletionDate")).isEqualTo("2024-12-31");
        assertThat(feature.get("featureOwner")).isEqualTo("john.doe");
        assertThat(feature.get("notes")).isEqualTo("Initial assignment");
        assertThat(feature.get("planningStatus")).isEqualTo("NOT_STARTED");
    }

    @Test
    @DisplayName("Should reject assign feature to release for unauthorized user")
    void shouldRejectAssignFeatureToReleaseForUnauthorizedUser() {
        var payload =
                """
        {
        "featureCode": "IDEA-2",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "jane.doe",
        "notes": "Unauthorized attempt"
        }
        """;
        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject duplicate feature assignment")
    @WithMockOAuth2User(username = "user")
    void shouldRejectDuplicateFeatureAssignment() throws JsonMappingException, JsonProcessingException {
        // First assignment should succeed
        var payload =
                """
        {
        "featureCode": "IDEA-3",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "First assignment"
        }
        """;
        var firstResult = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(firstResult).hasStatus2xxSuccessful();
        // Verify first assignment via GET with exact field matching
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> features = objectMapper.readValue(responseBody, new TypeReference<>() {});
        var feature = features.stream()
                .filter(f -> "IDEA-3".equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(feature.get("code")).isEqualTo("IDEA-3");
        assertThat(feature.get("plannedCompletionDate")).isEqualTo("2024-12-31");
        assertThat(feature.get("featureOwner")).isEqualTo("john.doe");
        assertThat(feature.get("notes")).isEqualTo("First assignment");
        assertThat(feature.get("planningStatus")).isEqualTo("NOT_STARTED");
        // Second assignment should fail
        var duplicatePayload =
                """
        {
        "featureCode": "IDEA-3",
        "plannedCompletionDate": "2025-01-15",
        "featureOwner": "jane.doe",
        "notes": "Duplicate assignment"
        }
        """;
        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicatePayload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should handle feature not found when assigning to release")
    @WithMockOAuth2User(username = "user")
    void shouldHandleFeatureNotFoundWhenAssigningToRelease() {
        var payload =
                """
        {
        "featureCode": "NON_EXISTENT_FEATURE",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "Assignment of non-existent feature"
        }
        """;
        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should handle release not found when assigning feature")
    @WithMockOAuth2User(username = "user")
    void shouldHandleReleaseNotFoundWhenAssigningFeature() {
        var payload =
                """
        {
        "featureCode": "IDEA-1",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "Assignment to non-existent release"
        }
        """;
        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "NON_EXISTENT_RELEASE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should get features assigned to release")
    void shouldGetFeaturesAssignedToRelease() throws JsonMappingException, JsonProcessingException {
        var result = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        assertThat(result.getResponse().getContentType()).isEqualTo("application/json");
        // Verify response contains specific feature data from test-data.sql including planning fields
        var responseBody = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> features = objectMapper.readValue(responseBody, new TypeReference<>() {});
        var feature1 = features.stream()
                .filter(f -> "IDEA-1".equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(feature1.get("code")).isEqualTo("IDEA-1");
        assertThat(feature1.get("title")).isEqualTo("Redesign Structure Tool Window");
        assertThat(feature1.get("plannedCompletionDate")).asString().isEqualTo("2024-03-01");
        assertThat(feature1.get("planningStatus")).isEqualTo("NOT_STARTED");
        assertThat(feature1.get("featureOwner")).isEqualTo("otheruser");
        assertThat(feature1.get("notes")).isEqualTo("Initial notes");
        var feature2 = features.stream()
                .filter(f -> "IDEA-2".equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(feature2.get("code")).isEqualTo("IDEA-2");
        assertThat(feature2.get("title")).isEqualTo("SDJ Repository Method AutoCompletion");
    }

    @Test
    @DisplayName("Should handle release not found when getting features")
    void shouldHandleReleaseNotFoundWhenGettingFeatures() {
        var result = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "NON_EXISTENT_RELEASE")
                .exchange();
        assertThat(result).hasStatus2xxSuccessful(); // Should return empty list, not 404
        assertThat(result.getResponse().getContentType()).isEqualTo("application/json");
        // Verify response is an empty array for non-existent release
        var responseBody = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(responseBody).isEqualTo("[]");
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
    @DisplayName("Should handle feature not found when updating planning")
    @WithMockOAuth2User(username = "user")
    void shouldHandleFeatureNotFoundWhenUpdatingPlanning() {
        var updatePayload = """
        {
        "planningStatus": "IN_PROGRESS"
        }
        """;
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "NON_EXISTENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should reject invalid status transition from NOT_STARTED to DONE")
    @WithMockOAuth2User(username = "user")
    void shouldRejectInvalidStatusTransition() {
        // First assign a feature
        var assignPayload =
                """
        {
        "featureCode": "IDEA-5",
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
        // Try invalid transition from NOT_STARTED to DONE
        var invalidUpdatePayload = """
        {
        "planningStatus": "DONE"
        }
        """;
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-5")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidUpdatePayload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
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

    @Test
    @DisplayName("Should validate status transition from NOT_STARTED to BLOCKED")
    @WithMockOAuth2User(username = "testuser")
    void shouldValidateStatusTransitionFromNotStartedToBlocked() throws JsonMappingException, JsonProcessingException {
        var featureCode = "IDEA-6";
        // Assign feature (defaults to NOT_STARTED)
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
        // Move from NOT_STARTED to BLOCKED
        var updatePayload =
                """
        {
        "planningStatus": "BLOCKED",
        "blockageReason": "Dependencies not ready"
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
        assertThat(feature.get("blockageReason")).isEqualTo("Dependencies not ready");
    }

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
    @DisplayName("Should move feature between releases")
    @WithMockOAuth2User(username = "user")
    void shouldMoveFeatureBetweenReleases() throws JsonMappingException, JsonProcessingException {
        // First assign a feature to a release
        var assignPayload =
                """
        {
        "featureCode": "IDEA-6",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "Initial assignment"
        }
        """;
        var assignResult = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        assertThat(assignResult).hasStatus2xxSuccessful();
        // Verify initial assignment via GET with exact field matching
        var getInitialResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var initialResponseBody =
                new String(getInitialResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> initialFeatures =
                objectMapper.readValue(initialResponseBody, new TypeReference<>() {});
        var initialFeature = initialFeatures.stream()
                .filter(f -> "IDEA-6".equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(initialFeature.get("code")).isEqualTo("IDEA-6");
        assertThat(initialFeature.get("featureOwner")).isEqualTo("john.doe");
        // Move to another release
        var movePayload =
                """
        {
        "targetReleaseCode": "RIDER-2024.2.6",
        "rationale": "Feature scope changed"
        }
        """;
        var result = mvc.post()
                .uri("/api/releases/{targetReleaseCode}/features/{featureCode}/move", "RIDER-2024.2.6", "IDEA-6")
                .contentType(MediaType.APPLICATION_JSON)
                .content(movePayload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        // Fetch the feature from the target release and verify it moved with exact field matching
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "RIDER-2024.2.6")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> features = objectMapper.readValue(responseBody, new TypeReference<>() {});
        var feature = features.stream()
                .filter(f -> "IDEA-6".equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(feature.get("code")).isEqualTo("IDEA-6");
        assertThat(feature.get("releaseCode")).isEqualTo("RIDER-2024.2.6");
        assertThat(feature.get("planningStatus")).isEqualTo("NOT_STARTED");
        assertThat(feature.get("notes")).asString().contains("Feature scope changed");
        // Verify it's no longer in the source release
        var getSourceResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var sourceResponseBody =
                new String(getSourceResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> sourceFeatures = objectMapper.readValue(sourceResponseBody, new TypeReference<>() {});
        var sourceFeature = sourceFeatures.stream()
                .filter(f -> "IDEA-6".equals(f.get("code")))
                .findFirst();
        assertThat(sourceFeature).isEmpty();
    }

    @Test
    @DisplayName("Should handle move of unassigned feature to release")
    @WithMockOAuth2User(username = "testuser")
    void shouldHandleMoveOfUnassignedFeatureToRelease() throws JsonMappingException, JsonProcessingException {
        // Use a feature that's not assigned to any release (like IDEA-1 from test data)
        var movePayload =
                """
        {
        "targetReleaseCode": "RIDER-2024.2.6",
        "rationale": "Moving unassigned feature"
        }
        """;
        var result = mvc.post()
                .uri("/api/releases/{targetReleaseCode}/features/{featureCode}/move", "RIDER-2024.2.6", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(movePayload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        // Verify the feature appears in the target release with exact field matching
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "RIDER-2024.2.6")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> features = objectMapper.readValue(responseBody, new TypeReference<>() {});
        var feature = features.stream()
                .filter(f -> "IDEA-1".equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(feature.get("code")).isEqualTo("IDEA-1");
    }

    @Test
    @DisplayName("Should handle release not found when moving feature")
    @WithMockOAuth2User(username = "user")
    void shouldHandleReleaseNotFoundWhenMovingFeature() {
        var movePayload =
                """
        {
        "targetReleaseCode": "NON_EXISTENT_RELEASE",
        "rationale": "Moving to non-existent release"
        }
        """;
        var result = mvc.post()
                .uri("/api/releases/{targetReleaseCode}/features/{featureCode}/move", "NON_EXISTENT_RELEASE", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(movePayload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should handle feature not found when moving")
    @WithMockOAuth2User(username = "user")
    void shouldHandleFeatureNotFoundWhenMoving() {
        var movePayload =
                """
        {
        "targetReleaseCode": "RIDER-2024.2.6",
        "rationale": "Moving non-existent feature"
        }
        """;
        var result = mvc.post()
                .uri(
                        "/api/releases/{targetReleaseCode}/features/{featureCode}/move",
                        "RIDER-2024.2.6",
                        "NON_EXISTENT_FEATURE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(movePayload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should remove feature from release")
    @WithMockOAuth2User(username = "user")
    void shouldRemoveFeatureFromRelease() throws JsonMappingException, JsonProcessingException {
        // First assign a feature
        // Use a unique feature code for isolation
        var featureCode = "IDEA-7";
        var assignPayload =
                """
        {
        "featureCode": "%s",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "Initial assignment"
        }
        """
                        .formatted(featureCode);
        var assignResult = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();
        assertThat(assignResult).hasStatus2xxSuccessful();
        // Verify assignment via GET
        var getAssignedResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var assignedResponseBody =
                new String(getAssignedResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> assignedFeatures =
                objectMapper.readValue(assignedResponseBody, new TypeReference<>() {});
        var assignedFeature = assignedFeatures.stream()
                .filter(f -> featureCode.equals(f.get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(assignedFeature.get("code")).isEqualTo(featureCode);
        assertThat(assignedFeature.get("featureOwner")).isEqualTo("john.doe");
        // Remove from release
        var removePayload = """
        {
        "rationale": "Feature cancelled"
        }
        """;
        var result = mvc.delete()
                .uri("/api/releases/{releaseCode}/features/{featureCode}", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(removePayload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        // Fetch the features and assert the feature is not present
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(responseBody).doesNotContain(featureCode);
    }

    @Test
    @DisplayName("Should handle removal of unassigned feature gracefully")
    @WithMockOAuth2User(username = "testuser")
    void shouldHandleRemovalOfUnassignedFeatureGracefully() {
        // Try to remove a feature that's not assigned to any release
        var removePayload = """
        {
        "rationale": "Removing unassigned feature"
        }
        """;
        var result = mvc.delete()
                .uri("/api/releases/{releaseCode}/features/{featureCode}", "IDEA-2023.3.8", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(removePayload)
                .exchange();
        // Should succeed even if feature wasn't assigned to this specific release
        assertThat(result).hasStatus2xxSuccessful();
        // Verify the feature is still unassigned (not in the release)
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(responseBody).doesNotContain("IDEA-1");
    }

    @Test
    @DisplayName("Should properly clear all planning data when removing feature")
    @WithMockOAuth2User(username = "testuser")
    void shouldProperlyClearAllPlanningDataWhenRemovingFeature() throws JsonMappingException, JsonProcessingException {
        var featureCode = "IDEA-3";
        // First assign and fully plan a feature
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
        // Set it to BLOCKED with blockage reason
        var blockedResult = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"BLOCKED\", \"blockageReason\": \"Some blockage\"}")
                .exchange();
        assertThat(blockedResult).hasStatus2xxSuccessful();
        // Verify BLOCKED state via GET with exact field matching
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
        assertThat(blockedFeature.get("blockageReason")).isEqualTo("Some blockage");
        // Now remove it
        var removePayload = """
        {
        "rationale": "Removing fully planned feature"
        }
        """;
        var result = mvc.delete()
                .uri("/api/releases/{releaseCode}/features/{featureCode}", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(removePayload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
        // Verify feature is no longer in the release
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(responseBody).doesNotContain(featureCode);
    }

    @Test
    @DisplayName("Should handle feature not found when removing")
    @WithMockOAuth2User(username = "user")
    void shouldHandleFeatureNotFoundWhenRemoving() {
        var removePayload = """
        {
        "rationale": "Removing non-existent feature"
        }
        """;
        var result = mvc.delete()
                .uri("/api/releases/{releaseCode}/features/{featureCode}", "IDEA-2023.3.8", "NON_EXISTENT_FEATURE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(removePayload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should validate required fields in assign feature payload")
    @WithMockOAuth2User(username = "user")
    void shouldValidateRequiredFieldsInAssignFeaturePayload() {
        var invalidPayload =
                """
        {
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "Missing featureCode"
        }
        """;
        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should validate date format in assign feature payload")
    @WithMockOAuth2User(username = "user")
    void shouldValidateDateFormatInAssignFeaturePayload() {
        var invalidDatePayload =
                """
        {
        "featureCode": "IDEA-6",
        "plannedCompletionDate": "invalid-date",
        "featureOwner": "john.doe",
        "notes": "Invalid date format"
        }
        """;
        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidDatePayload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should validate date format in update feature planning payload")
    @WithMockOAuth2User(username = "user")
    void shouldValidateDateFormatInUpdateFeaturePlanningPayload() {
        var invalidDatePayload =
                """
        {
        "plannedCompletionDate": "invalid-date",
        "planningStatus": "IN_PROGRESS"
        }
        """;
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidDatePayload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should validate status enum in update feature planning payload")
    @WithMockOAuth2User(username = "user")
    void shouldValidateStatusEnumInUpdateFeaturePlanningPayload() {
        var invalidStatusPayload = """
        {
        "planningStatus": "INVALID_STATUS"
        }
        """;
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidStatusPayload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should validate empty payload structure")
    @WithMockOAuth2User(username = "user")
    void shouldValidateEmptyPayloadStructure() {
        // Test assign feature with empty payload - this is different from missing required fields
        var result = mvc.post()
                .uri("/api/releases/IDEA-2023.3.8/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should filter features by planning status")
    @WithMockOAuth2User(username = "user")
    void shouldFilterFeaturesByPlanningStatus() throws JsonMappingException, JsonProcessingException {
        // Assign multiple features with different statuses
        var feature1Payload =
                """
        {
        "featureCode": "IDEA-3",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "john.doe",
        "notes": "Feature 1"
        }
        """;
        var feature2Payload =
                """
        {
        "featureCode": "IDEA-4",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "jane.doe",
        "notes": "Feature 2"
        }
        """;
        // Assign features
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feature1Payload)
                .exchange();
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feature2Payload)
                .exchange();
        // Update one feature to IN_PROGRESS
        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"IN_PROGRESS\"}")
                .exchange();
        // Filter by NOT_STARTED status
        var notStartedResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features?planningStatus=NOT_STARTED", "IDEA-2023.3.8")
                .exchange();
        assertThat(notStartedResult).hasStatus2xxSuccessful();
        var notStartedResponseBody =
                new String(notStartedResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> notStartedFeatures =
                objectMapper.readValue(notStartedResponseBody, new TypeReference<>() {});
        // Filter by status - check planning status
        assertThat(notStartedFeatures.stream().anyMatch(f -> "IDEA-4".equals(f.get("code"))))
                .isTrue();
        assertThat(notStartedFeatures.stream().anyMatch(f -> "IDEA-3".equals(f.get("code"))))
                .isFalse();
        // Filter by IN_PROGRESS status
        var inProgressResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features?planningStatus=IN_PROGRESS", "IDEA-2023.3.8")
                .exchange();
        assertThat(inProgressResult).hasStatus2xxSuccessful();
        var inProgressResponseBody =
                new String(inProgressResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> inProgressFeatures =
                objectMapper.readValue(inProgressResponseBody, new TypeReference<>() {});
        // Should contain IDEA-3 (IN_PROGRESS) but not IDEA-4 (NOT_STARTED)
        assertThat(inProgressFeatures.stream().anyMatch(f -> "IDEA-3".equals(f.get("code"))))
                .isTrue();
        assertThat(inProgressFeatures.stream().anyMatch(f -> "IDEA-4".equals(f.get("code"))))
                .isFalse();
    }

    @Test
    @DisplayName("Should filter features by owner")
    @WithMockOAuth2User(username = "user")
    void shouldFilterFeaturesByOwner() throws JsonMappingException, JsonProcessingException {
        // Assign features with different owners
        var feature1Payload =
                """
        {
        "featureCode": "IDEA-5",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "alice.smith",
        "notes": "Feature 1"
        }
        """;
        var feature2Payload =
                """
        {
        "featureCode": "IDEA-6",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "bob.jones",
        "notes": "Feature 2"
        }
        """;
        // Assign features
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feature1Payload)
                .exchange();
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feature2Payload)
                .exchange();
        // Filter by owner "alice"
        var aliceResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features?owner=alice", "IDEA-2023.3.8")
                .exchange();
        assertThat(aliceResult).hasStatus2xxSuccessful();
        var aliceResponseBody = new String(aliceResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> aliceFeatures = objectMapper.readValue(aliceResponseBody, new TypeReference<>() {});
        // Filter by owner
        assertThat(aliceFeatures.stream().anyMatch(f -> "IDEA-5".equals(f.get("code"))))
                .isTrue();
        assertThat(aliceFeatures.stream().anyMatch(f -> "IDEA-6".equals(f.get("code"))))
                .isFalse();
        // Filter by owner "bob"
        var bobResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features?owner=bob", "IDEA-2023.3.8")
                .exchange();
        assertThat(bobResult).hasStatus2xxSuccessful();
        var bobResponseBody = new String(bobResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> bobFeatures = objectMapper.readValue(bobResponseBody, new TypeReference<>() {});
        // Should contain IDEA-6 (bob.jones) but not IDEA-5 (alice.smith)
        assertThat(bobFeatures.stream().anyMatch(f -> "IDEA-6".equals(f.get("code"))))
                .isTrue();
        assertThat(bobFeatures.stream().anyMatch(f -> "IDEA-5".equals(f.get("code"))))
                .isFalse();
    }

    @Test
    @DisplayName("Should filter features by overdue status")
    @WithMockOAuth2User(username = "user")
    void shouldFilterFeaturesByOverdueStatus() throws JsonMappingException, JsonProcessingException {
        // Assign features with past and future dates
        var overdueFeaturePayload =
                """
        {
        "featureCode": "IDEA-7",
        "plannedCompletionDate": "2020-01-01",
        "featureOwner": "overdue.owner",
        "notes": "Overdue feature"
        }
        """;
        var futureFeaturePayload =
                """
        {
        "featureCode": "IDEA-8",
        "plannedCompletionDate": "2030-12-31",
        "featureOwner": "future.owner",
        "notes": "Future feature"
        }
        """;
        // Assign features
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(overdueFeaturePayload)
                .exchange();
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(futureFeaturePayload)
                .exchange();
        // Filter by overdue=true
        var overdueResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features?overdue=true", "IDEA-2023.3.8")
                .exchange();
        assertThat(overdueResult).hasStatus2xxSuccessful();
        var overdueResponseBody =
                new String(overdueResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> overdueFeatures =
                objectMapper.readValue(overdueResponseBody, new TypeReference<>() {});
        // Filter by overdue
        assertThat(overdueFeatures.stream().anyMatch(f -> "IDEA-7".equals(f.get("code"))))
                .isTrue();
        assertThat(overdueFeatures.stream().anyMatch(f -> "IDEA-8".equals(f.get("code"))))
                .isFalse();
    }

    @Test
    @DisplayName("Should filter features by blocked status")
    @WithMockOAuth2User(username = "user")
    void shouldFilterFeaturesByBlockedStatus() throws JsonMappingException, JsonProcessingException {
        // Assign features using unique codes (tests run in isolation but share database state)
        var feature1Payload =
                """
        {
        "featureCode": "GO-3",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "owner1",
        "notes": "Feature 1"
        }
        """;
        var feature2Payload =
                """
        {
        "featureCode": "IDEA-3",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "owner2",
        "notes": "Feature 2"
        }
        """;
        // Assign features
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feature1Payload)
                .exchange();
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feature2Payload)
                .exchange();
        // Block one feature
        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "GO-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"BLOCKED\", \"blockageReason\": \"Waiting for dependencies\"}")
                .exchange();
        // Filter by blocked=true
        var blockedResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features?blocked=true", "IDEA-2023.3.8")
                .exchange();
        assertThat(blockedResult).hasStatus2xxSuccessful();
        var blockedResponseBody =
                new String(blockedResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> blockedFeatures =
                objectMapper.readValue(blockedResponseBody, new TypeReference<>() {});
        // Filter by blocked
        assertThat(blockedFeatures.stream().anyMatch(f -> "GO-3".equals(f.get("code"))))
                .isTrue();
        assertThat(blockedFeatures.stream().anyMatch(f -> "IDEA-3".equals(f.get("code"))))
                .isFalse();
    }

    @Test
    @DisplayName("Should handle multiple filters combined")
    @WithMockOAuth2User(username = "user")
    void shouldHandleMultipleFiltersCombined() throws JsonMappingException, JsonProcessingException {
        // Assign features with different combinations
        var feature1Payload =
                """
        {
        "featureCode": "IDEA-4",
        "plannedCompletionDate": "2020-01-01",
        "featureOwner": "test.user",
        "notes": "Test feature 1"
        }
        """;
        var feature2Payload =
                """
        {
        "featureCode": "IDEA-5",
        "plannedCompletionDate": "2020-01-01",
        "featureOwner": "other.user",
        "notes": "Test feature 2"
        }
        """;
        // Assign features
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feature1Payload)
                .exchange();
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feature2Payload)
                .exchange();
        // Filter by overdue=true AND owner=test
        var combinedResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features?overdue=true&owner=test", "IDEA-2023.3.8")
                .exchange();
        assertThat(combinedResult).hasStatus2xxSuccessful();
        var combinedResponseBody =
                new String(combinedResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> combinedFeatures =
                objectMapper.readValue(combinedResponseBody, new TypeReference<>() {});
        // Filter by multiple criteria - overdue AND owner
        assertThat(combinedFeatures.stream().anyMatch(f -> "IDEA-4".equals(f.get("code"))))
                .isTrue();
        assertThat(combinedFeatures.stream().anyMatch(f -> "IDEA-5".equals(f.get("code"))))
                .isFalse();
    }

    @Test
    @DisplayName("Should handle invalid planningStatus filter")
    void shouldHandleInvalidPlanningStatusFilter() {
        var result = mvc.get()
                .uri("/api/releases/{releaseCode}/features?planningStatus=INVALID_STATUS", "IDEA-2023.3.8")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
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

    @Test
    @DisplayName("Should reject NOT_STARTED → DONE transition")
    @WithMockOAuth2User(username = "user")
    void shouldRejectNotStartedToDoneTransition() throws JsonMappingException, JsonProcessingException {
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
        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planningStatus\": \"DONE\"}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject BLOCKED → DONE transition")
    @WithMockOAuth2User(username = "user")
    void shouldRejectBlockedToDoneTransition() throws JsonMappingException, JsonProcessingException {
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
                .content("{\"planningStatus\": \"DONE\"}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject DONE → BLOCKED transition")
    @WithMockOAuth2User(username = "user")
    void shouldRejectDoneToBlockedTransition() throws JsonMappingException, JsonProcessingException {
        // Use IDEA-2 which is already assigned to IDEA-2023.3.8
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
                .content("{\"planningStatus\": \"BLOCKED\", \"blockageReason\": \"Should not work\"}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
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

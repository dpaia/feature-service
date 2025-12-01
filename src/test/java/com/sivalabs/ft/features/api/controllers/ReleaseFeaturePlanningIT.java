package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@DisplayName("Release Feature Planning Integration Tests")
class ReleaseFeaturePlanningIT extends AbstractIT {

    @Test
    @DisplayName("Should assign feature to release")
    @WithMockOAuth2User(username = "user")
    void shouldAssignFeatureToRelease() {
        var payload =
                """
            {
                "featureCode": "IDEA-3",
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
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

        // Fetch the feature and assert
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(responseBody).contains("IDEA-3");
    }

    @Test
    @DisplayName("Should reject assign feature to release for unauthorized user")
    void shouldRejectAssignFeatureToReleaseForUnauthorizedUser() {
        var payload =
                """
            {
                "featureCode": "IDEA-2",
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
                "featureOwner": "jane.doe",
                "notes": "Unauthorized attempt"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    @DisplayName("Should reject duplicate feature assignment")
    @WithMockOAuth2User(username = "user")
    void shouldRejectDuplicateFeatureAssignment() {
        // First assignment should succeed
        var payload =
                """
            {
                "featureCode": "IDEA-3",
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
                "featureOwner": "john.doe",
                "notes": "First assignment"
            }
            """;

        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        // Second assignment should fail
        var duplicatePayload =
                """
            {
                "featureCode": "IDEA-3",
                "plannedCompletionDate": "2025-01-15T23:59:59.999Z",
                "featureOwner": "jane.doe",
                "notes": "Duplicate assignment"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicatePayload)
                .exchange();

        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    @DisplayName("Should handle feature not found when assigning to release")
    @WithMockOAuth2User(username = "user")
    void shouldHandleFeatureNotFoundWhenAssigningToRelease() {
        var payload =
                """
            {
                "featureCode": "NON_EXISTENT_FEATURE",
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
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
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
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
    void shouldGetFeaturesAssignedToRelease() {
        var result = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();
        assertThat(result.getResponse().getContentType()).isEqualTo("application/json");

        // Verify response contains specific feature data from test-data.sql
        var responseBody = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(responseBody).contains("IDEA-1");
        assertThat(responseBody).contains("IDEA-2");
        assertThat(responseBody).contains("Redesign Structure Tool Window");
        assertThat(responseBody).contains("SDJ Repository Method AutoCompletion");
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
    void shouldUpdateFeaturePlanning() {
        // First assign a feature
        var assignPayload =
                """
            {
                "featureCode": "IDEA-4",
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
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
                "plannedCompletionDate": "2024-11-30T23:59:59.999Z",
                "status": "IN_PROGRESS",
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

        // Fetch the feature and assert
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(responseBody).contains("IN_PROGRESS");
    }

    @Test
    @DisplayName("Should handle feature not found when updating planning")
    @WithMockOAuth2User(username = "user")
    void shouldHandleFeatureNotFoundWhenUpdatingPlanning() {
        var updatePayload = """
            {
                "status": "IN_PROGRESS"
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
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
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
                "status": "DONE"
            }
            """;

        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-5")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidUpdatePayload)
                .exchange();

        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    @DisplayName("Should allow transition from BLOCKED to IN_PROGRESS")
    @WithMockOAuth2User(username = "testuser")
    void shouldAllowTransitionFromBlockedToInProgress() {
        var featureCode = "IDEA-3";

        // Assign and move to BLOCKED
        var assignPayload = String.format(
                """
            {
                "featureCode": "%s",
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
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
        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"BLOCKED\", \"blockageReason\": \"Waiting for dependencies\"}")
                .exchange();

        // Move from BLOCKED to IN_PROGRESS
        var updatePayload = """
            {
                "status": "IN_PROGRESS"
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
        assertThat(responseBody).contains("IN_PROGRESS");
        assertThat(responseBody).contains("\"blockageReason\":null");
    }

    @Test
    @DisplayName("Should allow transition from IN_PROGRESS to DONE")
    @WithMockOAuth2User(username = "testuser")
    void shouldAllowTransitionFromInProgressToDone() {
        var featureCode = "IDEA-4";

        // Assign and move to IN_PROGRESS
        var assignPayload = String.format(
                """
            {
                "featureCode": "%s",
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
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

        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"IN_PROGRESS\"}")
                .exchange();

        // Move from IN_PROGRESS to DONE
        var updatePayload = """
            {
                "status": "DONE"
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
        assertThat(responseBody).contains("DONE");
    }

    @Test
    @DisplayName("Should allow transition from IN_PROGRESS to BLOCKED with blockage reason")
    @WithMockOAuth2User(username = "testuser")
    void shouldAllowTransitionFromInProgressToBlocked() {
        var featureCode = "IDEA-5";

        // Assign and move to IN_PROGRESS
        var assignPayload = String.format(
                """
            {
                "featureCode": "%s",
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
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

        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"IN_PROGRESS\"}")
                .exchange();

        // Move from IN_PROGRESS to BLOCKED
        var updatePayload =
                """
            {
                "status": "BLOCKED",
                "blockageReason": "Waiting for dependencies"
            }
            """;

        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();

        // Verify status change and blockage reason
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();

        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(responseBody).contains("BLOCKED").contains("Waiting for dependencies");
    }

    @Test
    @DisplayName("Should validate status transition from NOT_STARTED to BLOCKED")
    @WithMockOAuth2User(username = "testuser")
    void shouldValidateStatusTransitionFromNotStartedToBlocked() {
        var featureCode = "IDEA-6";

        // Assign feature (defaults to NOT_STARTED)
        var assignPayload = String.format(
                """
            {
                "featureCode": "%s",
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
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
                "status": "BLOCKED",
                "blockageReason": "Dependencies not ready"
            }
            """;

        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();

        // Verify status change and blockage reason
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();

        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(responseBody).contains("BLOCKED").contains("Dependencies not ready");
    }

    @Test
    @DisplayName("Should preserve existing values when partial update")
    @WithMockOAuth2User(username = "testuser")
    void shouldPreserveExistingValuesWhenPartialUpdate() {
        var featureCode = "IDEA-8";

        // Assign and move to IN_PROGRESS with specific values
        var assignPayload = String.format(
                """
            {
                "featureCode": "%s",
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
                "featureOwner": "existingowner",
                "notes": "existing notes"
            }
            """,
                featureCode);

        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();

        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"IN_PROGRESS\"}")
                .exchange();

        // Partial update - only update notes (should preserve owner)
        var updatePayload =
                """
            {
                "status": "IN_PROGRESS",
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
    void shouldAllowSameStatusTransitionWithUpdates() {
        var featureCode = "GO-3";

        // Assign and move to IN_PROGRESS
        var assignPayload = String.format(
                """
            {
                "featureCode": "%s",
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
                "featureOwner": "owner",
                "notes": "initial notes"
            }
            """,
                featureCode);

        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();

        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"IN_PROGRESS\"}")
                .exchange();

        // Update with same status but different notes and date
        var updatePayload =
                """
            {
                "plannedCompletionDate": "2025-01-15T23:59:59.999Z",
                "status": "IN_PROGRESS",
                "notes": "updated notes and timeline"
            }
            """;

        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();

        // Verify the update
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();

        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(responseBody).contains("IN_PROGRESS").contains("updated notes and timeline");
    }

    @Test
    @DisplayName("Should move feature between releases")
    @WithMockOAuth2User(username = "user")
    void shouldMoveFeatureBetweenReleases() {
        // First assign a feature to a release
        var assignPayload =
                """
            {
                "featureCode": "IDEA-6",
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
                "featureOwner": "john.doe",
                "notes": "Initial assignment"
            }
            """;

        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();

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

        // Fetch the feature from the target release and assert
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "RIDER-2024.2.6")
                .exchange();
        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(responseBody).contains("RIDER-2024.2.6");
    }

    @Test
    @DisplayName("Should handle move of unassigned feature to release")
    @WithMockOAuth2User(username = "testuser")
    void shouldHandleMoveOfUnassignedFeatureToRelease() {
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

        // Verify the feature appears in the target release
        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "RIDER-2024.2.6")
                .exchange();

        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(responseBody).contains("IDEA-1");
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
    void shouldRemoveFeatureFromRelease() {
        // First assign a feature
        // Use a unique feature code for isolation
        var featureCode = "IDEA-7";
        var assignPayload =
                """
            {
            "featureCode": "%s",
            "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
            "featureOwner": "john.doe",
            "notes": "Initial assignment"
            }
            """
                        .formatted(featureCode);

        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();

        // Remove from release
        var removePayload =
                """
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
        var removePayload =
                """
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
    void shouldProperlyClearAllPlanningDataWhenRemovingFeature() {
        var featureCode = "IDEA-1";

        // First assign and fully plan a feature
        var assignPayload = String.format(
                """
            {
                "featureCode": "%s",
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
                "featureOwner": "owner",
                "notes": "initial notes"
            }
            """,
                featureCode);

        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload)
                .exchange();

        // Set it to BLOCKED with blockage reason
        mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"BLOCKED\", \"blockageReason\": \"Some blockage\"}")
                .exchange();

        // Now remove it
        var removePayload =
                """
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
        assertThat(responseBody).contains("\"blockageReason\":null");
    }

    @Test
    @DisplayName("Should handle feature not found when removing")
    @WithMockOAuth2User(username = "user")
    void shouldHandleFeatureNotFoundWhenRemoving() {
        var removePayload =
                """
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
                "plannedCompletionDate": "2024-12-31T23:59:59.999Z",
                "featureOwner": "john.doe",
                "notes": "Missing featureCode"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload)
                .exchange();

        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    @DisplayName("Should validate date format in assign feature payload")
    @WithMockOAuth2User(username = "user")
    void shouldValidateDateFormatInAssignFeaturePayload() {
        var invalidDatePayload =
                """
            {
                "featureCode": "IDEA-8",
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

        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    @DisplayName("Should validate date format in update feature planning payload")
    @WithMockOAuth2User(username = "user")
    void shouldValidateDateFormatInUpdateFeaturePlanningPayload() {
        var invalidDatePayload =
                """
            {
                "plannedCompletionDate": "invalid-date",
                "status": "IN_PROGRESS"
            }
            """;

        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidDatePayload)
                .exchange();

        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    @DisplayName("Should validate status enum in update feature planning payload")
    @WithMockOAuth2User(username = "user")
    void shouldValidateStatusEnumInUpdateFeaturePlanningPayload() {
        var invalidStatusPayload =
                """
            {
                "status": "INVALID_STATUS"
            }
            """;

        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidStatusPayload)
                .exchange();

        assertThat(result).hasStatus4xxClientError();
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

        assertThat(result).hasStatus4xxClientError();
    }
}

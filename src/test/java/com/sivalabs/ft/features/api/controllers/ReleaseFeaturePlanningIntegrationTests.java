package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@DisplayName("Release Feature Planning Integration Tests")
class ReleaseFeaturePlanningIntegrationTests extends AbstractIT {

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
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Bad Request");
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
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Bad Request");
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
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Bad Request");
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
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Bad Request");
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
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Bad Request");
    }

    @Test
    @DisplayName("Should handle invalid planningStatus filter")
    void shouldHandleInvalidPlanningStatusFilter() {
        var result = mvc.get()
                .uri("/api/releases/{releaseCode}/features?planningStatus=INVALID_STATUS", "IDEA-2023.3.8")
                .exchange();
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Bad Request");
    }

    @Test
    @DisplayName("Should reject invalid status transition from NOT_STARTED to DONE")
    @WithMockOAuth2User(username = "user")
    void shouldRejectInvalidStatusTransition() {
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
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Bad Request");
    }

    @Test
    @DisplayName("Should reject NOT_STARTED → DONE transition")
    @WithMockOAuth2User(username = "user")
    void shouldRejectNotStartedToDoneTransition() {
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
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Bad Request");
    }

    @Test
    @DisplayName("Should reject BLOCKED → DONE transition")
    @WithMockOAuth2User(username = "user")
    void shouldRejectBlockedToDoneTransition() {
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
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Bad Request");
    }

    @Test
    @DisplayName("Should reject DONE → BLOCKED transition")
    @WithMockOAuth2User(username = "user")
    void shouldRejectDoneToBlockedTransition() {
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
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Bad Request");
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
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Resource not found");
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
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Resource not found");
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
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Resource not found");
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
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Resource not found");
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
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Resource not found");
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
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Resource not found");
    }
}

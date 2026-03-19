package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class RoleBasedAccessControlTests extends AbstractIT {

    // Release endpoints tests

    @Test
    @WithMockOAuth2User(
            username = "product_manager",
            roles = {"PRODUCT_MANAGER"})
    void productManagerCanCreateRelease() {
        var payload =
                """
            {
                "productCode": "intellij",
                "code": "IDEA-2025.2",
                "description": "IntelliJ IDEA 2025.2"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void adminCanCreateRelease() {
        var payload =
                """
            {
                "productCode": "intellij",
                "code": "IDEA-2025.3",
                "description": "IntelliJ IDEA 2025.3"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @WithMockOAuth2User(
            username = "regular_user",
            roles = {"USER"})
    void regularUserCannotCreateRelease() {
        var payload =
                """
            {
                "productCode": "intellij",
                "code": "IDEA-2025.4",
                "description": "IntelliJ IDEA 2025.4"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockOAuth2User(
            username = "product_manager",
            roles = {"PRODUCT_MANAGER"})
    void productManagerCanUpdateRelease() {
        var payload =
                """
            {
                "description": "Updated by Product Manager",
                "status": "RELEASED"
            }
            """;

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(
            username = "regular_user",
            roles = {"USER"})
    void regularUserCannotUpdateRelease() {
        var payload =
                """
            {
                "description": "Updated by Regular User",
                "status": "RELEASED"
            }
            """;

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockOAuth2User(
            username = "product_manager",
            roles = {"PRODUCT_MANAGER"})
    void productManagerCannotDeleteRelease() {
        var result = mvc.delete().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void adminCanDeleteRelease() {
        var result = mvc.delete().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(
            username = "regular_user",
            roles = {"USER"})
    void regularUserCannotDeleteRelease() {
        var result = mvc.delete().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    // Feature assignment tests

    @Test
    @WithMockOAuth2User(
            username = "product_manager",
            roles = {"PRODUCT_MANAGER"})
    void productManagerCanAssignFeatureToRelease() {
        var payload =
                """
            {
                "featureCode": "IDEA-3",
                "plannedCompletionDate": "2024-12-31T23:59:59Z",
                "featureOwner": "john.doe",
                "notes": "High priority feature"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @WithMockOAuth2User(
            username = "regular_user",
            roles = {"USER"})
    void regularUserCannotAssignFeatureToRelease() {
        var payload =
                """
            {
                "featureCode": "IDEA-4",
                "plannedCompletionDate": "2024-12-31T23:59:59Z",
                "featureOwner": "john.doe",
                "notes": "High priority feature"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockOAuth2User(
            username = "product_manager",
            roles = {"PRODUCT_MANAGER"})
    void productManagerCanUpdateFeaturePlanning() {
        var payload =
                """
            {
                "plannedCompletionDate": "2024-12-31T23:59:59Z",
                "planningStatus": "IN_PROGRESS",
                "featureOwner": "jane.doe",
                "notes": "Updated planning"
            }
            """;

        var result = mvc.patch()
                .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(
            username = "product_manager",
            roles = {"PRODUCT_MANAGER"})
    void productManagerCanMoveFeature() {
        var payload =
                """
            {
                "rationale": "Moving to different release for better planning"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases/{targetReleaseCode}/features/{featureCode}/move", "IDEA-2024.2.3", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(
            username = "product_manager",
            roles = {"PRODUCT_MANAGER"})
    void productManagerCannotRemoveFeatureFromRelease() {
        var payload = """
            {
                "rationale": "No longer needed"
            }
            """;

        var result = mvc.delete()
                .uri("/api/releases/{releaseCode}/features/{featureCode}", "IDEA-2023.3.8", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void adminCanRemoveFeatureFromRelease() {
        var payload = """
            {
                "rationale": "No longer needed"
            }
            """;

        var result = mvc.delete()
                .uri("/api/releases/{releaseCode}/features/{featureCode}", "IDEA-2023.3.8", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();
    }

    // Milestone endpoints tests

    @Test
    @WithMockOAuth2User(
            username = "product_manager",
            roles = {"PRODUCT_MANAGER"})
    void productManagerCanCreateMilestone() {
        var payload =
                """
            {
                "productCode": "intellij",
                "code": "M-2025.1",
                "name": "Release 2025.1",
                "description": "Major release milestone",
                "targetDate": "2025-03-31T23:59:59Z",
                "status": "PLANNED",
                "owner": "product.manager",
                "notes": "Important milestone"
            }
            """;

        var result = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @WithMockOAuth2User(
            username = "regular_user",
            roles = {"USER"})
    void regularUserCannotCreateMilestone() {
        var payload =
                """
            {
                "productCode": "intellij",
                "code": "M-2025.2",
                "name": "Release 2025.2",
                "description": "Major release milestone",
                "targetDate": "2025-06-30T23:59:59Z",
                "status": "PLANNED",
                "owner": "product.manager",
                "notes": "Important milestone"
            }
            """;

        var result = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockOAuth2User(
            username = "product_manager",
            roles = {"PRODUCT_MANAGER"})
    void productManagerCanUpdateMilestone() {
        var payload =
                """
            {
                "name": "Updated Milestone",
                "description": "Updated description",
                "targetDate": "2025-04-30T23:59:59Z",
                "status": "IN_PROGRESS",
                "owner": "new.owner",
                "notes": "Updated notes"
            }
            """;

        var result = mvc.put()
                .uri("/api/milestones/{code}", "Q1-2024")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(
            username = "regular_user",
            roles = {"USER"})
    void regularUserCannotUpdateMilestone() {
        var payload =
                """
            {
                "name": "Updated Milestone",
                "description": "Updated description",
                "targetDate": "2025-04-30T23:59:59Z",
                "status": "IN_PROGRESS",
                "owner": "new.owner",
                "notes": "Updated notes"
            }
            """;

        var result = mvc.put()
                .uri("/api/milestones/{code}", "Q2-2024")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockOAuth2User(
            username = "product_manager",
            roles = {"PRODUCT_MANAGER"})
    void productManagerCannotDeleteMilestone() {
        var result = mvc.delete().uri("/api/milestones/{code}", "Q2-2024").exchange();
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void adminCanDeleteMilestone() {
        var result = mvc.delete().uri("/api/milestones/{code}", "Q3-2024").exchange();
        assertThat(result).hasStatusOk();
    }

    // Public access tests (GET endpoints should remain public)

    @Test
    void unauthenticatedUserCanReadReleases() {
        var result =
                mvc.get().uri("/api/releases?productCode={code}", "intellij").exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    void unauthenticatedUserCanReadMilestones() {
        var result =
                mvc.get().uri("/api/milestones?productCode={code}", "intellij").exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    void unauthenticatedUserCannotCreateRelease() {
        var payload =
                """
            {
                "productCode": "intellij",
                "code": "IDEA-2025.5",
                "description": "IntelliJ IDEA 2025.5"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unauthenticatedUserCannotCreateMilestone() {
        var payload =
                """
            {
                "productCode": "intellij",
                "code": "M-2025.3",
                "name": "Release 2025.3",
                "description": "Major release milestone",
                "targetDate": "2025-09-30T23:59:59Z",
                "status": "PLANNED",
                "owner": "product.manager",
                "notes": "Important milestone"
            }
            """;

        var result = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}

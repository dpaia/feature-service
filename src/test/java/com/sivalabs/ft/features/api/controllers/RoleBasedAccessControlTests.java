package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.io.UnsupportedEncodingException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Black-box integration tests for Role-Based Access Control (RBAC) on Releases and Milestones APIs.
 */
class RoleBasedAccessControlTests extends AbstractIT {

    private static final String UPDATE_RELEASE_PAYLOAD =
            """
                    {
                        "description": "Updated description",
                        "status": "DRAFT"
                    }
                    """;

    private static final String UPDATE_MILESTONE_PAYLOAD =
            """
                    {
                        "name": "Updated Milestone",
                        "description": "Updated description",
                        "targetDate": "2024-12-31T23:59:59Z",
                        "status": "IN_PROGRESS"
                    }
                    """;

    private static final String MOVE_FEATURE_PAYLOAD =
            """
                    {
                        "targetReleaseCode": "IDEA-2024.2.3",
                        "rationale": "Moving for better fit"
                    }
                    """;

    @Nested
    class PublicGetEndpoints {

        @Test
        void shouldAllowUnauthenticatedAccessToGetReleasesByProductCode() {
            var result = mvc.get()
                    .uri("/api/releases?productCode={code}", "intellij")
                    .exchange();
            assertThat(result)
                    .as("Unauthenticated user should be able to GET releases by product code")
                    .hasStatusOk();
        }

        @Test
        void shouldAllowUnauthenticatedAccessToGetReleaseByCode() {
            var result = mvc.get().uri("/api/releases/{code}", "IDEA-2023.3.8").exchange();
            assertThat(result)
                    .as("Unauthenticated user should be able to GET a release by code")
                    .hasStatusOk();
        }

        @Test
        void shouldAllowUnauthenticatedAccessToGetReleaseFeatures() {
            var result = mvc.get()
                    .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                    .exchange();
            assertThat(result)
                    .as("Unauthenticated user should be able to GET features of a release")
                    .hasStatusOk();
        }

        @Test
        void shouldAllowUnauthenticatedAccessToGetMilestonesByProductCode() {
            var result = mvc.get()
                    .uri("/api/milestones?productCode={code}", "intellij")
                    .exchange();
            assertThat(result)
                    .as("Unauthenticated user should be able to GET milestones by product code")
                    .hasStatusOk();
        }

        @Test
        void shouldAllowUnauthenticatedAccessToGetMilestoneByCode() {
            var result = mvc.get().uri("/api/milestones/{code}", "Q1-2024").exchange();
            assertThat(result)
                    .as("Unauthenticated user should be able to GET a milestone by code")
                    .hasStatusOk();
        }

        @Test
        @WithMockOAuth2User(
                username = "pm",
                roles = {"PRODUCT_MANAGER"})
        void shouldAllowProductManagerToGetReleases() {
            var result = mvc.get()
                    .uri("/api/releases?productCode={code}", "intellij")
                    .exchange();
            assertThat(result)
                    .as("PRODUCT_MANAGER should be able to GET releases")
                    .hasStatusOk();
        }

        @Test
        @WithMockOAuth2User(
                username = "admin",
                roles = {"ADMIN"})
        void shouldAllowAdminToGetMilestones() {
            var result = mvc.get()
                    .uri("/api/milestones?productCode={code}", "intellij")
                    .exchange();
            assertThat(result).as("ADMIN should be able to GET milestones").hasStatusOk();
        }
    }

    @Nested
    class CreateRelease {

        @Test
        void shouldReturn401WhenUnauthenticatedUserCreatesRelease() {
            var result = mvc.post()
                    .uri("/api/releases")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "intellij",
                                        "code": "RBAC-UNAUTH-REL",
                                        "description": "Should be rejected"
                                    }
                                    """)
                    .exchange();
            assertThat(result)
                    .as("Unauthenticated user should receive 401 when creating a release")
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @WithMockOAuth2User(username = "plain_user")
        void shouldReturn403WhenUserRoleCreatesRelease() {
            var result = mvc.post()
                    .uri("/api/releases")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "intellij",
                                        "code": "RBAC-USER-REL",
                                        "description": "Should be forbidden"
                                    }
                                    """)
                    .exchange();
            assertThat(result)
                    .as("USER role should receive 403 when creating a release")
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockOAuth2User(username = "plain_user")
        void shouldReturn403WithPermissionMessageInBody() throws UnsupportedEncodingException {
            var result = mvc.post()
                    .uri("/api/releases")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "intellij",
                                        "code": "RBAC-MSG-REL",
                                        "description": "Should be forbidden"
                                    }
                                    """)
                    .exchange();
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            String body = result.getMvcResult().getResponse().getContentAsString();
            assertThat(body)
                    .as("403 response body should contain the expected error message")
                    .contains("Insufficient permissions");
        }

        @Test
        @WithMockOAuth2User(
                username = "pm",
                roles = {"PRODUCT_MANAGER"})
        void shouldAllowProductManagerToCreateRelease() {
            var result = mvc.post()
                    .uri("/api/releases")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "intellij",
                                        "code": "PM-NEW-REL",
                                        "description": "Release created by PRODUCT_MANAGER"
                                    }
                                    """)
                    .exchange();
            assertThat(result)
                    .as("PRODUCT_MANAGER should be able to create a release")
                    .hasStatus(HttpStatus.CREATED);
        }

        @Test
        @WithMockOAuth2User(
                username = "admin",
                roles = {"ADMIN"})
        void shouldAllowAdminToCreateRelease() {
            var result = mvc.post()
                    .uri("/api/releases")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "intellij",
                                        "code": "ADMIN-NEW-REL",
                                        "description": "Release created by ADMIN"
                                    }
                                    """)
                    .exchange();
            assertThat(result).as("ADMIN should be able to create a release").hasStatus(HttpStatus.CREATED);
        }
    }

    @Nested
    class UpdateRelease {

        @Test
        void shouldReturn401WhenUnauthenticatedUserUpdatesRelease() {
            var result = mvc.put()
                    .uri("/api/releases/{code}", "IDEA-2023.3.8")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(UPDATE_RELEASE_PAYLOAD)
                    .exchange();
            assertThat(result)
                    .as("Unauthenticated user should receive 401 when updating a release")
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @WithMockOAuth2User(username = "plain_user")
        void shouldReturn403WhenUserRoleUpdatesRelease() {
            var result = mvc.put()
                    .uri("/api/releases/{code}", "IDEA-2023.3.8")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(UPDATE_RELEASE_PAYLOAD)
                    .exchange();
            assertThat(result)
                    .as("USER role should receive 403 when updating a release")
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockOAuth2User(
                username = "pm",
                roles = {"PRODUCT_MANAGER"})
        void shouldAllowProductManagerToUpdateRelease() {
            var result = mvc.put()
                    .uri("/api/releases/{code}", "IDEA-2023.3.8")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(UPDATE_RELEASE_PAYLOAD)
                    .exchange();
            assertThat(result)
                    .as("PRODUCT_MANAGER should be able to update a release")
                    .hasStatusOk();
        }

        @Test
        @WithMockOAuth2User(
                username = "admin",
                roles = {"ADMIN"})
        void shouldAllowAdminToUpdateRelease() {
            var result = mvc.put()
                    .uri("/api/releases/{code}", "IDEA-2023.3.8")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(UPDATE_RELEASE_PAYLOAD)
                    .exchange();
            assertThat(result).as("ADMIN should be able to update a release").hasStatusOk();
        }
    }

    @Nested
    class DeleteRelease {

        @Test
        void shouldReturn401WhenUnauthenticatedUserDeletesRelease() {
            var result =
                    mvc.delete().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
            assertThat(result)
                    .as("Unauthenticated user should receive 401 when deleting a release")
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @WithMockOAuth2User(username = "plain_user")
        void shouldReturn403WhenUserRoleDeletesRelease() {
            var result =
                    mvc.delete().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
            assertThat(result)
                    .as("USER role should receive 403 when deleting a release")
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockOAuth2User(
                username = "pm",
                roles = {"PRODUCT_MANAGER"})
        void shouldReturn403WhenProductManagerDeletesRelease() {
            var result =
                    mvc.delete().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
            assertThat(result)
                    .as("PRODUCT_MANAGER should receive 403 when attempting to delete a release")
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockOAuth2User(
                username = "pm",
                roles = {"PRODUCT_MANAGER"})
        void shouldReturn403WithPermissionMessageWhenProductManagerDeletesRelease()
                throws UnsupportedEncodingException {
            var result =
                    mvc.delete().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            String body = result.getMvcResult().getResponse().getContentAsString();
            assertThat(body)
                    .as("403 response body should contain the expected error message")
                    .contains("Insufficient permissions");
        }

        @Test
        @WithMockOAuth2User(
                username = "admin",
                roles = {"ADMIN"})
        void shouldAllowAdminToDeleteRelease() {
            var result =
                    mvc.delete().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
            assertThat(result).as("ADMIN should be able to delete a release").hasStatusOk();
        }
    }

    @Nested
    class AssignFeatureToRelease {

        @Test
        void shouldReturn401WhenUnauthenticatedUserAssignsFeature() {
            var result = mvc.post()
                    .uri("/api/releases/{releaseCode}/features", "IDEA-2024.2.3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "featureCode": "IDEA-4",
                                        "plannedCompletionDate": "2024-06-30",
                                        "featureOwner": "owner@example.com"
                                    }
                                    """)
                    .exchange();
            assertThat(result)
                    .as("Unauthenticated user should receive 401 when assigning a feature")
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @WithMockOAuth2User(username = "plain_user")
        void shouldReturn403WhenUserRoleAssignsFeature() {
            var result = mvc.post()
                    .uri("/api/releases/{releaseCode}/features", "IDEA-2024.2.3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "featureCode": "IDEA-4",
                                        "plannedCompletionDate": "2024-06-30",
                                        "featureOwner": "owner@example.com"
                                    }
                                    """)
                    .exchange();
            assertThat(result)
                    .as("USER role should receive 403 when assigning a feature to a release")
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockOAuth2User(
                username = "pm",
                roles = {"PRODUCT_MANAGER"})
        void shouldAllowProductManagerToAssignFeature() {
            var result = mvc.post()
                    .uri("/api/releases/{releaseCode}/features", "IDEA-2024.2.3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "featureCode": "IDEA-4",
                                        "plannedCompletionDate": "2024-06-30",
                                        "featureOwner": "owner@example.com"
                                    }
                                    """)
                    .exchange();
            assertThat(result)
                    .as("PRODUCT_MANAGER should be able to assign a feature to a release")
                    .hasStatus(HttpStatus.CREATED);
        }

        @Test
        @WithMockOAuth2User(
                username = "admin",
                roles = {"ADMIN"})
        void shouldAllowAdminToAssignFeature() {
            var result = mvc.post()
                    .uri("/api/releases/{releaseCode}/features", "IDEA-2024.2.3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "featureCode": "IDEA-5",
                                        "plannedCompletionDate": "2024-07-31",
                                        "featureOwner": "owner@example.com"
                                    }
                                    """)
                    .exchange();
            assertThat(result)
                    .as("ADMIN should be able to assign a feature to a release")
                    .hasStatus(HttpStatus.CREATED);
        }
    }

    @Nested
    class UpdateFeaturePlanning {

        private static final String PLANNING_PAYLOAD =
                """
                        {
                            "planningStatus": "IN_PROGRESS",
                            "featureOwner": "owner@example.com"
                        }
                        """;

        @Test
        void shouldReturn401WhenUnauthenticatedUserUpdatesFeaturePlanning() {
            var result = mvc.patch()
                    .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(PLANNING_PAYLOAD)
                    .exchange();
            assertThat(result)
                    .as("Unauthenticated user should receive 401 when updating feature planning")
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @WithMockOAuth2User(username = "plain_user")
        void shouldReturn403WhenUserRoleUpdatesFeaturePlanning() {
            var result = mvc.patch()
                    .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(PLANNING_PAYLOAD)
                    .exchange();
            assertThat(result)
                    .as("USER role should receive 403 when updating feature planning")
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockOAuth2User(
                username = "pm",
                roles = {"PRODUCT_MANAGER"})
        void shouldAllowProductManagerToUpdateFeaturePlanning() {
            var result = mvc.patch()
                    .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(PLANNING_PAYLOAD)
                    .exchange();
            assertThat(result)
                    .as("PRODUCT_MANAGER should be able to update feature planning")
                    .hasStatusOk();
        }

        @Test
        @WithMockOAuth2User(
                username = "admin",
                roles = {"ADMIN"})
        void shouldAllowAdminToUpdateFeaturePlanning() {
            var result = mvc.patch()
                    .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(PLANNING_PAYLOAD)
                    .exchange();
            assertThat(result)
                    .as("ADMIN should be able to update feature planning")
                    .hasStatusOk();
        }
    }

    @Nested
    class MoveFeatureBetweenReleases {

        @Test
        void shouldReturn401WhenUnauthenticatedUserMovesFeature() {
            var result = mvc.post()
                    .uri("/api/releases/{targetReleaseCode}/features/{featureCode}/move", "IDEA-2024.2.3", "IDEA-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(MOVE_FEATURE_PAYLOAD)
                    .exchange();
            assertThat(result)
                    .as("Unauthenticated user should receive 401 when moving a feature")
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @WithMockOAuth2User(username = "plain_user")
        void shouldReturn403WhenUserRoleMovesFeature() {
            var result = mvc.post()
                    .uri("/api/releases/{targetReleaseCode}/features/{featureCode}/move", "IDEA-2024.2.3", "IDEA-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(MOVE_FEATURE_PAYLOAD)
                    .exchange();
            assertThat(result)
                    .as("USER role should receive 403 when moving a feature")
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockOAuth2User(
                username = "pm",
                roles = {"PRODUCT_MANAGER"})
        void shouldAllowProductManagerToMoveFeature() {
            var result = mvc.post()
                    .uri("/api/releases/{targetReleaseCode}/features/{featureCode}/move", "IDEA-2024.2.3", "IDEA-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(MOVE_FEATURE_PAYLOAD)
                    .exchange();
            assertThat(result)
                    .as("PRODUCT_MANAGER should be able to move a feature between releases")
                    .hasStatusOk();
        }

        @Test
        @WithMockOAuth2User(
                username = "admin",
                roles = {"ADMIN"})
        void shouldAllowAdminToMoveFeature() {
            var result = mvc.post()
                    .uri("/api/releases/{targetReleaseCode}/features/{featureCode}/move", "IDEA-2024.2.3", "IDEA-2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(MOVE_FEATURE_PAYLOAD)
                    .exchange();
            assertThat(result)
                    .as("ADMIN should be able to move a feature between releases")
                    .hasStatusOk();
        }
    }

    @Nested
    class RemoveFeatureFromRelease {

        @Test
        void shouldReturn401WhenUnauthenticatedUserRemovesFeature() {
            var result = mvc.delete()
                    .uri("/api/releases/{releaseCode}/features/{featureCode}", "IDEA-2023.3.8", "IDEA-1")
                    .exchange();
            assertThat(result)
                    .as("Unauthenticated user should receive 401 when removing a feature from a release")
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @WithMockOAuth2User(username = "plain_user")
        void shouldReturn403WhenUserRoleRemovesFeature() {
            var result = mvc.delete()
                    .uri("/api/releases/{releaseCode}/features/{featureCode}", "IDEA-2023.3.8", "IDEA-1")
                    .exchange();
            assertThat(result)
                    .as("USER role should receive 403 when removing a feature from a release")
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockOAuth2User(
                username = "pm",
                roles = {"PRODUCT_MANAGER"})
        void shouldReturn403WhenProductManagerRemovesFeature() {
            var result = mvc.delete()
                    .uri("/api/releases/{releaseCode}/features/{featureCode}", "IDEA-2023.3.8", "IDEA-1")
                    .exchange();
            assertThat(result)
                    .as("PRODUCT_MANAGER should receive 403 when attempting to remove a feature from a release")
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockOAuth2User(
                username = "admin",
                roles = {"ADMIN"})
        void shouldAllowAdminToRemoveFeatureFromRelease() {
            var result = mvc.delete()
                    .uri("/api/releases/{releaseCode}/features/{featureCode}", "IDEA-2023.3.8", "IDEA-1")
                    .exchange();
            assertThat(result)
                    .as("ADMIN should be able to remove a feature from a release")
                    .hasStatusOk();
        }
    }

    @Nested
    class CreateMilestone {

        @Test
        void shouldReturn401WhenUnauthenticatedUserCreatesMilestone() {
            var result = mvc.post()
                    .uri("/api/milestones")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "intellij",
                                        "code": "RBAC-UNAUTH-MS",
                                        "name": "Should be rejected",
                                        "targetDate": "2024-12-31T23:59:59Z",
                                        "status": "PLANNED"
                                    }
                                    """)
                    .exchange();
            assertThat(result)
                    .as("Unauthenticated user should receive 401 when creating a milestone")
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @WithMockOAuth2User(username = "plain_user")
        void shouldReturn403WhenUserRoleCreatesMilestone() {
            var result = mvc.post()
                    .uri("/api/milestones")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "intellij",
                                        "code": "RBAC-USER-MS",
                                        "name": "Should be forbidden",
                                        "targetDate": "2024-12-31T23:59:59Z",
                                        "status": "PLANNED"
                                    }
                                    """)
                    .exchange();
            assertThat(result)
                    .as("USER role should receive 403 when creating a milestone")
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockOAuth2User(username = "plain_user")
        void shouldReturn403WithPermissionMessageWhenUserRoleCreatesMilestone() throws UnsupportedEncodingException {
            var result = mvc.post()
                    .uri("/api/milestones")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "intellij",
                                        "code": "RBAC-MSG-MS",
                                        "name": "Permission message test",
                                        "targetDate": "2024-12-31T23:59:59Z",
                                        "status": "PLANNED"
                                    }
                                    """)
                    .exchange();
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            String body = result.getMvcResult().getResponse().getContentAsString();
            assertThat(body)
                    .as("403 response body should contain the expected error message")
                    .contains("Insufficient permissions");
        }

        @Test
        @WithMockOAuth2User(
                username = "pm",
                roles = {"PRODUCT_MANAGER"})
        void shouldAllowProductManagerToCreateMilestone() {
            var result = mvc.post()
                    .uri("/api/milestones")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "intellij",
                                        "code": "PM-NEW-MS",
                                        "name": "PM Milestone",
                                        "description": "Created by PRODUCT_MANAGER",
                                        "targetDate": "2024-12-31T23:59:59Z",
                                        "status": "PLANNED"
                                    }
                                    """)
                    .exchange();
            assertThat(result)
                    .as("PRODUCT_MANAGER should be able to create a milestone")
                    .hasStatus(HttpStatus.CREATED);
        }

        @Test
        @WithMockOAuth2User(
                username = "admin",
                roles = {"ADMIN"})
        void shouldAllowAdminToCreateMilestone() {
            var result = mvc.post()
                    .uri("/api/milestones")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "intellij",
                                        "code": "ADMIN-NEW-MS",
                                        "name": "Admin Milestone",
                                        "description": "Created by ADMIN",
                                        "targetDate": "2024-12-31T23:59:59Z",
                                        "status": "PLANNED"
                                    }
                                    """)
                    .exchange();
            assertThat(result).as("ADMIN should be able to create a milestone").hasStatus(HttpStatus.CREATED);
        }
    }

    @Nested
    class UpdateMilestone {

        @Test
        void shouldReturn401WhenUnauthenticatedUserUpdatesMilestone() {
            var result = mvc.put()
                    .uri("/api/milestones/{code}", "Q2-2024")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(UPDATE_MILESTONE_PAYLOAD)
                    .exchange();
            assertThat(result)
                    .as("Unauthenticated user should receive 401 when updating a milestone")
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @WithMockOAuth2User(username = "plain_user")
        void shouldReturn403WhenUserRoleUpdatesMilestone() {
            var result = mvc.put()
                    .uri("/api/milestones/{code}", "Q2-2024")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(UPDATE_MILESTONE_PAYLOAD)
                    .exchange();
            assertThat(result)
                    .as("USER role should receive 403 when updating a milestone")
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockOAuth2User(
                username = "pm",
                roles = {"PRODUCT_MANAGER"})
        void shouldAllowProductManagerToUpdateMilestone() {
            var result = mvc.put()
                    .uri("/api/milestones/{code}", "Q2-2024")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(UPDATE_MILESTONE_PAYLOAD)
                    .exchange();
            assertThat(result)
                    .as("PRODUCT_MANAGER should be able to update a milestone")
                    .hasStatusOk();
        }

        @Test
        @WithMockOAuth2User(
                username = "admin",
                roles = {"ADMIN"})
        void shouldAllowAdminToUpdateMilestone() {
            var result = mvc.put()
                    .uri("/api/milestones/{code}", "Q2-2024")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(UPDATE_MILESTONE_PAYLOAD)
                    .exchange();
            assertThat(result).as("ADMIN should be able to update a milestone").hasStatusOk();
        }
    }

    @Nested
    class DeleteMilestone {

        @Test
        void shouldReturn401WhenUnauthenticatedUserDeletesMilestone() {
            var result = mvc.delete().uri("/api/milestones/{code}", "Q1-2024").exchange();
            assertThat(result)
                    .as("Unauthenticated user should receive 401 when deleting a milestone")
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @WithMockOAuth2User(username = "plain_user")
        void shouldReturn403WhenUserRoleDeletesMilestone() {
            var result = mvc.delete().uri("/api/milestones/{code}", "Q1-2024").exchange();
            assertThat(result)
                    .as("USER role should receive 403 when deleting a milestone")
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockOAuth2User(
                username = "pm",
                roles = {"PRODUCT_MANAGER"})
        void shouldReturn403WhenProductManagerDeletesMilestone() {
            var result = mvc.delete().uri("/api/milestones/{code}", "Q1-2024").exchange();
            assertThat(result)
                    .as("PRODUCT_MANAGER should receive 403 when attempting to delete a milestone")
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockOAuth2User(
                username = "pm",
                roles = {"PRODUCT_MANAGER"})
        void shouldReturn403WithPermissionMessageWhenProductManagerDeletesMilestone()
                throws UnsupportedEncodingException {
            var result = mvc.delete().uri("/api/milestones/{code}", "Q1-2024").exchange();
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            String body = result.getMvcResult().getResponse().getContentAsString();
            assertThat(body)
                    .as("403 response body should contain the expected error message")
                    .contains("Insufficient permissions");
        }

        @Test
        @WithMockOAuth2User(
                username = "admin",
                roles = {"ADMIN"})
        void shouldAllowAdminToDeleteMilestone() {
            var result = mvc.delete().uri("/api/milestones/{code}", "Q1-2024").exchange();
            assertThat(result).as("ADMIN should be able to delete a milestone").hasStatusOk();
        }
    }

    @Nested
    class FeatureCrudAuthOnly {

        @Test
        void shouldAllowUnauthenticatedUserToGetFeatures() {
            var result = mvc.get()
                    .uri("/api/features?productCode={code}", "intellij")
                    .exchange();
            assertThat(result)
                    .as("Unauthenticated user should be able to GET features")
                    .hasStatusOk();
        }

        @Test
        void shouldReturn401WhenUnauthenticatedUserCreatesFeature() {
            var result = mvc.post()
                    .uri("/api/features")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "intellij",
                                        "title": "New Feature",
                                        "description": "Feature description"
                                    }
                                    """)
                    .exchange();
            assertThat(result)
                    .as("Unauthenticated user should receive 401 when creating a feature")
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @WithMockOAuth2User(username = "plain_user")
        void shouldAllowUserRoleToCreateFeature() {
            var result = mvc.post()
                    .uri("/api/features")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "intellij",
                                        "title": "Feature by plain user",
                                        "description": "No role check expected"
                                    }
                                    """)
                    .exchange();
            assertThat(result)
                    .as("USER role should be able to create a feature (auth-only endpoint)")
                    .hasStatus(HttpStatus.CREATED);
        }

        @Test
        void shouldReturn401WhenUnauthenticatedUserDeletesFeature() {
            var result = mvc.delete().uri("/api/features/{code}", "IDEA-1").exchange();
            assertThat(result)
                    .as("Unauthenticated user should receive 401 when deleting a feature")
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @WithMockOAuth2User(username = "plain_user")
        void shouldAllowUserRoleToDeleteFeature() {
            var result = mvc.delete().uri("/api/features/{code}", "IDEA-9").exchange();
            assertThat(result)
                    .as("USER role should be able to delete a feature")
                    .hasStatusOk();
        }
    }

    @Nested
    class ProductManagerWorkflow {

        @Test
        @WithMockOAuth2User(
                username = "pm",
                roles = {"PRODUCT_MANAGER"})
        void productManagerCanCreateAndUpdateReleaseButNotDelete() {
            var createResult = mvc.post()
                    .uri("/api/releases")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "goland",
                                        "code": "PM-FLOW-REL",
                                        "description": "PM workflow release"
                                    }
                                    """)
                    .exchange();
            assertThat(createResult).as("PRODUCT_MANAGER: create release").hasStatus(HttpStatus.CREATED);

            var updateResult = mvc.put()
                    .uri("/api/releases/{code}", "GO-PM-FLOW-REL")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(UPDATE_RELEASE_PAYLOAD)
                    .exchange();
            assertThat(updateResult).as("PRODUCT_MANAGER: update release").hasStatusOk();

            var deleteResult =
                    mvc.delete().uri("/api/releases/{code}", "GO-PM-FLOW-REL").exchange();
            assertThat(deleteResult)
                    .as("PRODUCT_MANAGER: delete release should be forbidden")
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockOAuth2User(
                username = "pm",
                roles = {"PRODUCT_MANAGER"})
        void productManagerCanCreateAndUpdateMilestoneButNotDelete() {
            var createResult = mvc.post()
                    .uri("/api/milestones")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "goland",
                                        "code": "PM-FLOW-MS",
                                        "name": "PM Workflow Milestone",
                                        "description": "PM workflow milestone",
                                        "targetDate": "2024-12-31T23:59:59Z",
                                        "status": "PLANNED"
                                    }
                                    """)
                    .exchange();
            assertThat(createResult).as("PRODUCT_MANAGER: create milestone").hasStatus(HttpStatus.CREATED);

            var updateResult = mvc.put()
                    .uri("/api/milestones/{code}", "PM-FLOW-MS")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(UPDATE_MILESTONE_PAYLOAD)
                    .exchange();
            assertThat(updateResult).as("PRODUCT_MANAGER: update milestone").hasStatusOk();

            var deleteResult =
                    mvc.delete().uri("/api/milestones/{code}", "PM-FLOW-MS").exchange();
            assertThat(deleteResult)
                    .as("PRODUCT_MANAGER: delete milestone should be forbidden")
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class AdminWorkflow {

        @Test
        @WithMockOAuth2User(
                username = "admin",
                roles = {"ADMIN"})
        void adminCanCreateUpdateAndDeleteRelease() {
            var createResult = mvc.post()
                    .uri("/api/releases")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "pycharm",
                                        "code": "ADMIN-FLOW-REL",
                                        "description": "Admin workflow release"
                                    }
                                    """)
                    .exchange();
            assertThat(createResult).as("ADMIN: create release").hasStatus(HttpStatus.CREATED);

            var updateResult = mvc.put()
                    .uri("/api/releases/{code}", "PY-ADMIN-FLOW-REL")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(UPDATE_RELEASE_PAYLOAD)
                    .exchange();
            assertThat(updateResult).as("ADMIN: update release").hasStatusOk();

            var deleteResult = mvc.delete()
                    .uri("/api/releases/{code}", "PY-ADMIN-FLOW-REL")
                    .exchange();
            assertThat(deleteResult).as("ADMIN: delete release").hasStatusOk();
        }

        @Test
        @WithMockOAuth2User(
                username = "admin",
                roles = {"ADMIN"})
        void adminCanCreateUpdateAndDeleteMilestone() {
            var createResult = mvc.post()
                    .uri("/api/milestones")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                                    {
                                        "productCode": "pycharm",
                                        "code": "ADMIN-FLOW-MS",
                                        "name": "Admin Workflow Milestone",
                                        "description": "Admin workflow milestone",
                                        "targetDate": "2024-12-31T23:59:59Z",
                                        "status": "PLANNED"
                                    }
                                    """)
                    .exchange();
            assertThat(createResult).as("ADMIN: create milestone").hasStatus(HttpStatus.CREATED);

            var updateResult = mvc.put()
                    .uri("/api/milestones/{code}", "ADMIN-FLOW-MS")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(UPDATE_MILESTONE_PAYLOAD)
                    .exchange();
            assertThat(updateResult).as("ADMIN: update milestone").hasStatusOk();

            var deleteResult =
                    mvc.delete().uri("/api/milestones/{code}", "ADMIN-FLOW-MS").exchange();
            assertThat(deleteResult).as("ADMIN: delete milestone").hasStatusOk();
        }
    }
}

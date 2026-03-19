package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class MilestoneControllerTests extends AbstractIT {
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockOAuth2User
    void shouldGetMilestonesByProductCode() throws Exception {
        var result =
                mvc.get().uri("/api/milestones?productCode={code}", "intellij").exchange();

        assertThat(result).as("Should return milestones for intellij product").hasStatusOk();

        String json = result.getMvcResult().getResponse().getContentAsString();
        List<Map<String, Object>> milestones = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(milestones.size())
                .as("Should have exactly 2 milestones for intellij")
                .isEqualTo(2);
    }

    @Test
    @WithMockOAuth2User
    void shouldReturn400WhenProductCodeMissing() {
        var result = mvc.get().uri("/api/milestones").exchange();
        assertThat(result).as("Should return 400 when productCode is missing").hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User
    void shouldFilterMilestonesByStatus() {
        var result = mvc.get()
                .uri("/api/milestones?productCode={code}&status={status}", "intellij", "COMPLETED")
                .exchange();
        assertThat(result)
                .as("Should filter milestones by COMPLETED status")
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].status")
                .asArray()
                .allSatisfy(status -> assertThat(status)
                        .as("All returned milestones should have COMPLETED status")
                        .isEqualTo("COMPLETED"));
    }

    @Test
    @WithMockOAuth2User
    void shouldFilterMilestonesByOwner() {
        var result = mvc.get()
                .uri("/api/milestones?productCode={code}&owner={owner}", "intellij", "alice@example.com")
                .exchange();
        assertThat(result)
                .as("Should filter milestones by owner")
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].owner")
                .asArray()
                .allSatisfy(owner -> assertThat(owner)
                        .as("All returned milestones should be owned by alice@example.com")
                        .isEqualTo("alice@example.com"));
    }

    @Test
    @WithMockOAuth2User
    void shouldGetMilestoneByCode() throws Exception {
        var result = mvc.get().uri("/api/milestones/{code}", "Q1-2024").exchange();

        assertThat(result).as("Should return milestone by code").hasStatusOk();

        String json = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> milestone = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(milestone.get("code")).isEqualTo("Q1-2024");
        assertThat(milestone.get("name")).isEqualTo("Q1 2024 Release");
        assertThat(milestone.get("productCode")).isEqualTo("intellij");
        assertThat(milestone.get("status")).isEqualTo("COMPLETED");
        assertThat(milestone.get("owner")).isEqualTo("alice@example.com");
        assertThat(milestone.get("progress")).isEqualTo(100);
    }

    @Test
    @WithMockOAuth2User
    void shouldReturn404WhenMilestoneNotFound() {
        var result =
                mvc.get().uri("/api/milestones/{code}", "NON_EXISTENT_CODE").exchange();
        assertThat(result).as("Should return 404 for non-existent milestone").hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldCreateNewMilestone() {
        var payload =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "Q4-2024",
                                    "name": "Q4 2024 Release",
                                    "description": "Fourth quarter objectives and deliverables",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED",
                                    "owner": "frank@example.com",
                                    "notes": "Year-end release"
                                }
                                """;

        var result = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).as("Should create new milestone with 201 Created").hasStatus(HttpStatus.CREATED);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldUpdateMilestone() {
        var payload =
                """
                                {
                                    "name": "Updated Q2 2024 Release",
                                    "description": "Updated description for Q2",
                                    "targetDate": "2024-06-30T23:59:59Z",
                                    "actualDate": "2024-06-25T10:30:00Z",
                                    "status": "COMPLETED",
                                    "owner": "updated.bob@example.com",
                                    "notes": "Completed early"
                                }
                                """;

        var result = mvc.put()
                .uri("/api/milestones/{code}", "Q2-2024")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).as("Should update milestone successfully").hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"ADMIN"})
    void shouldDeleteMilestone() {
        var createPayload =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "DELETE-TEST",
                                    "name": "To Delete",
                                    "description": "Test milestone to delete",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();

        var result = mvc.delete().uri("/api/milestones/{code}", "DELETE-TEST").exchange();
        assertThat(result).as("Should delete milestone successfully").hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldReturn400WhenCreatingDuplicateMilestoneCode() {
        var payload =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "Q1-2024",
                                    "name": "Duplicate Q1",
                                    "description": "Should fail",
                                    "targetDate": "2024-03-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        var result = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).as("Should return 400 for duplicate milestone code").hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldReturn400WhenCreatingWithInvalidProduct() {
        var payload =
                """
                                {
                                    "productCode": "INVALID_PRODUCT",
                                    "code": "M1",
                                    "name": "Milestone",
                                    "description": "Description",
                                    "targetDate": "2024-03-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        var result = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result.getMvcResult().getResponse().getStatus())
                .as("Should return 400 or 404 for invalid product code")
                .isIn(HttpStatus.BAD_REQUEST.value(), HttpStatus.NOT_FOUND.value());
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldAssociateReleaseWithMilestone() {
        var payload =
                """
                                {
                                    "description": "Release associated with Q2 milestone",
                                    "status": "RELEASED",
                                    "releasedAt": "2024-06-15T10:00:00Z",
                                    "milestoneCode": "Q2-2024"
                                }
                                """;

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-2024.2.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).as("Should associate release with milestone").hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"ADMIN"})
    void shouldOrphanReleasesWhenMilestoneDeleted() throws Exception {
        var releaseBefore =
                mvc.get().uri("/api/releases/{code}", "IDEA-2023.3.8").exchange();
        assertThat(releaseBefore).hasStatusOk();

        String jsonBefore = releaseBefore.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> releaseDataBefore = objectMapper.readValue(jsonBefore, new TypeReference<>() {});
        assertThat(releaseDataBefore.get("milestoneCode"))
                .as("Release should be linked to Q1-2024 before deletion")
                .isEqualTo("Q1-2024");

        var deleteResult = mvc.delete().uri("/api/milestones/{code}", "Q1-2024").exchange();
        assertThat(deleteResult).hasStatusOk();

        var releaseAfter =
                mvc.get().uri("/api/releases/{code}", "IDEA-2023.3.8").exchange();
        assertThat(releaseAfter).hasStatusOk();

        String jsonAfter = releaseAfter.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> releaseDataAfter = objectMapper.readValue(jsonAfter, new TypeReference<>() {});
        assertThat(releaseDataAfter.get("milestoneCode"))
                .as("Release should be orphaned (milestoneCode null) after milestone deletion")
                .isNull();
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldRejectCrossProductMilestoneAssociation() {
        var milestonePayload =
                """
                                {
                                    "productCode": "vscode",
                                    "code": "VS-2024",
                                    "name": "VS Code 2024",
                                    "description": "VS Code milestone",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(milestonePayload)
                .exchange();

        var updatePayload =
                """
                                {
                                    "description": "Updated release",
                                    "status": "RELEASED",
                                    "milestoneCode": "Q3-2024"
                                }
                                """;

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();

        assertThat(result)
                .as("Should reject linking intellij release to goland milestone")
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User
    void shouldCalculateProgressBasedOnReleasedCount() throws Exception {
        var result1 = mvc.get().uri("/api/milestones/{code}", "Q2-2024").exchange();
        assertThat(result1).hasStatusOk();

        String json1 = result1.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> milestone1 = objectMapper.readValue(json1, new TypeReference<>() {});

        assertThat(milestone1.get("progress"))
                .as("Progress should be 100% when all releases are RELEASED (1/1)")
                .isEqualTo(100);

        var result2 = mvc.get().uri("/api/milestones/{code}", "Q1-2024").exchange();
        assertThat(result2).hasStatusOk();

        String json2 = result2.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> milestone2 = objectMapper.readValue(json2, new TypeReference<>() {});

        assertThat(milestone2.get("progress"))
                .as("Progress should be 100% when the only release is RELEASED")
                .isEqualTo(100);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldCalculateProgressWithMixedReleaseStatuses() throws Exception {
        var createMilestone =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "PROGRESS-TEST",
                                    "name": "Progress Test Milestone",
                                    "description": "Testing progress calculation",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMilestone)
                .exchange();

        var resultEmpty =
                mvc.get().uri("/api/milestones/{code}", "PROGRESS-TEST").exchange();
        assertThat(resultEmpty).hasStatusOk();

        String jsonEmpty = resultEmpty.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> milestoneEmpty = objectMapper.readValue(jsonEmpty, new TypeReference<>() {});

        assertThat(milestoneEmpty.get("progress"))
                .as("Progress should be 0% when milestone has no releases")
                .isEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldCalculateProgressWithVariousReleaseStatuses() throws Exception {
        var createMilestone =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "CALC-TEST",
                                    "name": "Calculation Test",
                                    "description": "Testing progress calculation",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;
        mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMilestone)
                .exchange();

        var result0 = mvc.get().uri("/api/milestones/{code}", "CALC-TEST").exchange();
        String json0 = result0.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> milestone0 = objectMapper.readValue(json0, new TypeReference<>() {});
        assertThat(milestone0.get("progress"))
                .as("Initial progress should be 0%")
                .isEqualTo(0);

        var release1 =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "REL-1",
                                    "description": "Release 1"
                                }
                                """;
        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(release1)
                .exchange();

        var updateRelease1 =
                """
                                {
                                    "description": "Release 1 - DRAFT",
                                    "status": "DRAFT",
                                    "milestoneCode": "CALC-TEST"
                                }
                                """;
        mvc.put()
                .uri("/api/releases/{code}", "IDEA-REL-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRelease1)
                .exchange();

        var result1 = mvc.get().uri("/api/milestones/{code}", "CALC-TEST").exchange();
        String json1 = result1.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> milestone1 = objectMapper.readValue(json1, new TypeReference<>() {});

        assertThat(milestone1.get("progress"))
                .as("Progress should be 0% with 1 DRAFT release (0/1)")
                .isEqualTo(0);

        var release2 =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "REL-2",
                                    "description": "Release 2"
                                }
                                """;
        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(release2)
                .exchange();

        var updateRelease2 =
                """
                                {
                                    "description": "Release 2 - DRAFT",
                                    "status": "DRAFT",
                                    "milestoneCode": "CALC-TEST"
                                }
                                """;
        mvc.put()
                .uri("/api/releases/{code}", "IDEA-REL-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRelease2)
                .exchange();

        var result2 = mvc.get().uri("/api/milestones/{code}", "CALC-TEST").exchange();
        String json2 = result2.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> milestone2 = objectMapper.readValue(json2, new TypeReference<>() {});

        assertThat(milestone2.get("progress"))
                .as("Progress should be 0% with 2 DRAFT releases (0/2)")
                .isEqualTo(0);

        var release3 =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "REL-3",
                                    "description": "Release 3"
                                }
                                """;
        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(release3)
                .exchange();

        var updateRelease3 =
                """
                                {
                                    "description": "Release 3 - RELEASED",
                                    "status": "RELEASED",
                                    "milestoneCode": "CALC-TEST"
                                }
                                """;
        mvc.put()
                .uri("/api/releases/{code}", "IDEA-REL-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRelease3)
                .exchange();

        var result3 = mvc.get().uri("/api/milestones/{code}", "CALC-TEST").exchange();
        String json3 = result3.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> milestone3 = objectMapper.readValue(json3, new TypeReference<>() {});

        assertThat(milestone3.get("progress"))
                .as("Progress should be 33% with 1 RELEASED out of 3 (1/3)")
                .isEqualTo(33);

        var release4 =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "REL-4",
                                    "description": "Release 4"
                                }
                                """;
        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(release4)
                .exchange();

        var updateRelease4 =
                """
                                {
                                    "description": "Release 4 - RELEASED",
                                    "status": "RELEASED",
                                    "milestoneCode": "CALC-TEST"
                                }
                                """;
        mvc.put()
                .uri("/api/releases/{code}", "IDEA-REL-4")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRelease4)
                .exchange();

        var result4 = mvc.get().uri("/api/milestones/{code}", "CALC-TEST").exchange();
        String json4 = result4.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> milestone4 = objectMapper.readValue(json4, new TypeReference<>() {});

        assertThat(milestone4.get("progress"))
                .as("Progress should be 50% with 2 RELEASED out of 4 (2/4)")
                .isEqualTo(50);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldValidateSameProductForMilestoneReleaseAssociation() {
        var milestonePayload =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "TEST-MILESTONE",
                                    "name": "Test Milestone",
                                    "description": "Test milestone for validation",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(milestonePayload)
                .exchange();

        var correctPayload =
                """
                                {
                                    "description": "Associate with correct milestone",
                                    "status": "DRAFT",
                                    "milestoneCode": "TEST-MILESTONE"
                                }
                                """;

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(correctPayload)
                .exchange();

        assertThat(result).as("Should allow association when products match").hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldEnforceGlobalUniquenessOfMilestoneCode() {
        var milestone1 =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "GLOBAL-UNIQUE-TEST",
                                    "name": "Test Milestone 1",
                                    "description": "First milestone",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        var result1 = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(milestone1)
                .exchange();
        assertThat(result1).hasStatus(HttpStatus.CREATED);

        var milestone2 =
                """
                                {
                                    "productCode": "goland",
                                    "code": "GLOBAL-UNIQUE-TEST",
                                    "name": "Test Milestone 2",
                                    "description": "Second milestone",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        var result2 = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(milestone2)
                .exchange();

        assertThat(result2)
                .as("Should reject duplicate milestone code across different products")
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User
    void shouldReturnEmptyListWhenNoMilestonesExistForProduct()
            throws UnsupportedEncodingException, JsonProcessingException {
        var result =
                mvc.get().uri("/api/milestones?productCode={code}", "pycharm").exchange();

        assertThat(result).hasStatusOk();
        String responseBody = result.getResponse().getContentAsString();
        List<?> response = objectMapper.readValue(responseBody, new TypeReference<>() {});
        assertThat(response).isEmpty();
    }

    @Test
    @WithMockOAuth2User
    void shouldIncludeAllRequiredFieldsInListResponse() throws Exception {
        var result =
                mvc.get().uri("/api/milestones?productCode={code}", "intellij").exchange();

        assertThat(result).hasStatusOk();

        String json = result.getMvcResult().getResponse().getContentAsString();
        List<Map<String, Object>> milestones = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(milestones).isNotEmpty();

        Map<String, Object> milestone = milestones.getFirst();
        assertThat(milestone)
                .containsKeys(
                        "id",
                        "code",
                        "name",
                        "description",
                        "targetDate",
                        "status",
                        "productCode",
                        "progress",
                        "createdBy",
                        "createdAt");
        assertThat(milestone).doesNotContainKey("releases");
    }

    @Test
    @WithMockOAuth2User
    void shouldIncludeReleasesArrayInDetailResponse() throws Exception {
        var result = mvc.get().uri("/api/milestones/{code}", "Q1-2024").exchange();

        assertThat(result).hasStatusOk();

        String json = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> milestone = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(milestone).containsKey("releases");
        assertThat(milestone.get("releases")).isInstanceOf(List.class);
    }

    @Test
    void shouldReturn401WhenCreatingMilestoneWithoutAuthentication() {
        var payload =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "AUTH-TEST",
                                    "name": "Auth Test Milestone",
                                    "description": "Testing authentication",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        var result = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result)
                .as("Should return 401 when creating milestone without authentication")
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401WhenUpdatingMilestoneWithoutAuthentication() {
        var payload =
                """
                                {
                                    "name": "Updated Name",
                                    "description": "Updated description",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        var result = mvc.put()
                .uri("/api/milestones/{code}", "Q1-2024")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result)
                .as("Should return 401 when updating milestone without authentication")
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401WhenDeletingMilestoneWithoutAuthentication() {
        var result = mvc.delete().uri("/api/milestones/{code}", "Q1-2024").exchange();

        assertThat(result)
                .as("Should return 401 when deleting milestone without authentication")
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAllowGettingMilestonesWithoutAuthentication() {
        var result =
                mvc.get().uri("/api/milestones?productCode={code}", "intellij").exchange();

        assertThat(result)
                .as("Should allow listing milestones without authentication (public endpoint)")
                .hasStatusOk();
    }

    @Test
    void shouldAllowGettingMilestoneByCodeWithoutAuthentication() {
        var result = mvc.get().uri("/api/milestones/{code}", "Q1-2024").exchange();

        assertThat(result)
                .as("Should allow getting milestone by code without authentication (public endpoint)")
                .hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldReturn400WhenCreatingMilestoneWithMissingCode() {
        var payload =
                """
                                {
                                    "productCode": "intellij",
                                    "name": "Missing Code Test",
                                    "description": "Code field is missing",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        var result = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result)
                .as("Should return 400 when milestone code is missing")
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldReturn400WhenCreatingMilestoneWithMissingName() {
        var payload =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "MISSING-NAME",
                                    "description": "Name field is missing",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        var result = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result)
                .as("Should return 400 when milestone name is missing")
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldReturn400WhenCreatingMilestoneWithMissingTargetDate() {
        var payload =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "MISSING-DATE",
                                    "name": "Missing Date Test",
                                    "description": "TargetDate field is missing",
                                    "status": "PLANNED"
                                }
                                """;

        var result = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result)
                .as("Should return 400 when milestone targetDate is missing")
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldReturn400WhenCreatingMilestoneWithMissingStatus() {
        var payload =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "MISSING-STATUS",
                                    "name": "Missing Status Test",
                                    "description": "Status field is missing",
                                    "targetDate": "2024-12-31T23:59:59Z"
                                }
                                """;

        var result = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result)
                .as("Should return 400 when milestone status is missing")
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldReturn400WhenCreatingMilestoneWithMissingProductCode() {
        var payload =
                """
                                {
                                    "code": "MISSING-PRODUCT",
                                    "name": "Missing Product Test",
                                    "description": "ProductCode field is missing",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        var result = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).as("Should return 400 when productCode is missing").hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldReturn400WhenCreatingMilestoneWithInvalidStatus() {
        var payload =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "INVALID-STATUS",
                                    "name": "Invalid Status Test",
                                    "description": "Testing invalid status enum value",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "INVALID_STATUS_VALUE"
                                }
                                """;

        var result = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result)
                .as("Should return 400 when status has invalid enum value")
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldReturn404WhenUpdatingNonExistentMilestone() {
        var payload =
                """
                                {
                                    "name": "Updated Name",
                                    "description": "Updated description",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        var result = mvc.put()
                .uri("/api/milestones/{code}", "NON-EXISTENT-MILESTONE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result)
                .as("Should return 404 when updating non-existent milestone")
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"ADMIN"})
    void shouldReturn404WhenDeletingNonExistentMilestone() {
        var result = mvc.delete()
                .uri("/api/milestones/{code}", "NON-EXISTENT-MILESTONE")
                .exchange();

        assertThat(result)
                .as("Should return 404 when deleting non-existent milestone")
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldCalculateZeroProgressWhenAllReleasesAreDraft() throws Exception {
        var createMilestone =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "ZERO-PROGRESS",
                                    "name": "Zero Progress Test",
                                    "description": "All releases are DRAFT",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMilestone)
                .exchange();

        var release1 =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "DRAFT-REL-1",
                                    "description": "Draft Release 1"
                                }
                                """;
        mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(release1)
                .exchange();

        var updateRelease1 =
                """
                                {
                                    "description": "Draft Release 1",
                                    "status": "DRAFT",
                                    "milestoneCode": "ZERO-PROGRESS"
                                }
                                """;
        mvc.put()
                .uri("/api/releases/{code}", "IDEA-DRAFT-REL-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRelease1)
                .exchange();

        var result = mvc.get().uri("/api/milestones/{code}", "ZERO-PROGRESS").exchange();
        assertThat(result).hasStatusOk();

        String json = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> milestone = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(milestone.get("progress"))
                .as("Progress should be 0% when no releases are RELEASED (0/1)")
                .isEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldHandleProgressRoundingCorrectly() throws Exception {
        var createMilestone =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "ROUNDING-TEST",
                                    "name": "Rounding Test",
                                    "description": "Test progress rounding to integer",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMilestone)
                .exchange();

        for (int i = 1; i <= 3; i++) {
            var release = String.format(
                    """
                                                        {
                                                            "productCode": "intellij",
                                                            "code": "ROUND-REL-%d",
                                                            "description": "Release %d"
                                                        }
                                                        """,
                    i, i);
            mvc.post()
                    .uri("/api/releases")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(release)
                    .exchange();

            String status = (i == 1) ? "RELEASED" : "DRAFT";
            var updateRelease = String.format(
                    """
                                                        {
                                                            "description": "Release %d",
                                                            "status": "%s",
                                                            "milestoneCode": "ROUNDING-TEST"
                                                        }
                                                        """,
                    i, status);
            mvc.put()
                    .uri("/api/releases/{code}", "IDEA-ROUND-REL-" + i)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateRelease)
                    .exchange();
        }

        var result = mvc.get().uri("/api/milestones/{code}", "ROUNDING-TEST").exchange();
        assertThat(result).hasStatusOk();

        String json = result.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> milestone = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(milestone.get("progress"))
                .as("Progress should be 33% as integer (1/3 = 33.33% rounded)")
                .isEqualTo(33);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldAcceptMilestoneWithAllOptionalFieldsOmitted() {
        var payload =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "MIN-FIELDS",
                                    "name": "Minimal Fields Test",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        var result = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result)
                .as("Should accept milestone with only required fields")
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldAcceptISO8601DateFormatWithTimezone() throws Exception {
        var payload =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "ISO-DATE-TEST",
                                    "name": "ISO Date Format Test",
                                    "description": "Testing ISO-8601 date format",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        var createResult = mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(createResult).hasStatus(HttpStatus.CREATED);

        var getResult = mvc.get().uri("/api/milestones/{code}", "ISO-DATE-TEST").exchange();
        assertThat(getResult).hasStatusOk();

        String json = getResult.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> milestone = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(milestone.get("targetDate"))
                .as("TargetDate should be returned in ISO-8601 format")
                .asString()
                .matches(".*T.*Z");
    }

    @Test
    @WithMockOAuth2User(
            username = "user",
            roles = {"PRODUCT_MANAGER"})
    void shouldPopulateAuditFieldsOnCreateAndUpdate() throws Exception {
        var createPayload =
                """
                                {
                                    "productCode": "intellij",
                                    "code": "AUDIT-TEST",
                                    "name": "Audit Fields Test",
                                    "description": "Testing audit fields",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "PLANNED"
                                }
                                """;

        mvc.post()
                .uri("/api/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();

        var getOriginal = mvc.get().uri("/api/milestones/{code}", "AUDIT-TEST").exchange();
        assertThat(getOriginal).hasStatusOk();

        String jsonOriginal = getOriginal.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> milestoneOriginal = objectMapper.readValue(jsonOriginal, new TypeReference<>() {});

        assertThat(milestoneOriginal.get("createdBy"))
                .as("CreatedBy should be populated on create")
                .isNotNull();

        assertThat(milestoneOriginal.get("createdAt"))
                .as("CreatedAt should be populated on create")
                .isNotNull();

        String originalCreatedBy = (String) milestoneOriginal.get("createdBy");
        String originalCreatedAt = (String) milestoneOriginal.get("createdAt");

        var updatePayload =
                """
                                {
                                    "name": "Updated Audit Test",
                                    "description": "Updated description",
                                    "targetDate": "2024-12-31T23:59:59Z",
                                    "status": "IN_PROGRESS"
                                }
                                """;

        mvc.put()
                .uri("/api/milestones/{code}", "AUDIT-TEST")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();

        var getUpdated = mvc.get().uri("/api/milestones/{code}", "AUDIT-TEST").exchange();
        String jsonUpdated = getUpdated.getMvcResult().getResponse().getContentAsString();
        Map<String, Object> milestoneUpdated = objectMapper.readValue(jsonUpdated, new TypeReference<>() {});

        assertThat(milestoneUpdated.get("createdBy"))
                .as("CreatedBy should remain unchanged after update")
                .isEqualTo(originalCreatedBy);

        assertThat(milestoneUpdated.get("createdAt"))
                .as("CreatedAt should remain unchanged after update")
                .isEqualTo(originalCreatedAt);

        assertThat(milestoneUpdated.get("updatedBy"))
                .as("UpdatedBy should be populated on update")
                .isNotNull();

        assertThat(milestoneUpdated.get("updatedAt"))
                .as("UpdatedAt should be populated on update")
                .isNotNull();
    }
}

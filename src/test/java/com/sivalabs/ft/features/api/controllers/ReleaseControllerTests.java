package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.parameters.P;

class ReleaseControllerTests extends AbstractIT {

    @Test
    void shouldGetReleasesByProductCode() {
        var result =
                mvc.get().uri("/api/releases?productCode={code}", "intellij").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(3);
    }

    @Test
    void shouldGetReleaseByCode() {
        String code = "IDEA-2023.3.8";
        var result = mvc.get().uri("/api/releases/{code}", code).exchange();
        assertThat(result).hasStatusOk().bodyJson().convertTo(ReleaseDto.class).satisfies(dto -> {
            assertThat(dto.code()).isEqualTo(code);
        });
    }

    @Test
    void shouldReturn404WhenReleaseNotFound() {
        var result = mvc.get().uri("/api/releases/{code}", "INVALID_CODE").exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateNewRelease() {
        var payload =
                """
            {
                "productCode": "intellij",
                "code": "IDEA-2025.1",
                "description": "IntelliJ IDEA 2025.1"
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
    @WithMockOAuth2User(username = "user")
    void shouldUpdateRelease() {
        var payload =
                """
            {
                "description": "Updated description",
                "status": "RELEASED",
                "releasedAt": "2023-12-01T10:00:00Z"
            }
            """;

        var result = mvc.put()
                .uri("/api/releases/{code}", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatusOk();

        // Verify the update
        var updatedRelease =
                mvc.get().uri("/api/releases/{code}", "IDEA-2023.3.8").exchange();
        assertThat(updatedRelease)
                .hasStatusOk()
                .bodyJson()
                .convertTo(ReleaseDto.class)
                .satisfies(dto -> {
                    assertThat(dto.description()).isEqualTo("Updated description");
                    assertThat(dto.status()).isEqualTo(ReleaseStatus.RELEASED);
                    assertThat(dto.releasedAt()).isNotNull();
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldDeleteRelease() {
        var result = mvc.delete().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
        assertThat(result).hasStatusOk();

        // Verify deletion
        var getResult = mvc.get().uri("/api/releases/{code}", "RIDER-2024.2.6").exchange();
        assertThat(getResult).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void createNewReleaseWithParent() {
        var parentPayload =
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
                .content(parentPayload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);

        var payload =
                """
            {
                "productCode": "intellij",
                "code": "IDEA-2025.2.1",
                "parentCode": "IDEA-2025.2",
                "description": "IntelliJ IDEA 2025.2.1 Update"
            }
            """;

        var result2 = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result2).hasStatus(HttpStatus.CREATED);

        var updatedRelease =
                mvc.get().uri("/api/releases/{code}", "IDEA-2025.2.1").exchange();
        assertThat(updatedRelease)
                .hasStatusOk()
                .bodyJson()
                .convertTo(ReleaseDto.class)
                .satisfies(dto -> {
                    assertThat(dto.description()).isEqualTo("IntelliJ IDEA 2025.2.1 Update");
                    assertThat(dto.status()).isEqualTo(ReleaseStatus.DRAFT);
                    assertThat(dto.parentCode()).isEqualTo("IDEA-2025.2");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void whenParentReleaseDeletedChildReleaseShouldRemain() {
        var parentPayload =
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
                .content(parentPayload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);

        var payload =
                """
            {
                "productCode": "intellij",
                "code": "IDEA-2025.2.1",
                "parentCode": "IDEA-2025.2",
                "description": "IntelliJ IDEA 2025.2.1 Update"
            }
            """;

        var result2 = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result2).hasStatus(HttpStatus.CREATED);

        var deleteResult = mvc.delete().uri("/api/releases/{code}", "IDEA-2025.2").exchange();
        assertThat(deleteResult).hasStatusOk();

        var updatedRelease =
                mvc.get().uri("/api/releases/{code}", "IDEA-2025.2.1").exchange();
        assertThat(updatedRelease)
                .hasStatusOk()
                .bodyJson()
                .convertTo(ReleaseDto.class)
                .satisfies(dto -> {
                    assertThat(dto.description()).isEqualTo("IntelliJ IDEA 2025.2.1 Update");
                    assertThat(dto.status()).isEqualTo(ReleaseStatus.DRAFT);
                    assertThat(dto.parentCode()).isNull();
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void parentReleaseShouldBeEditable() {
        var parentPayload =
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
                .content(parentPayload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);

        var payload =
                """
            {
                "productCode": "intellij",
                "code": "IDEA-2025.2.1",
                "description": "IntelliJ IDEA 2025.2.1 Update"
            }
            """;

        var result2 = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result2).hasStatus(HttpStatus.CREATED);

        var updateParentPayload =
                """
            {
                "parentCode": "IDEA-2025.2",
                "status": "DRAFT",
                "description": "IntelliJ IDEA 2025.2.1 Update"
            }
            """;
        var result3 = mvc.put()
                .uri("/api/releases/{code}", "IDEA-2025.2.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateParentPayload)
                .exchange();
        assertThat(result3).hasStatusOk();

        var updatedRelease =
                mvc.get().uri("/api/releases/{code}", "IDEA-2025.2.1").exchange();
        assertThat(updatedRelease)
                .hasStatusOk()
                .bodyJson()
                .convertTo(ReleaseDto.class)
                .satisfies(dto -> {
                    assertThat(dto.parentCode()).isEqualTo("IDEA-2025.2");
                });

        var removeParentPayload =
                """
            {
                "status": "DRAFT",
                "description": "IntelliJ IDEA 2025.2.1 Update"
            }
            """;
        var result4 = mvc.put()
                .uri("/api/releases/{code}", "IDEA-2025.2.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(removeParentPayload)
                .exchange();
        assertThat(result4).hasStatus2xxSuccessful();

        var updatedRelease2 =
                mvc.get().uri("/api/releases/{code}", "IDEA-2025.2.1").exchange();
        assertThat(updatedRelease2)
                .hasStatusOk()
                .bodyJson()
                .convertTo(ReleaseDto.class)
                .satisfies(dto -> {
                    assertThat(dto.parentCode()).isNull();
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void releaseCantBeParentForItselfInCreateMethod() {
        var payload =
                """
            {
                "productCode": "intellij",
                "code": "IDEA-2025.2",
                "parentCode": "IDEA-2025.2",
                "description": "IntelliJ IDEA 2025.2"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasFailed();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void parentReleaseShouldExistInCreateMethod() {
        var payload =
                """
            {
                "productCode": "intellij",
                "code": "IDEA-2025.2",
                "parentCode": "WRONG_RELEASE_CODE",
                "description": "IntelliJ IDEA 2025.2"
            }
            """;

        var result = mvc.post()
                .uri("/api/releases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasFailed();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void releaseCantBeParentForItselfInUpdateMethod() {
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

        var updateParentPayload =
                """
                        {
                            "parentCode": "IDEA-2025.2",
                            "status": "DRAFT",
                            "description": "IntelliJ IDEA 2025.2 Update"
                        }
                        """;
        var result2 = mvc.put()
                .uri("/api/releases/{code}", "IDEA-2025.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateParentPayload)
                .exchange();
        assertThat(result2).hasFailed();

        var updatedRelease =
                mvc.get().uri("/api/releases/{code}", "IDEA-2025.2").exchange();
        assertThat(updatedRelease)
                .hasStatusOk()
                .bodyJson()
                .convertTo(ReleaseDto.class)
                .satisfies(dto -> {
                    assertThat(dto.parentCode()).isNull();
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void parentReleaseShouldExistInUpdateMethod() {
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

        var updateParentPayload =
                """
            {
                "parentCode": "WRONG_RELEASE_CODE",
                "status": "DRAFT",
                "description": "IntelliJ IDEA 2025.2 Update"
            }
            """;
        var result2 = mvc.put()
                .uri("/api/releases/{code}", "IDEA-2025.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateParentPayload)
                .exchange();
        assertThat(result2).hasFailed();

        var updatedRelease =
                mvc.get().uri("/api/releases/{code}", "IDEA-2025.2").exchange();
        assertThat(updatedRelease)
                .hasStatusOk()
                .bodyJson()
                .convertTo(ReleaseDto.class)
                .satisfies(dto -> {
                    assertThat(dto.parentCode()).isNull();
                });
    }
}

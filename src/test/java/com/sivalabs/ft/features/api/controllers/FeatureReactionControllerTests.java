package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class FeatureReactionControllerTests extends AbstractIT {

    private static final String FEATURE_CODE = "IDEA-1";

    @Test
    @WithMockOAuth2User(username = "user")
    void addUpdateAndGetCurrentUsersReaction() {
        var addResult = mvc.post()
                .uri("/api/feature-reactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reactionPayload(FEATURE_CODE, "LIKE"))
                .exchange();

        assertThat(addResult).hasStatus(HttpStatus.CREATED);
        assertThat(addResult.getMvcResult().getResponse().getHeader("Location")).isNotBlank();

        mvc.get()
                .uri("/api/feature-reactions/{featureCode}/user", FEATURE_CODE)
                .exchange()
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.reactionType")
                .asString()
                .isEqualTo("LIKE");

        mvc.post()
                .uri("/api/feature-reactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reactionPayload(FEATURE_CODE, "DISLIKE"))
                .exchange()
                .assertThat()
                .hasStatus(HttpStatus.CREATED);

        mvc.get()
                .uri("/api/feature-reactions/{featureCode}/user", FEATURE_CODE)
                .exchange()
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.reactionType")
                .asString()
                .isEqualTo("DISLIKE");
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void removeCurrentUsersReaction() {
        mvc.post()
                .uri("/api/feature-reactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reactionPayload(FEATURE_CODE, "LIKE"))
                .exchange()
                .assertThat()
                .hasStatus(HttpStatus.CREATED);

        mvc.delete()
                .uri("/api/feature-reactions/{featureCode}", FEATURE_CODE)
                .exchange()
                .assertThat()
                .hasStatusOk();

        mvc.get()
                .uri("/api/feature-reactions/{featureCode}/user", FEATURE_CODE)
                .exchange()
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void listReactionsForFeature() {
        mvc.post()
                .uri("/api/feature-reactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reactionPayload(FEATURE_CODE, "LIKE"))
                .exchange()
                .assertThat()
                .hasStatus(HttpStatus.CREATED);

        mvc.get()
                .uri("/api/feature-reactions/{featureCode}", FEATURE_CODE)
                .exchange()
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].reactionType")
                .asString()
                .isEqualTo("LIKE");
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void getMostLikedFeatures() {
        mvc.post()
                .uri("/api/feature-reactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reactionPayload(FEATURE_CODE, "LIKE"))
                .exchange()
                .assertThat()
                .hasStatus(HttpStatus.CREATED);

        mvc.get()
                .uri("/api/feature-reactions/most-liked")
                .exchange()
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].code")
                .asString()
                .isEqualTo(FEATURE_CODE);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void returnNotFoundForUnknownFeature() {
        mvc.post()
                .uri("/api/feature-reactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reactionPayload("UNKNOWN", "LIKE"))
                .exchange()
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private String reactionPayload(String featureCode, String reactionType) {
        return """
                {
                    "featureCode": "%s",
                    "reactionType": "%s"
                }
                """
                .formatted(featureCode, reactionType);
    }
}

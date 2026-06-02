package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class ReleaseFeaturePlanningOwnerFilterTests extends AbstractIT {
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockOAuth2User(username = "user")
    void ownerFilterMatchesPartialOwnerName() throws Exception {
        assignFeature("IDEA-5", "alice.smith");
        assignFeature("IDEA-6", "bob.jones");

        var result = mvc.get()
                .uri("/api/releases/{releaseCode}/features?owner={owner}", "IDEA-2023.3.8", "alice")
                .exchange();

        assertThat(result).hasStatusOk();
        var responseBody = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> features = objectMapper.readValue(responseBody, new TypeReference<>() {});

        assertThat(features)
                .extracting(item -> item.get("code"))
                .contains("IDEA-5")
                .doesNotContain("IDEA-6");
        assertThat(features).extracting(item -> item.get("featureOwner")).containsOnly("alice.smith");
    }

    private void assignFeature(String featureCode, String owner) {
        var payload =
                """
                {
                  "featureCode": "%s",
                  "plannedCompletionDate": "2024-12-31",
                  "featureOwner": "%s",
                  "notes": "owner filter setup"
                }
                """
                        .formatted(featureCode, owner);

        assertThat(mvc.post()
                        .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .exchange())
                .hasStatus2xxSuccessful();
    }
}

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

class ReleaseFeaturePlanningPatchPreservesFieldsTests extends AbstractIT {
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockOAuth2User(username = "user")
    void patchPlanningPreservesOmittedOwnerField() throws Exception {
        var assignPayload =
                """
                {
                  "featureCode": "IDEA-8",
                  "plannedCompletionDate": "2024-12-31",
                  "featureOwner": "existingowner",
                  "notes": "existing notes"
                }
                """;

        assertThat(mvc.post()
                        .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignPayload)
                        .exchange())
                .hasStatus2xxSuccessful();

        var patchPayload =
                """
                {
                  "planningStatus": "IN_PROGRESS",
                  "notes": "updated notes only"
                }
                """;

        assertThat(mvc.patch()
                        .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchPayload)
                        .exchange())
                .hasStatusOk();

        var getResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();
        assertThat(getResult).hasStatusOk();

        var responseBody = new String(getResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> features = objectMapper.readValue(responseBody, new TypeReference<>() {});
        var feature = features.stream()
                .filter(item -> "IDEA-8".equals(item.get("code")))
                .findFirst()
                .orElseThrow();

        assertThat(feature.get("featureOwner")).isEqualTo("existingowner");
        assertThat(feature.get("notes")).isEqualTo("updated notes only");
        assertThat(feature.get("planningStatus")).isEqualTo("IN_PROGRESS");
    }
}

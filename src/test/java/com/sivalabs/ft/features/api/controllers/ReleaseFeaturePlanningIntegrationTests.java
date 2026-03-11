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
import org.springframework.http.MediaType;

@DisplayName("Release Feature Planning Integration Tests")
class ReleaseFeaturePlanningIntegrationTests extends AbstractIT {
    @Autowired
    private ObjectMapper objectMapper;

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
    @DisplayName("Should filter features by owner ignoring case and whitespace")
    @WithMockOAuth2User(username = "user")
    void shouldFilterFeaturesByOwnerIgnoringCaseAndWhitespace() throws JsonMappingException, JsonProcessingException {
        var feature1Payload =
                """
        {
        "featureCode": "IDEA-4",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "Alice.Smith",
        "notes": "Feature 1"
        }
        """;
        var feature2Payload =
                """
        {
        "featureCode": "IDEA-5",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "alice.cooper",
        "notes": "Feature 2"
        }
        """;
        var feature3Payload =
                """
        {
        "featureCode": "IDEA-6",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "  alice.jones  ",
        "notes": "Feature 3"
        }
        """;
        var feature4Payload =
                """
        {
        "featureCode": "IDEA-7",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "   ",
        "notes": "Feature 4"
        }
        """;
        var feature5Payload =
                """
        {
        "featureCode": "IDEA-8",
        "plannedCompletionDate": "2024-12-31",
        "featureOwner": "bob.jones",
        "notes": "Feature 5"
        }
        """;
        // Mix casing, surrounding whitespace, whitespace-only owner, and a non-matching owner.
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
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feature3Payload)
                .exchange();
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feature4Payload)
                .exchange();
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feature5Payload)
                .exchange();

        // Uppercase request param should match owners regardless of casing in stored values.
        var uppercaseResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features?owner={owner}", "IDEA-2023.3.8", "ALICE")
                .exchange();
        assertThat(uppercaseResult).hasStatus2xxSuccessful();
        var uppercaseResponseBody =
                new String(uppercaseResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> uppercaseFeatures =
                objectMapper.readValue(uppercaseResponseBody, new TypeReference<>() {});
        List<String> uppercaseCodes =
                uppercaseFeatures.stream().map(f -> (String) f.get("code")).toList();
        assertThat(uppercaseCodes).containsExactlyInAnyOrder("IDEA-4", "IDEA-5", "IDEA-6");

        // Request trimming should behave the same as a clean owner filter.
        var trimmedResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features?owner={owner}", "IDEA-2023.3.8", "  alice  ")
                .exchange();
        assertThat(trimmedResult).hasStatus2xxSuccessful();
        var trimmedResponseBody =
                new String(trimmedResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> trimmedFeatures =
                objectMapper.readValue(trimmedResponseBody, new TypeReference<>() {});
        List<String> trimmedCodes =
                trimmedFeatures.stream().map(f -> (String) f.get("code")).toList();
        assertThat(trimmedCodes).containsExactlyInAnyOrder("IDEA-4", "IDEA-5", "IDEA-6");
    }

    @Test
    @DisplayName("Should treat blank owner filter as no owner filter")
    @WithMockOAuth2User(username = "user")
    void shouldTreatBlankOwnerFilterAsNoOwnerFilter() throws JsonMappingException, JsonProcessingException {
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                {
                "featureCode": "IDEA-4",
                "plannedCompletionDate": "2024-12-31",
                "featureOwner": "alice.smith",
                "notes": "Feature 1"
                }
                """)
                .exchange();
        mvc.post()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                {
                "featureCode": "IDEA-5",
                "plannedCompletionDate": "2024-12-31",
                "featureOwner": "bob.jones",
                "notes": "Feature 2"
                }
                """)
                .exchange();

        // Blank owner filter is normalized away and should not break the release search.
        var blankOwnerResult = mvc.get()
                .uri("/api/releases/{releaseCode}/features?owner={owner}", "IDEA-2023.3.8", "   ")
                .exchange();
        assertThat(blankOwnerResult).hasStatus2xxSuccessful();
        var blankOwnerResponseBody =
                new String(blankOwnerResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Map<String, Object>> blankOwnerFeatures =
                objectMapper.readValue(blankOwnerResponseBody, new TypeReference<>() {});
        List<String> blankOwnerCodes =
                blankOwnerFeatures.stream().map(f -> (String) f.get("code")).toList();
        assertThat(blankOwnerCodes).contains("IDEA-1", "IDEA-2", "IDEA-4", "IDEA-5");
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
        // Filter by multiple criteria  - overdue AND owner
        assertThat(combinedFeatures.stream().anyMatch(f -> "IDEA-4".equals(f.get("code"))))
                .isTrue();
        assertThat(combinedFeatures.stream().anyMatch(f -> "IDEA-5".equals(f.get("code"))))
                .isFalse();
    }
}

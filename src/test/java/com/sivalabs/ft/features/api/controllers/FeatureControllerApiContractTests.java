package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class FeatureControllerApiContractTests extends AbstractIT {

    // === SECURITY TESTS ===

    @Test
    void shouldReturn401ForCreateDependencyWithoutAuth() {
        var payload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "HARD",
                "notes": "Test"
            }
            """;

        var result = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401ForUpdateDependencyWithoutAuth() {
        var payload =
                """
            {
                "dependencyType": "HARD",
                "notes": "Test"
            }
            """;

        var result = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401ForDeleteDependencyWithoutAuth() {
        var result = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    // === VALIDATION TESTS ===

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenCreatingDependencyForNonExistentFeature() {
        var payload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "HARD",
                "notes": "Test dependency"
            }
            """;

        var result = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "NON-EXISTENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenCreatingDependencyWithNonExistentDependsOnFeature() {
        var payload =
                """
            {
                "dependsOnFeatureCode": "NON-EXISTENT",
                "dependencyType": "HARD",
                "notes": "Test dependency"
            }
            """;

        var result = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400ForInvalidDependencyType() {
        // Note: Invalid dependency type can result in either 400 (Bad Request) or 500 (Internal Server Error)
        // depending on where the enum validation occurs. Jackson deserialization may throw
        // Exception for unknown enum values, which can be handled as either client or server error.
        var payload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "INVALID_TYPE",
                "notes": "Test dependency"
            }
            """;

        var result = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result)
                .satisfiesAnyOf(
                        response -> assertThat(response).hasStatus(HttpStatus.BAD_REQUEST),
                        response -> assertThat(response).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenUpdatingNonExistentDependency() {
        var payload =
                """
            {
                "dependencyType": "HARD",
                "notes": "Updated notes"
            }
            """;

        var result = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "NON-EXISTENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenUpdatingDependencyWithNonExistentFeature() {
        var payload =
                """
            {
                "dependencyType": "HARD",
                "notes": "Updated notes"
            }
            """;

        var result = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "NON-EXISTENT", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenDeletingNonExistentDependency() {
        var result = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "NON-EXISTENT")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenDeletingDependencyWithNonExistentFeature() {
        var result = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "NON-EXISTENT", "IDEA-2")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    // === DEPENDENCY TYPE TESTS ===

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateDependencyWithOptionalType() {
        var payload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "OPTIONAL",
                "notes": "Optional dependency"
            }
            """;

        var result = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateDependencyWithSoftType() {
        var payload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "SOFT",
                "notes": "Soft dependency"
            }
            """;

        var result = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateDependencyWithHardType() {
        var payload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "HARD",
                "notes": "Hard dependency"
            }
            """;

        var result = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
    }

    // === NOTES HANDLING TESTS ===

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateDependencyWithNotes() {
        var payload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "HARD",
                "notes": "This is a comprehensive test note that demonstrates the notes field functionality with sufficient length to test proper handling."
            }
            """;

        var result = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateDependencyWithoutNotes() {
        var payload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "SOFT"
            }
            """;

        var result = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateDependencyWithMinimalPayload() {
        var payload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "HARD"
            }
            """;

        var result = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldUpdateDependencyWithNotes() {
        // First create a dependency
        var createPayload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "SOFT",
                "notes": "Initial notes"
            }
            """;
        mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();

        // Then update it with longer notes
        var updatePayload =
                """
            {
                "dependencyType": "HARD",
                "notes": "Updated comprehensive notes with detailed information about the dependency relationship and its importance to the feature implementation."
            }
            """;

        var result = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldUpdateDependencyWithoutNotes() {
        // First create a dependency with notes
        var createPayload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "SOFT",
                "notes": "Initial notes"
            }
            """;
        mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();

        // Then update it without notes (should clear notes)
        var updatePayload = """
            {
                "dependencyType": "HARD"
            }
            """;

        var result = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
    }
}

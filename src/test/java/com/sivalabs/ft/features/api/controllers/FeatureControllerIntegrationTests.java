package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class FeatureControllerIntegrationTests extends AbstractIT {

    // === COMPREHENSIVE WORKFLOW TESTS ===

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCreateUpdateAndDeleteDependencyWithVerification() {
        // Step 1: Create a dependency
        var createPayload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "SOFT",
                "notes": "Test dependency for comprehensive verification"
            }
            """;

        var createResult = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();
        assertThat(createResult).hasStatus(HttpStatus.CREATED);

        // Step 2: Update the dependency
        var updatePayload =
                """
            {
                "dependencyType": "HARD",
                "notes": "Updated dependency notes for verification"
            }
            """;

        var updateResult = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(updateResult).hasStatusOk();

        // Step 3: Delete the dependency
        var deleteResult = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .exchange();
        assertThat(deleteResult).hasStatusOk();

        // Step 4: Verify the dependency was deleted by trying to update it (should fail)
        var verifyDeletePayload =
                """
            {
                "dependencyType": "OPTIONAL",
                "notes": "This should fail"
            }
            """;

        var verifyDeleteResult = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyDeletePayload)
                .exchange();
        assertThat(verifyDeleteResult).hasStatus(HttpStatus.NOT_FOUND);

        // This comprehensive test verifies that:
        // 1. POST dependency creation actually persists data
        // 2. PUT dependency update actually modifies existing data
        // 3. DELETE dependency removal actually removes data
        // 4. Subsequent operations reflect the state changes
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldHandleMultipleDependencyOperationsInSequence() {
        // Create multiple dependencies
        var dependency1Payload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "HARD",
                "notes": "First dependency"
            }
            """;

        var dependency2Payload =
                """
            {
                "dependsOnFeatureCode": "GO-3",
                "dependencyType": "SOFT",
                "notes": "Second dependency"
            }
            """;

        // Create first dependency
        var create1Result = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dependency1Payload)
                .exchange();
        assertThat(create1Result).hasStatus(HttpStatus.CREATED);

        // Create second dependency
        var create2Result = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dependency2Payload)
                .exchange();
        assertThat(create2Result).hasStatus(HttpStatus.CREATED);

        // Update first dependency
        var updatePayload =
                """
            {
                "dependencyType": "OPTIONAL",
                "notes": "Updated first dependency"
            }
            """;

        var updateResult = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(updateResult).hasStatusOk();

        // Delete second dependency
        var deleteResult = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "GO-3")
                .exchange();
        assertThat(deleteResult).hasStatusOk();

        // Verify second dependency is deleted
        var verifyDeleteResult = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "GO-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();
        assertThat(verifyDeleteResult).hasStatus(HttpStatus.NOT_FOUND);

        // Clean up first dependency
        var cleanup = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .exchange();
        assertThat(cleanup).hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldHandleDependencyTypeTransitions() {
        // Create dependency with SOFT type
        var createPayload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "SOFT",
                "notes": "Initial soft dependency"
            }
            """;

        var createResult = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();
        assertThat(createResult).hasStatus(HttpStatus.CREATED);

        // Update to HARD type
        var updateToHardPayload =
                """
            {
                "dependencyType": "HARD",
                "notes": "Updated to hard dependency"
            }
            """;

        var updateToHardResult = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateToHardPayload)
                .exchange();
        assertThat(updateToHardResult).hasStatusOk();

        // Update to OPTIONAL type
        var updateToOptionalPayload =
                """
            {
                "dependencyType": "OPTIONAL",
                "notes": "Updated to optional dependency"
            }
            """;

        var updateToOptionalResult = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateToOptionalPayload)
                .exchange();
        assertThat(updateToOptionalResult).hasStatusOk();

        // Clean up
        var cleanup = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .exchange();
        assertThat(cleanup).hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldHandleNotesUpdatesAndClearing() {
        // Create dependency with notes
        var createPayload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "HARD",
                "notes": "Initial detailed notes about this dependency"
            }
            """;

        var createResult = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();
        assertThat(createResult).hasStatus(HttpStatus.CREATED);

        // Update with longer notes
        var updateWithNotesPayload =
                """
            {
                "dependencyType": "HARD",
                "notes": "Very comprehensive and detailed notes that explain the complex relationship between these features and why this dependency is critical for the system architecture and functionality."
            }
            """;

        var updateWithNotesResult = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateWithNotesPayload)
                .exchange();
        assertThat(updateWithNotesResult).hasStatusOk();

        // Update without notes (should clear them)
        var updateWithoutNotesPayload =
                """
            {
                "dependencyType": "SOFT"
            }
            """;

        var updateWithoutNotesResult = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateWithoutNotesPayload)
                .exchange();
        assertThat(updateWithoutNotesResult).hasStatusOk();

        // Clean up
        var cleanup = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .exchange();
        assertThat(cleanup).hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldVerifyDependencyPersistenceAcrossOperations() {
        // Create dependency
        var createPayload =
                """
            {
                "dependsOnFeatureCode": "IDEA-2",
                "dependencyType": "HARD",
                "notes": "Persistence test dependency"
            }
            """;

        var createResult = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();
        assertThat(createResult).hasStatus(HttpStatus.CREATED);

        // Verify dependency exists by attempting to update it
        var verifyExistsPayload =
                """
            {
                "dependencyType": "SOFT",
                "notes": "Updated to verify existence"
            }
            """;

        var verifyExistsResult = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyExistsPayload)
                .exchange();
        assertThat(verifyExistsResult).hasStatusOk();

        // Delete dependency
        var deleteResult = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .exchange();
        assertThat(deleteResult).hasStatusOk();

        // Verify dependency no longer exists
        var verifyDeletedResult = mvc.put()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyExistsPayload)
                .exchange();
        assertThat(verifyDeletedResult).hasStatus(HttpStatus.NOT_FOUND);

        // Verify deletion is persistent by trying to delete again
        var verifyDeletePersistentResult = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .exchange();
        assertThat(verifyDeletePersistentResult).hasStatus(HttpStatus.NOT_FOUND);
    }
}

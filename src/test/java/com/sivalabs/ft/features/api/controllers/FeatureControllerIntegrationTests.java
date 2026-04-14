package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class FeatureControllerIntegrationTests extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

        // Verify dependency was created in database
        verifyDependencyExists("IDEA-1", "IDEA-2", "SOFT", "Test dependency for comprehensive verification");

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
        assertThat(updateResult).hasStatus2xxSuccessful();

        // Verify dependency was updated in database
        verifyDependencyExists("IDEA-1", "IDEA-2", "HARD", "Updated dependency notes for verification");

        // Step 3: Delete the dependency
        var deleteResult = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .exchange();
        assertThat(deleteResult).hasStatus2xxSuccessful();

        // Verify dependency was deleted from database
        verifyDependencyNotExists("IDEA-1", "IDEA-2");

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

        // Verify first dependency created
        verifyDependencyExists("IDEA-1", "IDEA-2", "HARD", "First dependency");

        // Create second dependency
        var create2Result = mvc.post()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dependency2Payload)
                .exchange();
        assertThat(create2Result).hasStatus(HttpStatus.CREATED);

        // Verify second dependency created and total count
        verifyDependencyExists("IDEA-1", "GO-3", "SOFT", "Second dependency");
        assertThat(countDependenciesForFeature("IDEA-1")).isEqualTo(2);

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
        assertThat(updateResult).hasStatus2xxSuccessful();

        // Verify first dependency was updated
        verifyDependencyExists("IDEA-1", "IDEA-2", "OPTIONAL", "Updated first dependency");

        // Delete second dependency
        var deleteResult = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "GO-3")
                .exchange();
        assertThat(deleteResult).hasStatus2xxSuccessful();

        // Verify second dependency was deleted from database
        verifyDependencyNotExists("IDEA-1", "GO-3");
        assertThat(countDependenciesForFeature("IDEA-1")).isEqualTo(1);

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
        assertThat(cleanup).hasStatus2xxSuccessful();
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

        // Verify initial SOFT dependency
        verifyDependencyExists("IDEA-1", "IDEA-2", "SOFT", "Initial soft dependency");

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
        assertThat(updateToHardResult).hasStatus2xxSuccessful();

        // Verify transition to HARD type
        verifyDependencyExists("IDEA-1", "IDEA-2", "HARD", "Updated to hard dependency");

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
        assertThat(updateToOptionalResult).hasStatus2xxSuccessful();

        // Verify final transition to OPTIONAL type
        verifyDependencyExists("IDEA-1", "IDEA-2", "OPTIONAL", "Updated to optional dependency");

        // Clean up
        var cleanup = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .exchange();
        assertThat(cleanup).hasStatus2xxSuccessful();
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

        // Verify initial dependency with notes
        verifyDependencyExists("IDEA-1", "IDEA-2", "HARD", "Initial detailed notes about this dependency");

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
        assertThat(updateWithNotesResult).hasStatus2xxSuccessful();

        // Verify notes were updated
        verifyDependencyExists(
                "IDEA-1",
                "IDEA-2",
                "HARD",
                "Very comprehensive and detailed notes that explain the complex relationship between these features and why this dependency is critical for the system architecture and functionality.");

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
        assertThat(updateWithoutNotesResult).hasStatus2xxSuccessful();

        // Verify notes were cleared (should be null)
        verifyDependencyExists("IDEA-1", "IDEA-2", "SOFT", null);

        // Clean up
        var cleanup = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .exchange();
        assertThat(cleanup).hasStatus2xxSuccessful();
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

        // Verify dependency was persisted in database
        verifyDependencyExists("IDEA-1", "IDEA-2", "HARD", "Persistence test dependency");

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
        assertThat(verifyExistsResult).hasStatus2xxSuccessful();

        // Verify update was persisted
        verifyDependencyExists("IDEA-1", "IDEA-2", "SOFT", "Updated to verify existence");

        // Delete dependency
        var deleteResult = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", "IDEA-1", "IDEA-2")
                .exchange();
        assertThat(deleteResult).hasStatus2xxSuccessful();

        // Verify deletion was persisted in database
        verifyDependencyNotExists("IDEA-1", "IDEA-2");

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

    // === HELPER METHODS FOR DATABASE VERIFICATION ===
    // Note: We use JdbcTemplate for direct database access instead of FeatureDependencyRepository
    // because the repository has package-private visibility (default access modifier) and is not
    // accessible from this test class which is in a different package (api.controllers vs domain).
    // Direct SQL queries ensure we can verify the actual database state independently of the
    // application's domain layer, providing true integration test verification.

    private void verifyDependencyExists(
            String featureCode, String dependsOnFeatureCode, String expectedType, String expectedNotes) {
        String sql =
                """
            SELECT dependency_type, notes
            FROM feature_dependencies
            WHERE feature_code = ? AND depends_on_feature_code = ?
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, featureCode, dependsOnFeatureCode);

        assertThat(results).hasSize(1);
        Map<String, Object> dependency = results.get(0);
        assertThat(dependency.get("dependency_type")).isEqualTo(expectedType);
        assertThat(dependency.get("notes")).isEqualTo(expectedNotes);
    }

    private void verifyDependencyNotExists(String featureCode, String dependsOnFeatureCode) {
        String sql =
                """
            SELECT COUNT(*)
            FROM feature_dependencies
            WHERE feature_code = ? AND depends_on_feature_code = ?
            """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, featureCode, dependsOnFeatureCode);
        assertThat(count).isEqualTo(0);
    }

    private int countDependenciesForFeature(String featureCode) {
        String sql =
                """
            SELECT COUNT(*)
            FROM feature_dependencies
            WHERE feature_code = ?
            """;

        return jdbcTemplate.queryForObject(sql, Integer.class, featureCode);
    }
}

package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class FeatureDependencyEndpointsIntegrationTests extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String idea3;
    private String go4;

    @BeforeEach
    void setUp() {
        // Clean up any existing dependencies for test features
        jdbcTemplate.update("DELETE FROM feature_dependencies");

        // Create additional test features through API and store their codes
        idea3 = createTestFeature("intellij", "IDEA-2023.3.8", "IDEA Feature 3", "Third IDEA feature for testing");
        go4 = createTestFeature("goland", "GO-2024.2.3", "GO Feature 4", "Fourth GO feature for testing");
        createTestFeature("goland", "GO-2024.2.3", "GO Feature 5", "Fifth GO feature for testing");
    }

    // === DEPENDENCIES ENDPOINT TESTS ===

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldGetDependenciesForFeature() {
        // Given - Create dependencies within same product (IDEA features depend on other IDEA features)
        createTestDependency("IDEA-1", "IDEA-2", "HARD", "IDEA-1 depends on IDEA-2");
        createTestDependency("IDEA-1", idea3, "SOFT", "IDEA-1 depends on " + idea3);

        // When - Get dependencies
        var result = mvc.get()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(2);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturnEmptyListWhenFeatureHasNoDependencies() {
        // When - Get dependencies for feature with no dependencies
        var result = mvc.get()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-2")
                .exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenFeatureDoesNotExist() {
        // When - Get dependencies for non-existent feature
        var result = mvc.get()
                .uri("/api/features/{featureCode}/dependencies", "NON-EXISTENT")
                .exchange();

        // Then
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldFilterDependenciesByProductCode() {
        // Given - Create dependencies within same product
        createTestDependency("IDEA-1", "IDEA-2", "HARD", "IDEA dependency");
        createTestDependency("IDEA-1", idea3, "SOFT", "IDEA dependency");
        createTestDependency("GO-3", go4, "SOFT", "GO dependency");

        // When - Get IDEA dependencies filtered by intellij product
        var result = mvc.get()
                .uri("/api/features/{featureCode}/dependencies?productCode=intellij", "IDEA-1")
                .exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(2);

        // Verify the returned features are IDEA features
        assertThat(result)
                .bodyJson()
                .extractingPath("$[0]")
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.code()).isIn("IDEA-2", idea3);
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldFilterDependenciesByReleaseCode() {
        // Given - Create dependencies within same product
        createTestDependency("IDEA-1", "IDEA-2", "HARD", "IDEA dependency");

        // When - Get dependencies filtered by IDEA release code
        var result = mvc.get()
                .uri("/api/features/{featureCode}/dependencies?releaseCode=IDEA-2023.3.8", "IDEA-1")
                .exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldFilterDependenciesByStatus() {
        // Given - Create dependencies within same product
        createTestDependency("GO-3", go4, "HARD", "GO dependency");

        // When - Get dependencies filtered by NEW status (GO-4 has NEW status)
        var result = mvc.get()
                .uri("/api/features/{featureCode}/dependencies?status=NEW", "GO-3")
                .exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(1);
    }

    // === DEPENDENTS ENDPOINT TESTS ===

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldGetDependentsForFeature() {
        // Given - Create features that depend on IDEA-2 (only IDEA features)
        createTestDependency("IDEA-1", "IDEA-2", "HARD", "IDEA-1 depends on IDEA-2");
        createTestDependency(idea3, "IDEA-2", "SOFT", idea3 + " depends on IDEA-2");

        // When - Get dependents
        var result = mvc.get()
                .uri("/api/features/{featureCode}/dependents", "IDEA-2")
                .exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(2);

        // Verify the structure of the first dependent
        assertThat(result)
                .bodyJson()
                .extractingPath("$[0]")
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.code()).isIn("IDEA-1", idea3);
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturnEmptyListWhenFeatureHasNoDependents() {
        // When - Get dependents for feature with no dependents
        var result = mvc.get()
                .uri("/api/features/{featureCode}/dependents", "IDEA-1")
                .exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldFilterDependentsByProductCode() {
        // Given - Create dependencies within different products
        createTestDependency("IDEA-1", "IDEA-2", "HARD", "IDEA-1 depends on IDEA-2");
        createTestDependency("GO-3", go4, "SOFT", "GO-3 depends on " + go4);

        // When - Get dependents of GO-4 filtered by goland product
        var result = mvc.get()
                .uri("/api/features/{featureCode}/dependents?productCode=goland", go4)
                .exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(1);

        // Verify the returned dependent
        assertThat(result)
                .bodyJson()
                .extractingPath("$[0]")
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.code()).isEqualTo("GO-3");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldFilterDependentsByReleaseCode() {
        // Given - Create features that depend on IDEA-2 (only IDEA features)
        createTestDependency("IDEA-1", "IDEA-2", "HARD", "IDEA-1 depends on IDEA-2");

        // When - Get dependents filtered by IDEA release code
        var result = mvc.get()
                .uri("/api/features/{featureCode}/dependents?releaseCode=IDEA-2023.3.8", "IDEA-2")
                .exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldFilterDependentsByStatus() {
        // Given - Create dependencies within same product
        createTestDependency("GO-3", go4, "HARD", "GO-3 depends on " + go4);

        // When - Get dependents of GO-4 filtered by IN_PROGRESS status (GO-3 has IN_PROGRESS status)
        var result = mvc.get()
                .uri("/api/features/{featureCode}/dependents?status=IN_PROGRESS", go4)
                .exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(1);
    }

    // === IMPACT ANALYSIS ENDPOINT TESTS ===

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldGetImpactAnalysisForFeature() {
        // Given - Create a dependency chain within same product: IDEA-1 -> IDEA-2
        createTestDependency("IDEA-1", "IDEA-2", "HARD", "IDEA-1 depends on IDEA-2");

        // When - Get impact analysis for IDEA-2 (should return IDEA-1)
        var result =
                mvc.get().uri("/api/features/{featureCode}/impact", "IDEA-2").exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(1); // Should include IDEA-1

        // Verify we get the correct feature code in the impact
        assertThat(result)
                .bodyJson()
                .extractingPath("$[0]")
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.code()).isEqualTo("IDEA-1");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldHandleCircularDependenciesInImpactAnalysis() {
        // Given - Create circular dependencies within same product: IDEA-1 -> IDEA-2 -> IDEA-1
        createTestDependency("IDEA-1", "IDEA-2", "HARD", "IDEA-1 depends on IDEA-2");
        createTestDependency("IDEA-2", "IDEA-1", "SOFT", "IDEA-2 depends on IDEA-1 - creates cycle");

        // When - Get impact analysis
        var result =
                mvc.get().uri("/api/features/{featureCode}/impact", "IDEA-1").exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(1); // Should include IDEA-2 (but not IDEA-1 itself)

        // Verify circular reference is handled correctly (no infinite loop)
        assertThat(result)
                .bodyJson()
                .extractingPath("$[0]")
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.code()).isEqualTo("IDEA-2");
                    assertThat(dto.code()).isNotEqualTo("IDEA-1");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturnEmptyImpactWhenFeatureHasNoDependents() {
        // When - Get impact analysis for feature with no dependents
        var result =
                mvc.get().uri("/api/features/{featureCode}/impact", "IDEA-1").exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(0);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldFilterImpactAnalysisByProductCode() {
        // Given - Create dependencies within same product
        createTestDependency("IDEA-1", "IDEA-2", "HARD", "IDEA-1 depends on IDEA-2");

        // When - Get impact analysis for IDEA-2 filtered by intellij product
        var result = mvc.get()
                .uri("/api/features/{featureCode}/impact?productCode=intellij", "IDEA-2")
                .exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(1);

        // Should return IDEA-1 impact
        assertThat(result)
                .bodyJson()
                .extractingPath("$[0]")
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.code()).isEqualTo("IDEA-1");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldFilterImpactAnalysisByReleaseCode() {
        // Given - Create dependencies within same product
        createTestDependency("IDEA-1", "IDEA-2", "HARD", "IDEA-1 depends on IDEA-2");

        // When - Get impact analysis for IDEA-2 filtered by IDEA release code
        var result = mvc.get()
                .uri("/api/features/{featureCode}/impact?releaseCode=IDEA-2023.3.8", "IDEA-2")
                .exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldFilterImpactAnalysisByStatus() {
        // Given - Create dependencies within same product
        createTestDependency("IDEA-1", "IDEA-2", "HARD", "IDEA-1 depends on IDEA-2");

        // When - Get impact analysis for IDEA-2 filtered by NEW status (IDEA-1 has NEW status)
        var result = mvc.get()
                .uri("/api/features/{featureCode}/impact?status=NEW", "IDEA-2")
                .exchange();

        // Then
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldFilterCrossProductImpactAnalysis() {
        // Given - Create cross-product dependency chain: IDEA-1 -> GO-3 -> IDEA-2
        createTestDependency("IDEA-1", "GO-3", "HARD", "IDEA-1 depends on GO-3");
        createTestDependency("GO-3", "IDEA-2", "SOFT", "GO-3 depends on IDEA-2");

        // When - Get impact analysis for IDEA-2 filtered by intellij product
        var result = mvc.get()
                .uri("/api/features/{featureCode}/impact?productCode=intellij", "IDEA-2")
                .exchange();

        // Then - Should return only IDEA-1 (filtered by intellij product, excluding GO-3)
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(1);

        // Verify the returned feature is IDEA-1
        assertThat(result)
                .bodyJson()
                .extractingPath("$[0]")
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.code()).isEqualTo("IDEA-1");
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldHandleCircularFeatureDeletionThroughAPI() {
        // Given - Create circular dependency within same product: IDEA-1 -> IDEA-2 -> IDEA-3 -> IDEA-1
        createTestDependency("IDEA-1", "IDEA-2", "HARD", "IDEA-1 depends on IDEA-2");
        createTestDependency("IDEA-2", idea3, "SOFT", "IDEA-2 depends on " + idea3);
        createTestDependency(idea3, "IDEA-1", "OPTIONAL", idea3 + " depends on IDEA-1 - creates cycle");

        // When - Get impact analysis before deletion
        var impactBefore =
                mvc.get().uri("/api/features/{featureCode}/impact", "IDEA-1").exchange();

        // Then - Verify circular dependency exists
        assertThat(impactBefore)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(2); // Should include IDEA-2 and IDEA-3

        // When - Delete IDEA-3 feature through API
        var deleteResult = mvc.delete().uri("/api/features/{code}", idea3).exchange();

        // Then - Verify deletion was successful
        assertThat(deleteResult).hasStatusOk();

        // When - Get impact analysis after deletion
        var impactAfter =
                mvc.get().uri("/api/features/{featureCode}/impact", "IDEA-1").exchange();

        // Then - Verify circular dependency is broken
        assertThat(impactAfter)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(0); // Should be empty because after deleting IDEA-3, no one depends on IDEA-1

        // Verify IDEA-3 no longer exists
        var getDeletedFeature = mvc.get().uri("/api/features/{code}", idea3).exchange();
        assertThat(getDeletedFeature).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldHandleCircularDependencyDeletionThroughAPI() {
        // Given - Create circular dependency within same product: IDEA-1 -> IDEA-2 -> IDEA-3 -> IDEA-1
        createTestDependency("IDEA-1", "IDEA-2", "HARD", "IDEA-1 depends on IDEA-2");
        createTestDependency("IDEA-2", idea3, "SOFT", "IDEA-2 depends on " + idea3);
        createTestDependency(idea3, "IDEA-1", "OPTIONAL", idea3 + " depends on IDEA-1 - creates cycle");

        // When - Get impact analysis before dependency deletion
        var impactBefore =
                mvc.get().uri("/api/features/{featureCode}/impact", "IDEA-1").exchange();

        // Then - Verify circular dependency exists
        assertThat(impactBefore)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(2); // Should include IDEA-2 and IDEA-3

        // When - Delete the circular dependency IDEA-3 -> IDEA-1 through API
        var deleteDependencyResult = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", idea3, "IDEA-1")
                .exchange();

        // Then - Verify dependency deletion was successful
        assertThat(deleteDependencyResult).hasStatusOk();

        // When - Get impact analysis after dependency deletion
        var impactAfter =
                mvc.get().uri("/api/features/{featureCode}/impact", "IDEA-1").exchange();

        // Then - Verify circular dependency is broken
        // After removing IDEA-100 -> IDEA-1, we have linear chain: IDEA-1 -> IDEA-2 -> IDEA-100
        // Impact analysis for IDEA-1 should show who depends ON IDEA-1, which is now nobody (cycle broken)
        // This is consistent with the original test behavior
        assertThat(impactAfter)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(
                        0); // Should be empty because no one depends on IDEA-1 anymore (consistent with original test)

        // Verify all features still exist
        var getIdea1 = mvc.get().uri("/api/features/{code}", "IDEA-1").exchange();
        assertThat(getIdea1).hasStatusOk();

        var getIdea2 = mvc.get().uri("/api/features/{code}", "IDEA-2").exchange();
        assertThat(getIdea2).hasStatusOk();

        var getIdea3 = mvc.get().uri("/api/features/{code}", idea3).exchange();
        assertThat(getIdea3).hasStatusOk();

        // Verify the circular dependency was actually removed
        var verifyDependencyGone = mvc.delete()
                .uri("/api/features/{featureCode}/dependencies/{dependsOnFeatureCode}", idea3, "IDEA-1")
                .exchange();
        assertThat(verifyDependencyGone).hasStatus(HttpStatus.NOT_FOUND);

        // Verify remaining dependencies are still intact
        // IDEA-1 -> IDEA-2 should still exist
        var idea1Dependencies = mvc.get()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-1")
                .exchange();
        assertThat(idea1Dependencies)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(1);

        assertThat(idea1Dependencies)
                .bodyJson()
                .extractingPath("$[0]")
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.code()).isEqualTo("IDEA-2");
                });

        // IDEA-2 -> IDEA-3 should still exist
        var idea2Dependencies = mvc.get()
                .uri("/api/features/{featureCode}/dependencies", "IDEA-2")
                .exchange();
        assertThat(idea2Dependencies)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(1);

        assertThat(idea2Dependencies)
                .bodyJson()
                .extractingPath("$[0]")
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.code()).isEqualTo(idea3);
                });

        // IDEA-3 should have no dependencies now (circular dependency removed)
        var idea3Dependencies =
                mvc.get().uri("/api/features/{featureCode}/dependencies", idea3).exchange();
        assertThat(idea3Dependencies)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(0);
    }

    // === HELPER METHODS ===

    private String createTestFeature(String productCode, String releaseCode, String title, String description) {
        var payload = String.format(
                """
            {
                "productCode": "%s",
                "releaseCode": "%s",
                "title": "%s",
                "description": "%s",
                "assignedTo": "test-user"
            }
            """,
                productCode, releaseCode, title, description);

        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        // Extract the feature code from the Location header
        String location = createResult.getResponse().getHeader("Location");
        String featureCode = location.substring(location.lastIndexOf("/") + 1);

        // Verify the feature was created successfully by getting it
        var getResult = mvc.get().uri("/api/features/{code}", featureCode).exchange();

        assertThat(getResult).hasStatusOk();

        return featureCode;
    }

    private void createTestDependency(
            String featureCode, String dependsOnFeatureCode, String dependencyType, String notes) {
        var payload = String.format(
                """
            {
                "dependsOnFeatureCode": "%s",
                "dependencyType": "%s",
                "notes": "%s"
            }
            """,
                dependsOnFeatureCode, dependencyType, notes);

        mvc.post()
                .uri("/api/features/{featureCode}/dependencies", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
    }
}

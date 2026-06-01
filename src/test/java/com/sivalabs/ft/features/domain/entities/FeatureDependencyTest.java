package com.sivalabs.ft.features.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.domain.models.DependencyType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FeatureDependencyTest {

    @Test
    void shouldCreateFeatureDependencyWithAllFields() {
        // Given
        Feature feature1 = new Feature();
        feature1.setCode("FEATURE-001");
        feature1.setTitle("Feature 1");

        Feature feature2 = new Feature();
        feature2.setCode("FEATURE-002");
        feature2.setTitle("Feature 2");

        DependencyType dependencyType = DependencyType.HARD;
        String notes = "This is a hard dependency";
        Instant createdAt = Instant.now();

        // When
        FeatureDependency dependency = new FeatureDependency();
        dependency.setFeature(feature1);
        dependency.setDependsOnFeature(feature2);
        dependency.setDependencyType(dependencyType);
        dependency.setNotes(notes);
        dependency.setCreatedAt(createdAt);

        // Then
        assertThat(dependency.getFeature()).isEqualTo(feature1);
        assertThat(dependency.getDependsOnFeature()).isEqualTo(feature2);
        assertThat(dependency.getDependencyType()).isEqualTo(dependencyType);
        assertThat(dependency.getNotes()).isEqualTo(notes);
        assertThat(dependency.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void shouldCreateFeatureDependencyWithMinimalFields() {
        // Given
        Feature feature1 = new Feature();
        feature1.setCode("FEATURE-001");

        Feature feature2 = new Feature();
        feature2.setCode("FEATURE-002");

        DependencyType dependencyType = DependencyType.SOFT;

        // When
        FeatureDependency dependency = new FeatureDependency();
        dependency.setFeature(feature1);
        dependency.setDependsOnFeature(feature2);
        dependency.setDependencyType(dependencyType);

        // Then
        assertThat(dependency.getFeature()).isEqualTo(feature1);
        assertThat(dependency.getDependsOnFeature()).isEqualTo(feature2);
        assertThat(dependency.getDependencyType()).isEqualTo(dependencyType);
        assertThat(dependency.getNotes()).isNull();
        assertThat(dependency.getId()).isNull();
    }

    @Test
    void shouldSetAndGetId() {
        // Given
        FeatureDependency dependency = new FeatureDependency();
        Long id = 1L;

        // When
        dependency.setId(id);

        // Then
        assertThat(dependency.getId()).isEqualTo(id);
    }

    @ParameterizedTest
    @EnumSource(DependencyType.class)
    void shouldHandleAllDependencyTypes(DependencyType dependencyType) {
        // Given
        FeatureDependency dependency = new FeatureDependency();

        // When
        dependency.setDependencyType(dependencyType);

        // Then
        assertThat(dependency.getDependencyType()).isEqualTo(dependencyType);
    }

    @Test
    void shouldSetFeature() {
        // Given
        FeatureDependency dependency = new FeatureDependency();
        Feature feature = new Feature();
        feature.setCode("FEATURE-001");

        // When
        dependency.setFeature(feature);

        // Then
        assertThat(dependency.getFeature()).isEqualTo(feature);
    }

    @Test
    void shouldSetFeatureToNull() {
        // Given
        FeatureDependency dependency = new FeatureDependency();
        Feature feature = new Feature();
        feature.setCode("FEATURE-001");
        dependency.setFeature(feature);

        // When
        dependency.setFeature(null);

        // Then
        assertThat(dependency.getFeature()).isNull();
    }

    @Test
    void shouldSetDependsOnFeature() {
        // Given
        FeatureDependency dependency = new FeatureDependency();
        Feature dependsOnFeature = new Feature();
        dependsOnFeature.setCode("FEATURE-002");

        // When
        dependency.setDependsOnFeature(dependsOnFeature);

        // Then
        assertThat(dependency.getDependsOnFeature()).isEqualTo(dependsOnFeature);
    }

    @Test
    void shouldSetDependsOnFeatureToNull() {
        // Given
        FeatureDependency dependency = new FeatureDependency();
        Feature dependsOnFeature = new Feature();
        dependsOnFeature.setCode("FEATURE-002");
        dependency.setDependsOnFeature(dependsOnFeature);

        // When
        dependency.setDependsOnFeature(null);

        // Then
        assertThat(dependency.getDependsOnFeature()).isNull();
    }

    @Test
    void shouldSetNotesAndCreatedAt() {
        // Given
        FeatureDependency dependency = new FeatureDependency();
        String notes = "Test notes";
        Instant createdAt = Instant.now();

        // When
        dependency.setNotes(notes);
        dependency.setCreatedAt(createdAt);

        // Then
        assertThat(dependency.getNotes()).isEqualTo(notes);
        assertThat(dependency.getCreatedAt()).isEqualTo(createdAt);
    }
}

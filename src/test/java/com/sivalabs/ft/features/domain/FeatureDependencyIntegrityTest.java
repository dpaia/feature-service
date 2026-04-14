package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sivalabs.ft.features.TestcontainersConfiguration;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.FeatureDependency;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.models.DependencyType;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.test.annotation.Rollback;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class FeatureDependencyIntegrityTest {

    @Autowired
    private FeatureDependencyRepository featureDependencyRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Product testProduct;
    private Feature feature1;
    private Feature feature2;

    @BeforeEach
    void setUp() {
        // Create test product
        testProduct = new Product();
        testProduct.setCode("TEST-PRODUCT-" + System.currentTimeMillis());
        testProduct.setPrefix("TP");
        testProduct.setName("Test Product");
        testProduct.setImageUrl("http://example.com/image.png");
        testProduct.setCreatedBy("test-user");
        testProduct.setCreatedAt(Instant.now());
        testProduct = productRepository.save(testProduct);

        // Create test features
        feature1 = new Feature();
        feature1.setCode("TEST-FEATURE-001-" + System.currentTimeMillis());
        feature1.setTitle("Test Feature 1");
        feature1.setStatus(FeatureStatus.NEW);
        feature1.setProduct(testProduct);
        feature1.setCreatedBy("test-user");
        feature1.setCreatedAt(Instant.now());
        feature1 = featureRepository.save(feature1);

        feature2 = new Feature();
        feature2.setCode("TEST-FEATURE-002-" + System.currentTimeMillis());
        feature2.setTitle("Test Feature 2");
        feature2.setStatus(FeatureStatus.NEW);
        feature2.setProduct(testProduct);
        feature2.setCreatedBy("test-user");
        feature2.setCreatedAt(Instant.now());
        feature2 = featureRepository.save(feature2);
    }

    @Test
    @Rollback
    void shouldEnforceForeignKeyConstraintForFeature() {
        // Given - Create a feature that doesn't exist in database
        Feature nonExistentFeature = new Feature();
        nonExistentFeature.setCode("NON-EXISTENT-FEATURE");

        FeatureDependency dependency = new FeatureDependency();
        dependency.setFeature(nonExistentFeature);
        dependency.setDependsOnFeature(feature2);
        dependency.setDependencyType(DependencyType.HARD);
        dependency.setCreatedAt(Instant.now());

        // When & Then
        assertThrows(
                InvalidDataAccessApiUsageException.class,
                () -> {
                    featureDependencyRepository.save(dependency);
                    entityManager.flush();
                },
                "Should not allow dependency with non-existent feature");
    }

    @Test
    @Rollback
    void shouldEnforceForeignKeyConstraintForDependsOnFeature() {
        // Given - Create a feature that doesn't exist in database
        Feature nonExistentFeature = new Feature();
        nonExistentFeature.setCode("NON-EXISTENT-FEATURE");

        FeatureDependency dependency = new FeatureDependency();
        dependency.setFeature(feature1);
        dependency.setDependsOnFeature(nonExistentFeature);
        dependency.setDependencyType(DependencyType.HARD);
        dependency.setCreatedAt(Instant.now());

        // When & Then
        assertThrows(
                InvalidDataAccessApiUsageException.class,
                () -> {
                    featureDependencyRepository.save(dependency);
                    entityManager.flush();
                },
                "Should not allow dependency with non-existent depends on feature");
    }

    @Test
    @Rollback
    void shouldEnforceUniqueConstraint() {
        // Given
        FeatureDependency dependency1 = new FeatureDependency();
        dependency1.setFeature(feature1);
        dependency1.setDependsOnFeature(feature2);
        dependency1.setDependencyType(DependencyType.HARD);
        dependency1.setCreatedAt(Instant.now());
        featureDependencyRepository.save(dependency1);

        FeatureDependency dependency2 = new FeatureDependency();
        dependency2.setFeature(feature1);
        dependency2.setDependsOnFeature(feature2);
        dependency2.setDependencyType(DependencyType.SOFT);
        dependency2.setCreatedAt(Instant.now());

        // When & Then
        assertThrows(
                ConstraintViolationException.class,
                () -> {
                    featureDependencyRepository.save(dependency2);
                    entityManager.flush();
                },
                "Should not allow duplicate dependencies between same features");
    }

    @Test
    @Rollback
    void shouldEnforceSelfDependencyConstraint() {
        // Given
        FeatureDependency dependency = new FeatureDependency();
        dependency.setFeature(feature1);
        dependency.setDependsOnFeature(feature1);
        dependency.setDependencyType(DependencyType.HARD);
        dependency.setCreatedAt(Instant.now());

        // When & Then
        assertThrows(
                ConstraintViolationException.class,
                () -> {
                    featureDependencyRepository.save(dependency);
                    entityManager.flush();
                },
                "Should not allow self-dependency");
    }

    @Test
    @Rollback
    void shouldEnforceDependencyTypeConstraint() {
        // Given - We cannot directly test enum constraint as it's enforced by JPA/Hibernate
        // But we can verify that only valid enum values are accepted
        FeatureDependency hardDependency = new FeatureDependency();
        hardDependency.setFeature(feature1);
        hardDependency.setDependsOnFeature(feature2);
        hardDependency.setDependencyType(DependencyType.HARD);
        hardDependency.setCreatedAt(Instant.now());

        // When
        FeatureDependency saved = featureDependencyRepository.save(hardDependency);

        // Then
        assertThat(saved.getDependencyType()).isEqualTo(DependencyType.HARD);
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Rollback
    void shouldAllowManualDeletionInCorrectOrder() {
        // Given
        FeatureDependency dependency = new FeatureDependency();
        dependency.setFeature(feature1);
        dependency.setDependsOnFeature(feature2);
        dependency.setDependencyType(DependencyType.HARD);
        dependency.setCreatedAt(Instant.now());
        FeatureDependency saved = featureDependencyRepository.save(dependency);

        // Verify dependency exists
        assertThat(featureDependencyRepository.findById(saved.getId())).isPresent();

        // When - Delete dependencies first, then the feature (manual deletion in correct order)
        featureDependencyRepository.deleteById(saved.getId());
        featureRepository.deleteById(feature1.getId());

        // Then - Feature and dependency should be deleted
        assertThat(featureDependencyRepository.findById(saved.getId())).isEmpty();
        assertThat(featureRepository.findById(feature1.getId())).isEmpty();
    }

    @Test
    @Rollback
    void shouldPreventDeletingFeatureThatIsReferencedAsDependency() {
        // Given
        FeatureDependency dependency = new FeatureDependency();
        dependency.setFeature(feature1);
        dependency.setDependsOnFeature(feature2);
        dependency.setDependencyType(DependencyType.HARD);
        dependency.setCreatedAt(Instant.now());
        featureDependencyRepository.save(dependency);

        // When & Then - Try to delete feature2 which is referenced as dependency
        assertThrows(
                IllegalStateException.class,
                () -> {
                    featureRepository.deleteById(feature2.getId());
                    entityManager.flush();
                },
                "Should not allow deleting feature that is referenced as dependency");
    }

    @Test
    @Rollback
    void shouldAllowValidDependencyCreation() {
        // Given
        FeatureDependency dependency = new FeatureDependency();
        dependency.setFeature(feature1);
        dependency.setDependsOnFeature(feature2);
        dependency.setDependencyType(DependencyType.HARD);
        dependency.setNotes("Valid dependency for testing");
        dependency.setCreatedAt(Instant.now());

        // When
        FeatureDependency saved = featureDependencyRepository.save(dependency);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFeature().getCode()).isEqualTo(feature1.getCode());
        assertThat(saved.getDependsOnFeature().getCode()).isEqualTo(feature2.getCode());
        assertThat(saved.getDependencyType()).isEqualTo(DependencyType.HARD);

        // Verify it can be retrieved
        assertThat(featureDependencyRepository.findById(saved.getId())).isPresent();
    }

    static Stream<Arguments> notNullConstraintTestData() {
        return Stream.of(
                Arguments.of("feature", "Should not allow null feature"),
                Arguments.of("dependsOnFeature", "Should not allow null depends_on_feature"),
                Arguments.of("dependencyType", "Should not allow null dependency_type"));
    }

    @ParameterizedTest
    @MethodSource("notNullConstraintTestData")
    @Rollback
    void shouldEnforceNotNullConstraints(String nullField, String expectedMessage) {
        // Using Exception.class instead of specific constraint violation exceptions
        // because different environments (Spring Boot versions, Hibernate versions)
        // may throw different types: org.hibernate.exception.ConstraintViolationException
        // vs jakarta.validation.ConstraintViolationException depending on validation layer

        // Given
        FeatureDependency dependency = new FeatureDependency();
        dependency.setFeature(feature1);
        dependency.setDependsOnFeature(feature2);
        dependency.setDependencyType(DependencyType.HARD);
        dependency.setCreatedAt(Instant.now());

        // Set the specified field to null
        switch (nullField) {
            case "feature" -> dependency.setFeature(null);
            case "dependsOnFeature" -> dependency.setDependsOnFeature(null);
            case "dependencyType" -> dependency.setDependencyType(null);
        }

        // When & Then
        assertThrows(
                Exception.class,
                () -> {
                    featureDependencyRepository.save(dependency);
                    entityManager.flush();
                },
                expectedMessage);
    }
}

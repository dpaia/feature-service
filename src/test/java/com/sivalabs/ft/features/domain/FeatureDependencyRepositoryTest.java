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
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class FeatureDependencyRepositoryTest {

    @Autowired
    private FeatureDependencyRepository featureDependencyRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Feature feature1;
    private Feature feature2;
    private Feature feature3;

    @BeforeEach
    void setUp() {
        // Get existing product or create one
        Product product = productRepository.findByCode("intellij").orElseGet(() -> {
            Product newProduct = new Product();
            newProduct.setCode("test-product");
            newProduct.setPrefix("TP");
            newProduct.setName("Test Product");
            newProduct.setImageUrl("http://example.com/image.png");
            newProduct.setCreatedBy("test-user");
            newProduct.setCreatedAt(Instant.now());
            return productRepository.save(newProduct);
        });

        // Create unique test features with timestamp to avoid duplicates
        long timestamp = System.currentTimeMillis();

        feature1 = featureRepository.findByCode("IDEA-358562").orElseGet(() -> {
            Feature newFeature = new Feature();
            newFeature.setCode("TEST-FEATURE-001-" + timestamp);
            newFeature.setTitle("Test Feature 1");
            newFeature.setStatus(FeatureStatus.NEW);
            newFeature.setProduct(product);
            newFeature.setCreatedBy("test-user");
            newFeature.setCreatedAt(Instant.now());
            return featureRepository.save(newFeature);
        });

        feature2 = featureRepository.findByCode("IDEA-360676").orElseGet(() -> {
            Feature newFeature = new Feature();
            newFeature.setCode("TEST-FEATURE-002-" + timestamp);
            newFeature.setTitle("Test Feature 2");
            newFeature.setStatus(FeatureStatus.NEW);
            newFeature.setProduct(product);
            newFeature.setCreatedBy("test-user");
            newFeature.setCreatedAt(Instant.now());
            return featureRepository.save(newFeature);
        });

        feature3 = featureRepository.findByCode("IDEA-352694").orElseGet(() -> {
            Feature newFeature = new Feature();
            newFeature.setCode("TEST-FEATURE-003-" + timestamp);
            newFeature.setTitle("Test Feature 3");
            newFeature.setStatus(FeatureStatus.NEW);
            newFeature.setProduct(product);
            newFeature.setCreatedBy("test-user");
            newFeature.setCreatedAt(Instant.now());
            return featureRepository.save(newFeature);
        });
    }

    @Test
    void shouldSaveAndRetrieveFeatureDependency() {
        // Given
        FeatureDependency dependency = new FeatureDependency();
        dependency.setFeature(feature1);
        dependency.setDependsOnFeature(feature2);
        dependency.setDependencyType(DependencyType.HARD);
        dependency.setNotes("Critical dependency");
        dependency.setCreatedAt(Instant.now());

        // When
        FeatureDependency saved = featureDependencyRepository.save(dependency);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFeature().getCode()).isEqualTo(feature1.getCode());
        assertThat(saved.getDependsOnFeature().getCode()).isEqualTo(feature2.getCode());
        assertThat(saved.getDependencyType()).isEqualTo(DependencyType.HARD);
        assertThat(saved.getNotes()).isEqualTo("Critical dependency");
    }

    @Test
    void shouldFindDependenciesByFeatureCode() {
        // Given
        createTestDependency(feature1, feature2, DependencyType.HARD);
        createTestDependency(feature1, feature3, DependencyType.SOFT);

        // When
        List<FeatureDependency> dependencies = featureDependencyRepository.findByFeature_Code(feature1.getCode());

        // Then
        assertThat(dependencies).hasSize(2);
        assertThat(dependencies)
                .extracting(dep -> dep.getDependsOnFeature().getCode())
                .containsExactlyInAnyOrder(feature2.getCode(), feature3.getCode());
    }

    @Test
    void shouldFindDependenciesByDependsOnFeatureCode() {
        // Given
        createTestDependency(feature1, feature3, DependencyType.HARD);
        createTestDependency(feature2, feature3, DependencyType.SOFT);

        // When
        List<FeatureDependency> dependencies =
                featureDependencyRepository.findByDependsOnFeature_Code(feature3.getCode());

        // Then
        assertThat(dependencies).hasSize(2);
        assertThat(dependencies)
                .extracting(dep -> dep.getFeature().getCode())
                .containsExactlyInAnyOrder(feature1.getCode(), feature2.getCode());
    }

    @Test
    void shouldFindSpecificDependency() {
        // Given
        createTestDependency(feature1, feature2, DependencyType.HARD);

        // When
        Optional<FeatureDependency> dependency = featureDependencyRepository.findByFeature_CodeAndDependsOnFeature_Code(
                feature1.getCode(), feature2.getCode());

        // Then
        assertThat(dependency).isPresent();
        assertThat(dependency.get().getDependencyType()).isEqualTo(DependencyType.HARD);
    }

    @Test
    void shouldCheckIfDependencyExists() {
        // Given
        createTestDependency(feature1, feature2, DependencyType.HARD);

        // When & Then
        assertThat(featureDependencyRepository.findByFeature_CodeAndDependsOnFeature_Code(
                        feature1.getCode(), feature2.getCode()))
                .isPresent();
        assertThat(featureDependencyRepository.findByFeature_CodeAndDependsOnFeature_Code(
                        feature2.getCode(), feature1.getCode()))
                .isEmpty();
    }

    @Test
    void shouldDeleteDependency() {
        // Given
        FeatureDependency dependency = createTestDependency(feature1, feature2, DependencyType.HARD);

        // When
        featureDependencyRepository.deleteById(dependency.getId());

        // Then
        assertThat(featureDependencyRepository.findById(dependency.getId())).isEmpty();
    }

    @Test
    void shouldPreventDuplicateDependencies() {
        // Given
        createTestDependency(feature1, feature2, DependencyType.HARD);

        // When & Then
        assertThrows(ConstraintViolationException.class, () -> {
            FeatureDependency duplicate = new FeatureDependency();
            duplicate.setFeature(feature1);
            duplicate.setDependsOnFeature(feature2);
            duplicate.setDependencyType(DependencyType.SOFT);
            duplicate.setCreatedAt(Instant.now());
            featureDependencyRepository.save(duplicate);
            entityManager.flush();
        });
    }

    @Test
    void shouldPreventSelfDependency() {
        // Given
        FeatureDependency dependency = new FeatureDependency();
        dependency.setFeature(feature1);
        dependency.setDependsOnFeature(feature1);
        dependency.setDependencyType(DependencyType.HARD);
        dependency.setCreatedAt(Instant.now());

        // When & Then
        assertThrows(ConstraintViolationException.class, () -> {
            featureDependencyRepository.save(dependency);
            entityManager.flush();
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldPreventInvalidFeatureReference() {
        // Given - Create a feature that doesn't exist in database
        Feature nonExistentFeature = new Feature();
        nonExistentFeature.setCode("NON-EXISTENT");

        FeatureDependency dependency = new FeatureDependency();
        dependency.setFeature(nonExistentFeature);
        dependency.setDependsOnFeature(feature2);
        dependency.setDependencyType(DependencyType.HARD);
        dependency.setCreatedAt(Instant.now());

        // When & Then
        assertThrows(InvalidDataAccessApiUsageException.class, () -> {
            featureDependencyRepository.save(dependency);
            entityManager.flush(); // Force constraint check
        });
    }

    private FeatureDependency createTestDependency(Feature feature, Feature dependsOnFeature, DependencyType type) {
        FeatureDependency dependency = new FeatureDependency();
        dependency.setFeature(feature);
        dependency.setDependsOnFeature(dependsOnFeature);
        dependency.setDependencyType(type);
        dependency.setCreatedAt(Instant.now());
        return featureDependencyRepository.save(dependency);
    }
}

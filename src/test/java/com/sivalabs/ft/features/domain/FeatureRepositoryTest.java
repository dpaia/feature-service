package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = {AbstractIT.class})
class FeatureRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void shouldSaveAndFindFeatureWithPlanningFields() {
        // Given
        Product product = createTestProduct();
        Feature feature = createTestFeatureWithPlanningFields(product);

        // When
        Feature savedFeature = featureRepository.save(feature);
        entityManager.flush();
        entityManager.clear();

        // Then
        Optional<Feature> foundFeature = featureRepository.findByCode(savedFeature.getCode());
        assertThat(foundFeature).isPresent();

        Feature retrievedFeature = foundFeature.get();
        assertThat(retrievedFeature.getCode()).isEqualTo("TEST-001");
        assertThat(retrievedFeature.getTitle()).isEqualTo("Test Feature with Planning");
        assertThat(retrievedFeature.getPlannedCompletionDate()).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThat(retrievedFeature.getActualCompletionDate()).isEqualTo(LocalDate.of(2024, 12, 15));
        assertThat(retrievedFeature.getFeaturePlanningStatus()).isEqualTo(FeaturePlanningStatus.DONE);
        assertThat(retrievedFeature.getFeatureOwner()).isEqualTo("john.doe@example.com");
        assertThat(retrievedFeature.getBlockageReason()).isEqualTo("None");
    }

    @Test
    void shouldSaveFeatureWithNullPlanningFields() {
        // Given
        Product product = createTestProduct();
        Feature feature = createTestFeatureWithNullPlanningFields(product);

        // When
        Feature savedFeature = featureRepository.save(feature);
        entityManager.flush();
        entityManager.clear();

        // Then
        Optional<Feature> foundFeature = featureRepository.findByCode(savedFeature.getCode());
        assertThat(foundFeature).isPresent();

        Feature retrievedFeature = foundFeature.get();
        assertThat(retrievedFeature.getCode()).isEqualTo("TEST-002");
        assertThat(retrievedFeature.getPlannedCompletionDate()).isNull();
        assertThat(retrievedFeature.getActualCompletionDate()).isNull();
        assertThat(retrievedFeature.getFeaturePlanningStatus()).isNull();
        assertThat(retrievedFeature.getFeatureOwner()).isNull();
        assertThat(retrievedFeature.getBlockageReason()).isNull();
    }

    @Test
    void shouldUpdatePlanningFields() {
        // Given
        Product product = createTestProduct();
        Feature feature = createTestFeatureWithNullPlanningFields(product);
        Feature savedFeature = featureRepository.save(feature);
        entityManager.flush();

        // When
        savedFeature.setPlannedCompletionDate(LocalDate.of(2025, 1, 15));
        savedFeature.setFeaturePlanningStatus(FeaturePlanningStatus.IN_PROGRESS);
        savedFeature.setFeatureOwner("jane.smith@example.com");
        savedFeature.setBlockageReason("Waiting for approval");
        savedFeature.setUpdatedAt(Instant.now());

        featureRepository.save(savedFeature);
        entityManager.flush();
        entityManager.clear();

        // Then
        Optional<Feature> foundFeature = featureRepository.findByCode(savedFeature.getCode());
        assertThat(foundFeature).isPresent();

        Feature updatedFeature = foundFeature.get();
        assertThat(updatedFeature.getPlannedCompletionDate()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(updatedFeature.getFeaturePlanningStatus()).isEqualTo(FeaturePlanningStatus.IN_PROGRESS);
        assertThat(updatedFeature.getFeatureOwner()).isEqualTo("jane.smith@example.com");
        assertThat(updatedFeature.getBlockageReason()).isEqualTo("Waiting for approval");
    }

    @Test
    void shouldHandleAllPlanningStatusValues() {
        // Given
        Product product = createTestProduct();

        for (FeaturePlanningStatus status : FeaturePlanningStatus.values()) {
            // When
            Feature feature = new Feature();
            feature.setCode("TEST-" + status.name());
            feature.setTitle("Test Feature " + status.name());
            feature.setDescription("Test Description");
            feature.setStatus(FeatureStatus.NEW);
            feature.setFeaturePlanningStatus(status);
            feature.setProduct(product);
            feature.setCreatedBy("test");
            feature.setCreatedAt(Instant.now());

            Feature savedFeature = featureRepository.save(feature);
            entityManager.flush();
            entityManager.clear();

            // Then
            Optional<Feature> foundFeature = featureRepository.findByCode(savedFeature.getCode());
            assertThat(foundFeature).isPresent();
            assertThat(foundFeature.get().getFeaturePlanningStatus()).isEqualTo(status);
        }
    }

    private Product createTestProduct() {
        Product product = new Product();
        product.setCode("TEST_PRODUCT");
        product.setPrefix("TEST");
        product.setName("Test Product");
        product.setDescription("Test Product Description");
        product.setImageUrl("http://example.com/image.png");
        product.setCreatedBy("test");
        product.setCreatedAt(Instant.now());
        return productRepository.save(product);
    }

    private Feature createTestFeatureWithPlanningFields(Product product) {
        Feature feature = new Feature();
        feature.setCode("TEST-001");
        feature.setTitle("Test Feature with Planning");
        feature.setDescription("Test Description");
        feature.setStatus(FeatureStatus.IN_PROGRESS);
        feature.setPlannedCompletionDate(LocalDate.of(2024, 12, 31));
        feature.setActualCompletionDate(LocalDate.of(2024, 12, 15));
        feature.setFeaturePlanningStatus(FeaturePlanningStatus.DONE);
        feature.setFeatureOwner("john.doe@example.com");
        feature.setBlockageReason("None");
        feature.setProduct(product);
        feature.setCreatedBy("test");
        feature.setCreatedAt(Instant.now());
        return feature;
    }

    private Feature createTestFeatureWithNullPlanningFields(Product product) {
        Feature feature = new Feature();
        feature.setCode("TEST-002");
        feature.setTitle("Test Feature without Planning");
        feature.setDescription("Test Description");
        feature.setStatus(FeatureStatus.NEW);
        feature.setProduct(product);
        feature.setCreatedBy("test");
        feature.setCreatedAt(Instant.now());
        return feature;
    }
}

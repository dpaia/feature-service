package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class FeatureRepositoryTest extends AbstractIT {

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void shouldSaveFeatureWithPlanningFields() {
        Product product = productRepository.findByCode("intellij").orElseThrow();

        Feature feature = new Feature();
        feature.setProduct(product);
        feature.setCode("TEST-PLAN-001");
        feature.setTitle("Feature with Planning Data");
        feature.setDescription("Testing planning fields persistence");
        feature.setStatus(FeatureStatus.NEW);
        feature.setPlannedCompletionDate(LocalDate.of(2025, 12, 31));
        feature.setFeaturePlanningStatus(FeaturePlanningStatus.NOT_STARTED);
        feature.setFeatureOwner("owner@example.com");
        feature.setCreatedBy("test@example.com");
        feature.setCreatedAt(Instant.now());

        Feature savedFeature = featureRepository.save(feature);
        Feature retrievedFeature = featureRepository.findByCode("TEST-PLAN-001").orElseThrow();

        assertThat(savedFeature.getId()).isNotNull();
        assertThat(retrievedFeature.getCode()).isEqualTo("TEST-PLAN-001");
        assertThat(retrievedFeature.getTitle()).isEqualTo("Feature with Planning Data");
        assertThat(retrievedFeature.getPlannedCompletionDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(retrievedFeature.getFeaturePlanningStatus()).isEqualTo(FeaturePlanningStatus.NOT_STARTED);
        assertThat(retrievedFeature.getFeatureOwner()).isEqualTo("owner@example.com");
        assertThat(retrievedFeature.getBlockageReason()).isNull();
        assertThat(retrievedFeature.getActualCompletionDate()).isNull();
    }

    @Test
    void shouldUpdateFeatureWithPlanningFields() {
        Product product = productRepository.findByCode("intellij").orElseThrow();

        Feature feature = new Feature();
        feature.setProduct(product);
        feature.setCode("TEST-PLAN-002");
        feature.setTitle("Feature to Update");
        feature.setDescription("Initial description");
        feature.setStatus(FeatureStatus.NEW);
        feature.setFeaturePlanningStatus(FeaturePlanningStatus.NOT_STARTED);
        feature.setPlannedCompletionDate(LocalDate.of(2025, 11, 30));
        feature.setCreatedBy("test@example.com");
        feature.setCreatedAt(Instant.now());
        featureRepository.save(feature);

        Feature existingFeature = featureRepository.findByCode("TEST-PLAN-002").orElseThrow();
        existingFeature.setFeaturePlanningStatus(FeaturePlanningStatus.IN_PROGRESS);
        existingFeature.setFeatureOwner("new.owner@example.com");
        existingFeature.setPlannedCompletionDate(LocalDate.of(2025, 12, 15));
        existingFeature.setUpdatedBy("updater@example.com");
        existingFeature.setUpdatedAt(Instant.now());
        featureRepository.save(existingFeature);

        Feature updatedFeature = featureRepository.findByCode("TEST-PLAN-002").orElseThrow();
        assertThat(updatedFeature.getFeaturePlanningStatus()).isEqualTo(FeaturePlanningStatus.IN_PROGRESS);
        assertThat(updatedFeature.getFeatureOwner()).isEqualTo("new.owner@example.com");
        assertThat(updatedFeature.getPlannedCompletionDate()).isEqualTo(LocalDate.of(2025, 12, 15));
        assertThat(updatedFeature.getUpdatedBy()).isEqualTo("updater@example.com");
        assertThat(updatedFeature.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldSaveFeatureWithBlockedStatus() {
        Product product = productRepository.findByCode("intellij").orElseThrow();

        Feature feature = new Feature();
        feature.setProduct(product);
        feature.setCode("TEST-PLAN-003");
        feature.setTitle("Blocked Feature");
        feature.setDescription("Feature blocked by dependencies");
        feature.setStatus(FeatureStatus.ON_HOLD);
        feature.setFeaturePlanningStatus(FeaturePlanningStatus.BLOCKED);
        feature.setBlockageReason("Waiting for external API approval");
        feature.setFeatureOwner("blocked.owner@example.com");
        feature.setPlannedCompletionDate(LocalDate.of(2025, 10, 31));
        feature.setCreatedBy("test@example.com");
        feature.setCreatedAt(Instant.now());

        featureRepository.save(feature);
        Feature retrievedFeature = featureRepository.findByCode("TEST-PLAN-003").orElseThrow();

        assertThat(retrievedFeature.getFeaturePlanningStatus()).isEqualTo(FeaturePlanningStatus.BLOCKED);
        assertThat(retrievedFeature.getBlockageReason()).isEqualTo("Waiting for external API approval");
        assertThat(retrievedFeature.getFeatureOwner()).isEqualTo("blocked.owner@example.com");
    }

    @Test
    void shouldSaveFeatureWithDoneStatusAndActualDate() {
        Product product = productRepository.findByCode("intellij").orElseThrow();

        Feature feature = new Feature();
        feature.setProduct(product);
        feature.setCode("TEST-PLAN-004");
        feature.setTitle("Completed Feature");
        feature.setDescription("Feature completed successfully");
        feature.setStatus(FeatureStatus.RELEASED);
        feature.setFeaturePlanningStatus(FeaturePlanningStatus.DONE);
        feature.setPlannedCompletionDate(LocalDate.of(2025, 10, 15));
        feature.setActualCompletionDate(LocalDate.of(2025, 10, 11));
        feature.setFeatureOwner("done.owner@example.com");
        feature.setCreatedBy("test@example.com");
        feature.setCreatedAt(Instant.now());

        featureRepository.save(feature);
        Feature retrievedFeature = featureRepository.findByCode("TEST-PLAN-004").orElseThrow();

        assertThat(retrievedFeature.getFeaturePlanningStatus()).isEqualTo(FeaturePlanningStatus.DONE);
        assertThat(retrievedFeature.getActualCompletionDate()).isEqualTo(LocalDate.of(2025, 10, 11));
        assertThat(retrievedFeature.getPlannedCompletionDate()).isEqualTo(LocalDate.of(2025, 10, 15));
        assertThat(retrievedFeature.getActualCompletionDate()).isBefore(retrievedFeature.getPlannedCompletionDate());
    }

    @Test
    void shouldAllowNullPlanningFieldsInDatabase() {
        Product product = productRepository.findByCode("intellij").orElseThrow();

        Feature feature = new Feature();
        feature.setProduct(product);
        feature.setCode("TEST-PLAN-005");
        feature.setTitle("Feature Without Planning");
        feature.setDescription("Feature without any planning data");
        feature.setStatus(FeatureStatus.NEW);
        feature.setCreatedBy("test@example.com");
        feature.setCreatedAt(Instant.now());

        featureRepository.save(feature);
        Feature retrievedFeature = featureRepository.findByCode("TEST-PLAN-005").orElseThrow();

        assertThat(retrievedFeature.getPlannedCompletionDate()).isNull();
        assertThat(retrievedFeature.getActualCompletionDate()).isNull();
        assertThat(retrievedFeature.getFeaturePlanningStatus()).isNull();
        assertThat(retrievedFeature.getFeatureOwner()).isNull();
        assertThat(retrievedFeature.getBlockageReason()).isNull();
    }

    @Test
    @Transactional
    void shouldDeleteFeatureWithPlanningFields() {
        Product product = productRepository.findByCode("intellij").orElseThrow();

        Feature feature = new Feature();
        feature.setProduct(product);
        feature.setCode("TEST-PLAN-006");
        feature.setTitle("Feature to Delete");
        feature.setDescription("Feature to test deletion");
        feature.setStatus(FeatureStatus.NEW);
        feature.setFeaturePlanningStatus(FeaturePlanningStatus.IN_PROGRESS);
        feature.setPlannedCompletionDate(LocalDate.of(2025, 12, 31));
        feature.setFeatureOwner("delete.owner@example.com");
        feature.setCreatedBy("test@example.com");
        feature.setCreatedAt(Instant.now());
        featureRepository.save(feature);

        featureRepository.deleteByCode("TEST-PLAN-006");

        assertThat(featureRepository.findByCode("TEST-PLAN-006")).isEmpty();
    }

    @Test
    void shouldUpdateOnlyPlanningFieldsWithoutAffectingOthers() {
        Product product = productRepository.findByCode("intellij").orElseThrow();

        Feature feature = new Feature();
        feature.setProduct(product);
        feature.setCode("TEST-PLAN-007");
        feature.setTitle("Original Title");
        feature.setDescription("Original Description");
        feature.setStatus(FeatureStatus.NEW);
        feature.setAssignedTo("original@example.com");
        feature.setCreatedBy("test@example.com");
        feature.setCreatedAt(Instant.now());
        featureRepository.save(feature);

        Feature existingFeature = featureRepository.findByCode("TEST-PLAN-007").orElseThrow();
        existingFeature.setFeaturePlanningStatus(FeaturePlanningStatus.IN_PROGRESS);
        existingFeature.setFeatureOwner("planning.owner@example.com");
        existingFeature.setPlannedCompletionDate(LocalDate.of(2025, 11, 30));
        featureRepository.save(existingFeature);

        Feature updatedFeature = featureRepository.findByCode("TEST-PLAN-007").orElseThrow();
        assertThat(updatedFeature.getTitle()).isEqualTo("Original Title");
        assertThat(updatedFeature.getDescription()).isEqualTo("Original Description");
        assertThat(updatedFeature.getStatus()).isEqualTo(FeatureStatus.NEW);
        assertThat(updatedFeature.getAssignedTo()).isEqualTo("original@example.com");

        assertThat(updatedFeature.getFeaturePlanningStatus()).isEqualTo(FeaturePlanningStatus.IN_PROGRESS);
        assertThat(updatedFeature.getFeatureOwner()).isEqualTo("planning.owner@example.com");
        assertThat(updatedFeature.getPlannedCompletionDate()).isEqualTo(LocalDate.of(2025, 11, 30));
    }
}

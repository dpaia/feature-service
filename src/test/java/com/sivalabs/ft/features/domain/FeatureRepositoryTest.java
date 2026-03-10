package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.TestcontainersConfiguration;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class FeatureRepositoryTest {

    @Autowired
    private FeatureRepository featureRepository;

    @Test
    void shouldFindFeatureByCode() {
        var feature = featureRepository.findByCode("IDEA-1");
        assertThat(feature).isPresent();
        assertThat(feature.get().getCode()).isEqualTo("IDEA-1");
    }

    @Test
    void shouldPersistPlanningFields() {
        Feature feature = featureRepository.findByCode("IDEA-1").orElseThrow();
        Instant planned = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        Instant actual = Instant.now().plus(45, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);

        feature.setPlannedCompletionAt(planned);
        feature.setActualCompletionAt(actual);
        feature.setFeaturePlanningStatus(FeaturePlanningStatus.IN_PROGRESS);
        feature.setFeatureOwner("planning.owner");
        feature.setBlockageReason(null);
        featureRepository.save(feature);

        Feature saved = featureRepository.findByCode("IDEA-1").orElseThrow();
        assertThat(saved.getPlannedCompletionAt()).isEqualTo(planned);
        assertThat(saved.getActualCompletionAt()).isEqualTo(actual);
        assertThat(saved.getFeaturePlanningStatus()).isEqualTo(FeaturePlanningStatus.IN_PROGRESS);
        assertThat(saved.getFeatureOwner()).isEqualTo("planning.owner");
        assertThat(saved.getBlockageReason()).isNull();
    }

    @Test
    void shouldPersistBlockedStatusWithReason() {
        Feature feature = featureRepository.findByCode("IDEA-1").orElseThrow();
        feature.setFeaturePlanningStatus(FeaturePlanningStatus.BLOCKED);
        feature.setBlockageReason("Waiting for API contract");
        featureRepository.save(feature);

        Feature saved = featureRepository.findByCode("IDEA-1").orElseThrow();
        assertThat(saved.getFeaturePlanningStatus()).isEqualTo(FeaturePlanningStatus.BLOCKED);
        assertThat(saved.getBlockageReason()).isEqualTo("Waiting for API contract");
    }

    @Test
    void shouldPersistDoneStatus() {
        Feature feature = featureRepository.findByCode("GO-3").orElseThrow();
        Instant completedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        feature.setFeaturePlanningStatus(FeaturePlanningStatus.DONE);
        feature.setActualCompletionAt(completedAt);
        featureRepository.save(feature);

        Feature saved = featureRepository.findByCode("GO-3").orElseThrow();
        assertThat(saved.getFeaturePlanningStatus()).isEqualTo(FeaturePlanningStatus.DONE);
        assertThat(saved.getActualCompletionAt()).isEqualTo(completedAt);
    }

    @Test
    void shouldSupportNullPlanningFields() {
        Feature feature = featureRepository.findByCode("IDEA-1").orElseThrow();
        feature.setPlannedCompletionAt(null);
        feature.setActualCompletionAt(null);
        feature.setFeaturePlanningStatus(null);
        feature.setFeatureOwner(null);
        feature.setBlockageReason(null);
        featureRepository.save(feature);

        Feature saved = featureRepository.findByCode("IDEA-1").orElseThrow();
        assertThat(saved.getPlannedCompletionAt()).isNull();
        assertThat(saved.getActualCompletionAt()).isNull();
        assertThat(saved.getFeaturePlanningStatus()).isNull();
        assertThat(saved.getFeatureOwner()).isNull();
        assertThat(saved.getBlockageReason()).isNull();
    }
}

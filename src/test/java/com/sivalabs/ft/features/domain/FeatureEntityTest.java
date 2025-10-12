package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FeatureEntityTest {

    @Test
    void shouldCreateFeatureWithPlanningFields() {
        // Given
        Feature feature = new Feature();
        LocalDate plannedDate = LocalDate.of(2024, 12, 31);
        LocalDate actualDate = LocalDate.of(2024, 12, 15);

        // When
        feature.setCode("PROJ-123");
        feature.setTitle("Test Feature");
        feature.setDescription("Test Description");
        feature.setStatus(FeatureStatus.IN_PROGRESS);
        feature.setPlannedCompletionDate(plannedDate);
        feature.setActualCompletionDate(actualDate);
        feature.setFeaturePlanningStatus(FeaturePlanningStatus.IN_PROGRESS);
        feature.setFeatureOwner("john.doe@example.com");
        feature.setBlockageReason("Waiting for dependency");
        feature.setCreatedBy("admin");
        feature.setCreatedAt(Instant.now());

        // Then
        assertThat(feature.getCode()).isEqualTo("PROJ-123");
        assertThat(feature.getTitle()).isEqualTo("Test Feature");
        assertThat(feature.getDescription()).isEqualTo("Test Description");
        assertThat(feature.getStatus()).isEqualTo(FeatureStatus.IN_PROGRESS);
        assertThat(feature.getPlannedCompletionDate()).isEqualTo(plannedDate);
        assertThat(feature.getActualCompletionDate()).isEqualTo(actualDate);
        assertThat(feature.getFeaturePlanningStatus()).isEqualTo(FeaturePlanningStatus.IN_PROGRESS);
        assertThat(feature.getFeatureOwner()).isEqualTo("john.doe@example.com");
        assertThat(feature.getBlockageReason()).isEqualTo("Waiting for dependency");
        assertThat(feature.getCreatedBy()).isEqualTo("admin");
        assertThat(feature.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldHandleNullPlanningFields() {
        // Given
        Feature feature = new Feature();

        // When
        feature.setCode("PROJ-124");
        feature.setTitle("Test Feature 2");
        feature.setStatus(FeatureStatus.NEW);
        feature.setPlannedCompletionDate(null);
        feature.setActualCompletionDate(null);
        feature.setFeaturePlanningStatus(null);
        feature.setFeatureOwner(null);
        feature.setBlockageReason(null);

        // Then
        assertThat(feature.getCode()).isEqualTo("PROJ-124");
        assertThat(feature.getTitle()).isEqualTo("Test Feature 2");
        assertThat(feature.getStatus()).isEqualTo(FeatureStatus.NEW);
        assertThat(feature.getPlannedCompletionDate()).isNull();
        assertThat(feature.getActualCompletionDate()).isNull();
        assertThat(feature.getFeaturePlanningStatus()).isNull();
        assertThat(feature.getFeatureOwner()).isNull();
        assertThat(feature.getBlockageReason()).isNull();
    }

    @Test
    void shouldTestAllPlanningStatusValues() {
        // Given
        Feature feature = new Feature();

        // When & Then
        for (FeaturePlanningStatus status : FeaturePlanningStatus.values()) {
            feature.setFeaturePlanningStatus(status);
            assertThat(feature.getFeaturePlanningStatus()).isEqualTo(status);
        }
    }
}

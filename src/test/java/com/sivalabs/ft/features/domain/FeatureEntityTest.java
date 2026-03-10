package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FeatureEntityTest {

    @Test
    void shouldSetAndGetPlanningFields() {
        Feature feature = new Feature();
        Instant planned = Instant.parse("2026-06-01T00:00:00Z");
        Instant actual = Instant.parse("2026-06-15T00:00:00Z");

        feature.setPlannedCompletionAt(planned);
        feature.setActualCompletionAt(actual);
        feature.setFeaturePlanningStatus(FeaturePlanningStatus.IN_PROGRESS);
        feature.setFeatureOwner("owner.user");
        feature.setBlockageReason("Waiting for dependencies");

        assertThat(feature.getPlannedCompletionAt()).isEqualTo(planned);
        assertThat(feature.getActualCompletionAt()).isEqualTo(actual);
        assertThat(feature.getFeaturePlanningStatus()).isEqualTo(FeaturePlanningStatus.IN_PROGRESS);
        assertThat(feature.getFeatureOwner()).isEqualTo("owner.user");
        assertThat(feature.getBlockageReason()).isEqualTo("Waiting for dependencies");
    }

    @Test
    void shouldHaveNullPlanningFieldsByDefault() {
        Feature feature = new Feature();

        assertThat(feature.getPlannedCompletionAt()).isNull();
        assertThat(feature.getActualCompletionAt()).isNull();
        assertThat(feature.getFeaturePlanningStatus()).isNull();
        assertThat(feature.getFeatureOwner()).isNull();
        assertThat(feature.getBlockageReason()).isNull();
    }

    @Test
    void shouldSupportAllPlanningStatusValues() {
        Feature feature = new Feature();

        for (FeaturePlanningStatus status : FeaturePlanningStatus.values()) {
            feature.setFeaturePlanningStatus(status);
            assertThat(feature.getFeaturePlanningStatus()).isEqualTo(status);
        }
    }

    @Test
    void shouldSetBlockedStatus() {
        Feature feature = new Feature();
        feature.setStatus(FeatureStatus.ON_HOLD);
        feature.setFeaturePlanningStatus(FeaturePlanningStatus.BLOCKED);
        feature.setBlockageReason("External dependency not ready");

        assertThat(feature.getFeaturePlanningStatus()).isEqualTo(FeaturePlanningStatus.BLOCKED);
        assertThat(feature.getBlockageReason()).isEqualTo("External dependency not ready");
    }
}

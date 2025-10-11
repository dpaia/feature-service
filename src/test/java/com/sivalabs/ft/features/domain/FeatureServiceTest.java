package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.domain.Commands.UpdateFeatureCommand;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeatureServiceTest extends AbstractIT {

    @Autowired
    private FeatureService featureService;

    @Test
    void shouldUpdateFeatureWithPlanningFields() {
        String featureCode = "IDEA-1";
        LocalDate plannedDate = LocalDate.of(2025, 12, 31);

        var cmd = new UpdateFeatureCommand(
                featureCode,
                "Updated Feature With Planning",
                "Feature with planning data",
                FeatureStatus.IN_PROGRESS,
                null,
                "assigned@example.com",
                plannedDate,
                null,
                FeaturePlanningStatus.IN_PROGRESS,
                "planner@example.com",
                null,
                "updater@example.com");

        featureService.updateFeature(cmd);

        FeatureDto feature = featureService.findFeatureByCode(null, featureCode).orElseThrow();
        assertThat(feature.title()).isEqualTo("Updated Feature With Planning");
        assertThat(feature.plannedCompletionDate()).isEqualTo(plannedDate);
        assertThat(feature.featurePlanningStatus()).isEqualTo(FeaturePlanningStatus.IN_PROGRESS);
        assertThat(feature.featureOwner()).isEqualTo("planner@example.com");
        assertThat(feature.blockageReason()).isNull();
        assertThat(feature.actualCompletionDate()).isNull();
    }

    @Test
    void shouldUpdateFeatureToBlockedWithReason() {
        String featureCode = "IDEA-2";
        LocalDate plannedDate = LocalDate.of(2025, 11, 30);

        var cmd = new UpdateFeatureCommand(
                featureCode,
                "Blocked Feature",
                "Feature blocked by dependencies",
                FeatureStatus.ON_HOLD,
                null,
                "blocked@example.com",
                plannedDate,
                null,
                FeaturePlanningStatus.BLOCKED,
                "owner@example.com",
                "Waiting for external API approval",
                "updater@example.com");

        featureService.updateFeature(cmd);

        FeatureDto feature = featureService.findFeatureByCode(null, featureCode).orElseThrow();
        assertThat(feature.featurePlanningStatus()).isEqualTo(FeaturePlanningStatus.BLOCKED);
        assertThat(feature.blockageReason()).isEqualTo("Waiting for external API approval");
        assertThat(feature.featureOwner()).isEqualTo("owner@example.com");
    }

    @Test
    void shouldUpdateFeatureToDoneWithActualDate() {
        String featureCode = "IDEA-3";
        LocalDate plannedDate = LocalDate.of(2025, 10, 31);
        LocalDate actualDate = LocalDate.of(2025, 10, 11);

        var cmd = new UpdateFeatureCommand(
                featureCode,
                "Completed Feature",
                "Feature completed successfully",
                FeatureStatus.RELEASED,
                null,
                "completed@example.com",
                plannedDate,
                actualDate,
                FeaturePlanningStatus.DONE,
                "done.owner@example.com",
                null,
                "updater@example.com");

        featureService.updateFeature(cmd);

        FeatureDto feature = featureService.findFeatureByCode(null, featureCode).orElseThrow();
        assertThat(feature.featurePlanningStatus()).isEqualTo(FeaturePlanningStatus.DONE);
        assertThat(feature.actualCompletionDate()).isEqualTo(actualDate);
        assertThat(feature.plannedCompletionDate()).isEqualTo(plannedDate);
    }

    @Test
    void shouldUpdateFeatureToNotStartedStatus() {
        String featureCode = "IDEA-1";
        LocalDate plannedDate = LocalDate.of(2026, 1, 15);

        var cmd = new UpdateFeatureCommand(
                featureCode,
                "Future Feature",
                "Feature not yet started",
                FeatureStatus.NEW,
                null,
                "future@example.com",
                plannedDate,
                null,
                FeaturePlanningStatus.NOT_STARTED,
                "future.owner@example.com",
                null,
                "updater@example.com");

        featureService.updateFeature(cmd);

        FeatureDto feature = featureService.findFeatureByCode(null, featureCode).orElseThrow();
        assertThat(feature.featurePlanningStatus()).isEqualTo(FeaturePlanningStatus.NOT_STARTED);
        assertThat(feature.plannedCompletionDate()).isEqualTo(plannedDate);
        assertThat(feature.actualCompletionDate()).isNull();
    }

    @Test
    void shouldAllowNullPlanningFieldsInUpdate() {
        String featureCode = "IDEA-2";

        var cmd = new UpdateFeatureCommand(
                featureCode,
                "Feature Without Planning",
                "Feature without any planning data",
                FeatureStatus.NEW,
                null,
                "noplanning@example.com",
                null,
                null,
                null,
                null,
                null,
                "updater@example.com");

        featureService.updateFeature(cmd);

        FeatureDto feature = featureService.findFeatureByCode(null, featureCode).orElseThrow();
        assertThat(feature.title()).isEqualTo("Feature Without Planning");
        assertThat(feature.plannedCompletionDate()).isNull();
        assertThat(feature.actualCompletionDate()).isNull();
        assertThat(feature.featurePlanningStatus()).isNull();
        assertThat(feature.featureOwner()).isNull();
        assertThat(feature.blockageReason()).isNull();
    }

    @Test
    void shouldUpdateOnlyPlanningFieldsWithoutAffectingOthers() {
        String featureCode = "IDEA-3";
        var initialCmd = new UpdateFeatureCommand(
                featureCode,
                "Original Title",
                "Original Description",
                FeatureStatus.NEW,
                null,
                "original@example.com",
                null,
                null,
                null,
                null,
                null,
                "creator@example.com");
        featureService.updateFeature(initialCmd);

        LocalDate plannedDate = LocalDate.of(2025, 11, 30);
        var updateCmd = new UpdateFeatureCommand(
                featureCode,
                "Original Title", // Keep same
                "Original Description", // Keep same
                FeatureStatus.NEW, // Keep same
                null,
                "original@example.com", // Keep same
                plannedDate,
                null,
                FeaturePlanningStatus.IN_PROGRESS,
                "planning.owner@example.com",
                null,
                "updater@example.com");
        featureService.updateFeature(updateCmd);

        FeatureDto feature = featureService.findFeatureByCode(null, featureCode).orElseThrow();
        assertThat(feature.title()).isEqualTo("Original Title");
        assertThat(feature.description()).isEqualTo("Original Description");
        assertThat(feature.status()).isEqualTo(FeatureStatus.NEW);
        assertThat(feature.assignedTo()).isEqualTo("original@example.com");
        assertThat(feature.featurePlanningStatus()).isEqualTo(FeaturePlanningStatus.IN_PROGRESS);
        assertThat(feature.featureOwner()).isEqualTo("planning.owner@example.com");
        assertThat(feature.plannedCompletionDate()).isEqualTo(plannedDate);
    }
}

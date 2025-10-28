package com.sivalabs.ft.features.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FeaturePlanningStatusTest {

    @Test
    void shouldHaveAllExpectedValues() {
        FeaturePlanningStatus[] values = FeaturePlanningStatus.values();

        assertThat(values).hasSize(4);
        assertThat(values)
                .contains(
                        FeaturePlanningStatus.NOT_STARTED,
                        FeaturePlanningStatus.IN_PROGRESS,
                        FeaturePlanningStatus.DONE,
                        FeaturePlanningStatus.BLOCKED);
    }

    @Test
    void shouldConvertFromString() {
        assertThat(FeaturePlanningStatus.valueOf("NOT_STARTED")).isEqualTo(FeaturePlanningStatus.NOT_STARTED);
        assertThat(FeaturePlanningStatus.valueOf("IN_PROGRESS")).isEqualTo(FeaturePlanningStatus.IN_PROGRESS);
        assertThat(FeaturePlanningStatus.valueOf("DONE")).isEqualTo(FeaturePlanningStatus.DONE);
        assertThat(FeaturePlanningStatus.valueOf("BLOCKED")).isEqualTo(FeaturePlanningStatus.BLOCKED);
    }

    @Test
    void shouldHaveCorrectNames() {
        assertThat(FeaturePlanningStatus.NOT_STARTED.name()).isEqualTo("NOT_STARTED");
        assertThat(FeaturePlanningStatus.IN_PROGRESS.name()).isEqualTo("IN_PROGRESS");
        assertThat(FeaturePlanningStatus.DONE.name()).isEqualTo("DONE");
        assertThat(FeaturePlanningStatus.BLOCKED.name()).isEqualTo("BLOCKED");
    }
}

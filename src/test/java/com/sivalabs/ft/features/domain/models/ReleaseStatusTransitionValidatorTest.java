package com.sivalabs.ft.features.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReleaseStatusTransitionValidatorTest {

    @Test
    void testValidTransition_PlannedToDraft() {
        assertThat(ReleaseStatus.PLANNED.canTransitionTo(ReleaseStatus.DRAFT)).isTrue();
    }

    @Test
    void testInvalidTransition_InProgressToDraft() {
        assertThat(ReleaseStatus.IN_PROGRESS.canTransitionTo(ReleaseStatus.DRAFT))
                .isFalse();
    }

    @Test
    void testInvalidTransition_CompletedToAnyState() {
        assertThat(ReleaseStatus.COMPLETED.canTransitionTo(ReleaseStatus.DRAFT)).isFalse();
        assertThat(ReleaseStatus.COMPLETED.canTransitionTo(ReleaseStatus.PLANNED))
                .isFalse();
        assertThat(ReleaseStatus.COMPLETED.canTransitionTo(ReleaseStatus.CANCELLED))
                .isFalse();
    }

    @Test
    void testNullNewStatus() {
        assertThat(ReleaseStatus.DRAFT.canTransitionTo(null)).isFalse();
    }
}

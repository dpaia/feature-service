package com.sivalabs.ft.features.domain;

import static com.sivalabs.ft.features.domain.models.ReleaseStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReleaseStatusTransitionValidator Unit Tests")
class ReleaseStatusTransitionValidatorTest {

    // Valid transition tests
    @Test
    @DisplayName("Should allow valid transition from DRAFT to PLANNED")
    void testValidTransition_DraftToPlanned() {
        boolean result = DRAFT.canTransitionTo(PLANNED);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow valid transition from DRAFT to CANCELLED")
    void testValidTransition_DraftToCancelled() {
        boolean result = DRAFT.canTransitionTo(CANCELLED);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow valid transition from PLANNED to IN_PROGRESS")
    void testValidTransition_PlannedToInProgress() {
        boolean result = PLANNED.canTransitionTo(IN_PROGRESS);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow valid transition from PLANNED to CANCELLED")
    void testValidTransition_PlannedToCancelled() {
        boolean result = PLANNED.canTransitionTo(CANCELLED);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow valid transition from PLANNED to DRAFT (rollback)")
    void testValidTransition_PlannedToDraft() {
        boolean result = PLANNED.canTransitionTo(DRAFT);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow valid transition from IN_PROGRESS to COMPLETED")
    void testValidTransition_InProgressToCompleted() {
        boolean result = IN_PROGRESS.canTransitionTo(COMPLETED);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow valid transition from IN_PROGRESS to PLANNED")
    void testValidTransition_InProgressToPlanned() {
        boolean result = IN_PROGRESS.canTransitionTo(PLANNED);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow valid transition from IN_PROGRESS to CANCELLED")
    void testValidTransition_InProgressToCancelled() {
        boolean result = IN_PROGRESS.canTransitionTo(CANCELLED);
        assertThat(result).isTrue();
    }

    // Invalid transition tests - Terminal states
    @Test
    @DisplayName("Should reject transition from COMPLETED to PLANNED (terminal state)")
    void testInvalidTransition_CompletedToPlanned() {
        boolean result = COMPLETED.canTransitionTo(PLANNED);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject transition from COMPLETED to any state (terminal state)")
    void testInvalidTransition_CompletedToAnyState() {
        assertThat(COMPLETED.canTransitionTo(DRAFT)).isFalse();
        assertThat(COMPLETED.canTransitionTo(PLANNED)).isFalse();
        assertThat(COMPLETED.canTransitionTo(IN_PROGRESS)).isFalse();
        assertThat(COMPLETED.canTransitionTo(CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("Should reject transition from CANCELLED to COMPLETED (terminal state)")
    void testInvalidTransition_CancelledToCompleted() {
        boolean result = CANCELLED.canTransitionTo(COMPLETED);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject transition from CANCELLED to any state (terminal state)")
    void testInvalidTransition_CancelledToAnyState() {
        assertThat(CANCELLED.canTransitionTo(DRAFT)).isFalse();
        assertThat(CANCELLED.canTransitionTo(PLANNED)).isFalse();
        assertThat(CANCELLED.canTransitionTo(IN_PROGRESS)).isFalse();
        assertThat(CANCELLED.canTransitionTo(COMPLETED)).isFalse();
    }

    // Invalid transition tests - Not allowed paths
    @Test
    @DisplayName("Should reject invalid transition from DRAFT to IN_PROGRESS")
    void testInvalidTransition_DraftToInProgress() {
        boolean result = DRAFT.canTransitionTo(IN_PROGRESS);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject invalid transition from DRAFT to COMPLETED")
    void testInvalidTransition_DraftToCompleted() {
        boolean result = DRAFT.canTransitionTo(COMPLETED);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject invalid transition from IN_PROGRESS to DRAFT")
    void testInvalidTransition_InProgressToDraft() {
        boolean result = IN_PROGRESS.canTransitionTo(DRAFT);
        assertThat(result).isFalse();
    }

    // Same state tests
    @Test
    @DisplayName("Should allow same state transition (no actual transition)")
    void testSameStateTransition() {
        assertThat(DRAFT.canTransitionTo(DRAFT)).isTrue();
        assertThat(PLANNED.canTransitionTo(PLANNED)).isTrue();
        assertThat(IN_PROGRESS.canTransitionTo(IN_PROGRESS)).isTrue();
        assertThat(COMPLETED.canTransitionTo(COMPLETED)).isTrue();
        assertThat(CANCELLED.canTransitionTo(CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("Should reject transition when new status is null")
    void testNullNewStatus() {
        boolean result = DRAFT.canTransitionTo(null);
        assertThat(result).isFalse();
    }
}

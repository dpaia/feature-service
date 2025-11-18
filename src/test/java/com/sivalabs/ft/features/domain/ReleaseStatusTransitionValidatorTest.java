package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReleaseStatusTransitionValidator Unit Tests")
class ReleaseStatusTransitionValidatorTest {

    private ReleaseStatusTransitionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ReleaseStatusTransitionValidator();
    }

    // Valid transition tests
    @Test
    @DisplayName("Should allow valid transition from DRAFT to PLANNED")
    void testValidTransition_DraftToPlanned() {
        boolean result = validator.validateTransition(ReleaseStatus.DRAFT, ReleaseStatus.PLANNED);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow valid transition from DRAFT to CANCELLED")
    void testValidTransition_DraftToCancelled() {
        boolean result = validator.validateTransition(ReleaseStatus.DRAFT, ReleaseStatus.CANCELLED);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow valid transition from PLANNED to IN_PROGRESS")
    void testValidTransition_PlannedToInProgress() {
        boolean result = validator.validateTransition(ReleaseStatus.PLANNED, ReleaseStatus.IN_PROGRESS);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow valid transition from PLANNED to CANCELLED")
    void testValidTransition_PlannedToCancelled() {
        boolean result = validator.validateTransition(ReleaseStatus.PLANNED, ReleaseStatus.CANCELLED);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow valid transition from PLANNED to DRAFT (rollback)")
    void testValidTransition_PlannedToDraft() {
        boolean result = validator.validateTransition(ReleaseStatus.PLANNED, ReleaseStatus.DRAFT);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow valid transition from IN_PROGRESS to COMPLETED")
    void testValidTransition_InProgressToCompleted() {
        boolean result = validator.validateTransition(ReleaseStatus.IN_PROGRESS, ReleaseStatus.COMPLETED);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow valid transition from IN_PROGRESS to PLANNED")
    void testValidTransition_InProgressToPlanned() {
        boolean result = validator.validateTransition(ReleaseStatus.IN_PROGRESS, ReleaseStatus.PLANNED);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow valid transition from IN_PROGRESS to CANCELLED")
    void testValidTransition_InProgressToCancelled() {
        boolean result = validator.validateTransition(ReleaseStatus.IN_PROGRESS, ReleaseStatus.CANCELLED);
        assertThat(result).isTrue();
    }

    // Invalid transition tests - Terminal states
    @Test
    @DisplayName("Should reject transition from COMPLETED to PLANNED (terminal state)")
    void testInvalidTransition_CompletedToPlanned() {
        boolean result = validator.validateTransition(ReleaseStatus.COMPLETED, ReleaseStatus.PLANNED);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject transition from COMPLETED to any state (terminal state)")
    void testInvalidTransition_CompletedToAnyState() {
        assertThat(validator.validateTransition(ReleaseStatus.COMPLETED, ReleaseStatus.DRAFT))
                .isFalse();
        assertThat(validator.validateTransition(ReleaseStatus.COMPLETED, ReleaseStatus.PLANNED))
                .isFalse();
        assertThat(validator.validateTransition(ReleaseStatus.COMPLETED, ReleaseStatus.IN_PROGRESS))
                .isFalse();
        assertThat(validator.validateTransition(ReleaseStatus.COMPLETED, ReleaseStatus.CANCELLED))
                .isFalse();
    }

    @Test
    @DisplayName("Should reject transition from CANCELLED to COMPLETED (terminal state)")
    void testInvalidTransition_CancelledToCompleted() {
        boolean result = validator.validateTransition(ReleaseStatus.CANCELLED, ReleaseStatus.COMPLETED);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject transition from CANCELLED to any state (terminal state)")
    void testInvalidTransition_CancelledToAnyState() {
        assertThat(validator.validateTransition(ReleaseStatus.CANCELLED, ReleaseStatus.DRAFT))
                .isFalse();
        assertThat(validator.validateTransition(ReleaseStatus.CANCELLED, ReleaseStatus.PLANNED))
                .isFalse();
        assertThat(validator.validateTransition(ReleaseStatus.CANCELLED, ReleaseStatus.IN_PROGRESS))
                .isFalse();
        assertThat(validator.validateTransition(ReleaseStatus.CANCELLED, ReleaseStatus.COMPLETED))
                .isFalse();
    }

    // Invalid transition tests - Not allowed paths
    @Test
    @DisplayName("Should reject invalid transition from DRAFT to IN_PROGRESS")
    void testInvalidTransition_DraftToInProgress() {
        boolean result = validator.validateTransition(ReleaseStatus.DRAFT, ReleaseStatus.IN_PROGRESS);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject invalid transition from DRAFT to COMPLETED")
    void testInvalidTransition_DraftToCompleted() {
        boolean result = validator.validateTransition(ReleaseStatus.DRAFT, ReleaseStatus.COMPLETED);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject invalid transition from IN_PROGRESS to DRAFT")
    void testInvalidTransition_InProgressToDraft() {
        boolean result = validator.validateTransition(ReleaseStatus.IN_PROGRESS, ReleaseStatus.DRAFT);
        assertThat(result).isFalse();
    }

    // Same state tests
    @Test
    @DisplayName("Should allow same state transition (no actual transition)")
    void testSameStateTransition() {
        assertThat(validator.validateTransition(ReleaseStatus.DRAFT, ReleaseStatus.DRAFT))
                .isTrue();
        assertThat(validator.validateTransition(ReleaseStatus.PLANNED, ReleaseStatus.PLANNED))
                .isTrue();
        assertThat(validator.validateTransition(ReleaseStatus.IN_PROGRESS, ReleaseStatus.IN_PROGRESS))
                .isTrue();
        assertThat(validator.validateTransition(ReleaseStatus.COMPLETED, ReleaseStatus.COMPLETED))
                .isTrue();
        assertThat(validator.validateTransition(ReleaseStatus.CANCELLED, ReleaseStatus.CANCELLED))
                .isTrue();
    }

    // Null handling tests
    @Test
    @DisplayName("Should reject transition when current status is null")
    void testNullCurrentStatus() {
        boolean result = validator.validateTransition(null, ReleaseStatus.PLANNED);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject transition when new status is null")
    void testNullNewStatus() {
        boolean result = validator.validateTransition(ReleaseStatus.DRAFT, null);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject transition when both statuses are null")
    void testBothStatusesNull() {
        boolean result = validator.validateTransition(null, null);
        assertThat(result).isFalse();
    }

    // GetValidNextStates tests
    @Test
    @DisplayName("Should return correct valid next states from DRAFT")
    void testGetValidNextStates_FromDraft() {
        Set<ReleaseStatus> validStates = validator.getValidNextStates(ReleaseStatus.DRAFT);
        assertThat(validStates)
                .containsExactlyInAnyOrder(ReleaseStatus.PLANNED, ReleaseStatus.CANCELLED)
                .hasSize(2);
    }

    @Test
    @DisplayName("Should return correct valid next states from PLANNED")
    void testGetValidNextStates_FromPlanned() {
        Set<ReleaseStatus> validStates = validator.getValidNextStates(ReleaseStatus.PLANNED);
        assertThat(validStates)
                .containsExactlyInAnyOrder(ReleaseStatus.IN_PROGRESS, ReleaseStatus.CANCELLED, ReleaseStatus.DRAFT)
                .hasSize(3);
    }

    @Test
    @DisplayName("Should return correct valid next states from IN_PROGRESS")
    void testGetValidNextStates_FromInProgress() {
        Set<ReleaseStatus> validStates = validator.getValidNextStates(ReleaseStatus.IN_PROGRESS);
        assertThat(validStates)
                .containsExactlyInAnyOrder(ReleaseStatus.COMPLETED, ReleaseStatus.PLANNED, ReleaseStatus.CANCELLED)
                .hasSize(3);
    }

    @Test
    @DisplayName("Should return empty set for terminal state COMPLETED")
    void testGetValidNextStates_FromCompleted() {
        Set<ReleaseStatus> validStates = validator.getValidNextStates(ReleaseStatus.COMPLETED);
        assertThat(validStates).isEmpty();
    }

    @Test
    @DisplayName("Should return empty set for terminal state CANCELLED")
    void testGetValidNextStates_FromCancelled() {
        Set<ReleaseStatus> validStates = validator.getValidNextStates(ReleaseStatus.CANCELLED);
        assertThat(validStates).isEmpty();
    }

    @Test
    @DisplayName("Should return empty set when status is null")
    void testGetValidNextStates_NullStatus() {
        Set<ReleaseStatus> validStates = validator.getValidNextStates(null);
        assertThat(validStates).isEmpty();
    }
}

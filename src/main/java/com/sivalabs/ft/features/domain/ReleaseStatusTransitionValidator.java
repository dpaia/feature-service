package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validator for release status transitions following state machine rules.
 *
 * Valid State Transitions:
 * - DRAFT → PLANNED (Planning complete)
 * - DRAFT → CANCELLED (Cancelled before planning)
 * - PLANNED → IN_PROGRESS (Development started)
 * - PLANNED → CANCELLED (Scope cancelled)
 * - PLANNED → DRAFT (Rare: Go back to draft)
 * - IN_PROGRESS → COMPLETED (Release delivered)
 * - IN_PROGRESS → PLANNED (Back to planning if needed)
 * - IN_PROGRESS → CANCELLED (Cancelled during development)
 *
 * Terminal States (no transitions allowed):
 * - COMPLETED
 * - CANCELLED
 */
@Component
public class ReleaseStatusTransitionValidator {

    private static final Map<ReleaseStatus, Set<ReleaseStatus>> VALID_TRANSITIONS = new EnumMap<>(ReleaseStatus.class);

    static {
        // DRAFT can transition to PLANNED or CANCELLED
        VALID_TRANSITIONS.put(ReleaseStatus.DRAFT, Set.of(ReleaseStatus.PLANNED, ReleaseStatus.CANCELLED));

        // PLANNED can transition to IN_PROGRESS, CANCELLED, or back to DRAFT
        VALID_TRANSITIONS.put(
                ReleaseStatus.PLANNED, Set.of(ReleaseStatus.IN_PROGRESS, ReleaseStatus.CANCELLED, ReleaseStatus.DRAFT));

        // IN_PROGRESS can transition to COMPLETED, PLANNED, or CANCELLED
        VALID_TRANSITIONS.put(
                ReleaseStatus.IN_PROGRESS,
                Set.of(ReleaseStatus.COMPLETED, ReleaseStatus.PLANNED, ReleaseStatus.CANCELLED));

        // COMPLETED is a terminal state (no transitions)
        VALID_TRANSITIONS.put(ReleaseStatus.COMPLETED, Set.of());

        // CANCELLED is a terminal state (no transitions)
        VALID_TRANSITIONS.put(ReleaseStatus.CANCELLED, Set.of());
    }

    /**
     * Validates if a status transition is allowed.
     *
     * @param currentStatus The current status
     * @param newStatus The requested new status
     * @return true if transition is valid, false otherwise
     */
    public boolean validateTransition(ReleaseStatus currentStatus, ReleaseStatus newStatus) {
        if (currentStatus == null || newStatus == null) {
            return false;
        }
        if (currentStatus == newStatus) {
            return true; // Same status is always valid (no transition)
        }
        Set<ReleaseStatus> validNextStates = VALID_TRANSITIONS.get(currentStatus);
        return validNextStates != null && validNextStates.contains(newStatus);
    }

    /**
     * Gets the set of valid next states for a given current status.
     *
     * @param currentStatus The current status
     * @return Set of valid next states (empty for terminal states)
     */
    public Set<ReleaseStatus> getValidNextStates(ReleaseStatus currentStatus) {
        if (currentStatus == null) {
            return Set.of();
        }
        return VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
    }
}

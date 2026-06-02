package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Validates release status transitions.
 */
@Service
public class ReleaseStatusValidator {

    // Define allowed transitions for each status
    // According to requirements:
    // DRAFT → PLANNED
    // PLANNED → IN_PROGRESS
    // IN_PROGRESS → RELEASED, DELAYED, CANCELLED
    // DELAYED → RELEASED, CANCELLED
    // RELEASED → COMPLETED
    // CANCELLED — end state (terminal)
    // COMPLETED — end state (terminal)
    private static final Map<ReleaseStatus, Set<ReleaseStatus>> ALLOWED_TRANSITIONS = Map.of(
            ReleaseStatus.DRAFT, EnumSet.of(ReleaseStatus.PLANNED),
            ReleaseStatus.PLANNED, EnumSet.of(ReleaseStatus.IN_PROGRESS),
            ReleaseStatus.IN_PROGRESS,
                    EnumSet.of(ReleaseStatus.RELEASED, ReleaseStatus.DELAYED, ReleaseStatus.CANCELLED),
            ReleaseStatus.DELAYED, EnumSet.of(ReleaseStatus.RELEASED, ReleaseStatus.CANCELLED),
            ReleaseStatus.RELEASED, EnumSet.of(ReleaseStatus.COMPLETED),
            ReleaseStatus.COMPLETED, EnumSet.noneOf(ReleaseStatus.class), // Terminal state - no transitions allowed
            ReleaseStatus.CANCELLED, EnumSet.noneOf(ReleaseStatus.class) // Terminal state - no transitions allowed
            );

    /**
     * Validates if a status transition is allowed.
     */
    public void validateTransition(ReleaseStatus oldStatus, ReleaseStatus newStatus) {
        if (oldStatus == newStatus) {
            return; // No transition needed
        }

        Set<ReleaseStatus> allowedTransitions = ALLOWED_TRANSITIONS.get(oldStatus);
        if (allowedTransitions == null || !allowedTransitions.contains(newStatus)) {
            throw new BadRequestException("Invalid release status transition: " + oldStatus + " -> " + newStatus);
        }
    }
}

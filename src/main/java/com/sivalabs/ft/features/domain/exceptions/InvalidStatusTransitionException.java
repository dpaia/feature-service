package com.sivalabs.ft.features.domain.exceptions;

import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.util.Set;

public class InvalidStatusTransitionException extends BadRequestException {
    private final ReleaseStatus currentStatus;
    private final ReleaseStatus requestedStatus;
    private final Set<ReleaseStatus> validNextStates;

    public InvalidStatusTransitionException(
            ReleaseStatus currentStatus, ReleaseStatus requestedStatus, Set<ReleaseStatus> validNextStates) {
        super(buildMessage(currentStatus, requestedStatus, validNextStates));
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
        this.validNextStates = validNextStates;
    }

    private static String buildMessage(
            ReleaseStatus currentStatus, ReleaseStatus requestedStatus, Set<ReleaseStatus> validNextStates) {
        return String.format(
                "Invalid status transition from %s to %s. Valid next states are: %s",
                currentStatus, requestedStatus, validNextStates);
    }

    public ReleaseStatus getCurrentStatus() {
        return currentStatus;
    }

    public ReleaseStatus getRequestedStatus() {
        return requestedStatus;
    }

    public Set<ReleaseStatus> getValidNextStates() {
        return validNextStates;
    }
}

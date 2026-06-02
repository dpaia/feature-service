package com.sivalabs.ft.features.domain.models;

public enum ReleaseStatus {
    DRAFT,
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    DELAYED,
    CANCELLED,
    RELEASED;

    public boolean canTransitionTo(ReleaseStatus target) {
        if (this == target) return true;
        return switch (this) {
            case DRAFT -> true;
            case PLANNED -> target == IN_PROGRESS || target == DELAYED || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == DELAYED || target == CANCELLED;
            case COMPLETED -> target == RELEASED;
            case DELAYED -> target == PLANNED || target == IN_PROGRESS || target == CANCELLED;
            case CANCELLED, RELEASED -> false;
        };
    }
}

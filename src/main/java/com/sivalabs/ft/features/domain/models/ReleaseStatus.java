package com.sivalabs.ft.features.domain.models;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum ReleaseStatus {
    DRAFT,
    PLANNED,
    IN_PROGRESS,
    RELEASED,
    DELAYED,
    CANCELLED,
    COMPLETED;

    private static final Map<ReleaseStatus, Set<ReleaseStatus>> VALID_TRANSITIONS;

    static {
        VALID_TRANSITIONS = new EnumMap<>(ReleaseStatus.class);
        VALID_TRANSITIONS.put(DRAFT, EnumSet.of(PLANNED));
        VALID_TRANSITIONS.put(PLANNED, EnumSet.of(IN_PROGRESS));
        VALID_TRANSITIONS.put(IN_PROGRESS, EnumSet.of(RELEASED, DELAYED, CANCELLED));
        VALID_TRANSITIONS.put(DELAYED, EnumSet.of(RELEASED, CANCELLED));
        VALID_TRANSITIONS.put(RELEASED, EnumSet.of(COMPLETED));
        VALID_TRANSITIONS.put(CANCELLED, EnumSet.noneOf(ReleaseStatus.class));
        VALID_TRANSITIONS.put(COMPLETED, EnumSet.noneOf(ReleaseStatus.class));
    }

    public boolean canTransitionTo(ReleaseStatus newStatus) {
        return VALID_TRANSITIONS
                .getOrDefault(this, EnumSet.noneOf(ReleaseStatus.class))
                .contains(newStatus);
    }
}

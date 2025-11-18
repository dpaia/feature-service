package com.sivalabs.ft.features.domain.models;

public enum ReleaseStatus {
    DRAFT, // Initial state after creation (no release scheduled yet)
    PLANNED, // Release date and scope planned, features assigned
    IN_PROGRESS, // Release development started, features being developed
    COMPLETED, // Release delivered to production
    CANCELLED // Release cancelled, not proceeding
}

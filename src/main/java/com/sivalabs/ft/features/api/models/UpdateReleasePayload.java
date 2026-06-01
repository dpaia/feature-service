package com.sivalabs.ft.features.api.models;

import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateReleasePayload(
        String description,
        ReleaseStatus status,
        Instant releasedAt,
        @Size(max = 50, message = "Milestone code cannot exceed 50 characters") String milestoneCode) {}

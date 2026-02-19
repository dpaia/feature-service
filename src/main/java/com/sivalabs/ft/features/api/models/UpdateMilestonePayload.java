package com.sivalabs.ft.features.api.models;

import com.sivalabs.ft.features.domain.models.MilestoneStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateMilestonePayload(
        @Size(max = 255, message = "Milestone name cannot exceed 255 characters") @NotEmpty(message = "Milestone name is required") String name,
        String description,
        @NotNull(message = "Target date is required") Instant targetDate,
        Instant actualDate,
        @NotNull(message = "Status is required") MilestoneStatus status,
        String owner,
        String notes) {}

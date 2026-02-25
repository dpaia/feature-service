package com.sivalabs.ft.features.api.models;

import com.sivalabs.ft.features.domain.models.MilestoneStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateMilestonePayload(
        @NotEmpty(message = "Product code is required") String productCode,
        @Size(max = 50, message = "Milestone code cannot exceed 50 characters") @NotEmpty(message = "Milestone code is required") String code,
        @Size(max = 255, message = "Milestone name cannot exceed 255 characters") @NotEmpty(message = "Milestone name is required") String name,
        String description,
        @NotNull(message = "Target date is required") Instant targetDate,
        @NotNull(message = "Status is required") MilestoneStatus status,
        String owner,
        String notes) {}

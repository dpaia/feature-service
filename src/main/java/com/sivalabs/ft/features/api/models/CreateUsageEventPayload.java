package com.sivalabs.ft.features.api.models;

import com.sivalabs.ft.features.domain.models.ActionType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record CreateUsageEventPayload(
        @NotNull(message = "Action type is required") ActionType actionType,
        String featureCode,
        String productCode,
        String releaseCode,
        Map<String, Object> context) {}

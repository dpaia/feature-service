package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.RiskLevel;
import com.sivalabs.ft.features.domain.models.TimelineAdherence;
import java.io.Serializable;

public record HealthIndicatorsDto(TimelineAdherence timelineAdherence, RiskLevel riskLevel, int blockedFeatures)
        implements Serializable {}

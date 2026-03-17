package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.RiskLevel;
import com.sivalabs.ft.features.domain.models.TimelineAdherence;

public record HealthIndicators(RiskLevel riskLevel, TimelineAdherence timelineAdherence) {}

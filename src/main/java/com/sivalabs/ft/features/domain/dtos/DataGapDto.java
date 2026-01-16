package com.sivalabs.ft.features.domain.dtos;

import java.time.Instant;

public record DataGapDto(Instant startTime, Instant endTime, String reason) {}

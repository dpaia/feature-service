package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.ErrorType;
import java.time.Instant;

public record ErrorLogDto(
        Long id,
        Instant timestamp,
        ErrorType errorType,
        String errorMessage,
        String stackTrace,
        String eventPayload,
        String userId,
        Boolean resolved) {}

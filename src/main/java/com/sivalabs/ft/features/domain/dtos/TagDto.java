package com.sivalabs.ft.features.domain.dtos;

import java.io.Serializable;
import java.time.Instant;

public record TagDto(Long id, String name, String description, String createdBy, Instant createdAt)
        implements Serializable {}

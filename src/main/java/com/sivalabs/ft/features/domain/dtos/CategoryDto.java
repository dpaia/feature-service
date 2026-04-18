package com.sivalabs.ft.features.domain.dtos;

import java.io.Serializable;
import java.time.Instant;

public record CategoryDto(
        Long id, String name, String description, CategoryDto parentCategory, String createdBy, Instant createdAt)
        implements Serializable {}

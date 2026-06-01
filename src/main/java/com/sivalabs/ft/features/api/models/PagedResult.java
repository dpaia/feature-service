package com.sivalabs.ft.features.api.models;

import java.util.List;

public record PagedResult<T>(List<T> content, long totalElements, int totalPages, int pageNumber, int pageSize) {}

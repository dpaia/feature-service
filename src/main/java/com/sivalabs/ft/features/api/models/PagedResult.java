package com.sivalabs.ft.features.api.models;

import java.util.List;
import org.springframework.data.domain.Page;

public record PagedResult<T>(
        List<T> content, long totalElements, int totalPages, int number, int size, boolean first, boolean last) {

    public static <T> PagedResult<T> from(Page<T> page) {
        return new PagedResult<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize(),
                page.isFirst(),
                page.isLast());
    }
}

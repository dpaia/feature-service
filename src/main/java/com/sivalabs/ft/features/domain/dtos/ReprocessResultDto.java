package com.sivalabs.ft.features.domain.dtos;

import java.util.ArrayList;
import java.util.List;

public record ReprocessResultDto(
        Integer totalProcessed, Integer successCount, Integer failedCount, List<ErrorDetail> errors) {

    public record ErrorDetail(Long errorLogId, String errorMessage) {}

    public ReprocessResultDto() {
        this(0, 0, 0, new ArrayList<>());
    }
}

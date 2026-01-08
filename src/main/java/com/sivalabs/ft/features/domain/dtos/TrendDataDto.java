package com.sivalabs.ft.features.domain.dtos;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * DTO representing complete trend data for an entity (feature, product, or overall).
 * Contains the trend data points and summary statistics.
 */
public record TrendDataDto(
        /**
         * Entity code (featureCode, productCode, or "overall")
         */
        String entityCode,

        /**
         * Type of entity (FEATURE, PRODUCT, OVERALL)
         */
        @NotNull String entityType,

        /**
         * List of trend data points ordered by time
         */
        @NotNull List<UsageTrendDto> trends,

        /**
         * Summary statistics for the entire trend
         */
        @NotNull TrendSummaryDto summary) {}

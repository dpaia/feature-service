package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.AdvancedFilterDto;
import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Service for advanced multi-dimensional filtering of feature usage data.
 * Supports filtering by multiple criteria including custom tags from context.
 */
@Service
public class AdvancedFilterService {
    private static final Logger log = LoggerFactory.getLogger(AdvancedFilterService.class);

    private final FeatureUsageRepository featureUsageRepository;

    public AdvancedFilterService(FeatureUsageRepository featureUsageRepository) {
        this.featureUsageRepository = featureUsageRepository;
    }

    /**
     * Build JPA Specification for advanced filtering.
     * Supports multiple dimensions: features, products, releases, action types, dates, and context tags.
     *
     * @param filter Advanced filter criteria
     * @return JPA Specification for the filter
     */
    public Specification<FeatureUsage> buildSpecification(AdvancedFilterDto filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by feature codes
            if (filter.featureCodes() != null && !filter.featureCodes().isEmpty()) {
                predicates.add(root.get("featureCode").in(filter.featureCodes()));
            }

            // Filter by product codes
            if (filter.productCodes() != null && !filter.productCodes().isEmpty()) {
                predicates.add(root.get("productCode").in(filter.productCodes()));
            }

            // Filter by release codes
            if (filter.releaseCodes() != null && !filter.releaseCodes().isEmpty()) {
                predicates.add(root.get("releaseCode").in(filter.releaseCodes()));
            }

            // Filter by action types
            if (filter.actionTypes() != null && !filter.actionTypes().isEmpty()) {
                predicates.add(root.get("actionType").in(filter.actionTypes()));
            }

            // Filter by date range
            if (filter.startDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), filter.startDate()));
            }

            if (filter.endDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), filter.endDate()));
            }

            // Filter by context tags (MVP: simple string matching, can upgrade to JSONB later)
            if (filter.contextTags() != null && !filter.contextTags().isEmpty()) {
                for (Map.Entry<String, String> tag : filter.contextTags().entrySet()) {
                    // Simple approach: check if context contains the tag
                    // For MVP, using LIKE operator. TODO: Upgrade to JSONB for better performance
                    String pattern = "%" + tag.getKey() + "\":" + "\"" + tag.getValue() + "%";
                    predicates.add(cb.like(root.get("context"), pattern));
                }
            }

            log.debug("Built specification with {} predicates", predicates.size());

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Apply advanced filters and return matching usage events.
     *
     * @param filter Advanced filter criteria
     * @return List of matching feature usage events
     */
    public List<FeatureUsage> applyAdvancedFilters(AdvancedFilterDto filter) {
        log.debug(
                "Applying advanced filters: features={}, products={}, contextTags={}",
                filter.featureCodes() != null ? filter.featureCodes().size() : 0,
                filter.productCodes() != null ? filter.productCodes().size() : 0,
                filter.contextTags() != null ? filter.contextTags().size() : 0);

        Specification<FeatureUsage> spec = buildSpecification(filter);
        List<FeatureUsage> results = featureUsageRepository.findAll(spec);

        log.info("Advanced filter returned {} results", results.size());

        return results;
    }

    /**
     * Filter by custom context tags.
     * MVP implementation using string matching. Can be upgraded to JSONB queries.
     *
     * @param tags Map of tag key-value pairs to match
     * @param startDate Optional start date
     * @param endDate Optional end date
     * @return List of matching events
     */
    public List<FeatureUsage> filterByContextTags(Map<String, String> tags, Instant startDate, Instant endDate) {
        if (tags == null || tags.isEmpty()) {
            log.warn("No tags provided for context filtering");
            return List.of();
        }

        log.debug("Filtering by {} context tags", tags.size());

        // Build filter DTO
        AdvancedFilterDto filter = new AdvancedFilterDto(
                null, // featureCodes
                null, // productCodes
                null, // releaseCodes
                null, // actionTypes
                startDate,
                endDate,
                tags,
                null, // userSegment
                null // groupBy
                );

        return applyAdvancedFilters(filter);
    }

    /**
     * Validate advanced filter criteria.
     *
     * @param filter Filter to validate
     * @return true if valid, false otherwise
     */
    public boolean validateFilter(AdvancedFilterDto filter) {
        if (filter == null) {
            return false;
        }

        // Validate date range
        if (filter.startDate() != null && filter.endDate() != null) {
            if (filter.startDate().isAfter(filter.endDate())) {
                log.warn("Invalid date range: start {} is after end {}", filter.startDate(), filter.endDate());
                return false;
            }
        }

        // Validate that at least one filter criterion is provided
        boolean hasAnyCriteria = (filter.featureCodes() != null
                        && !filter.featureCodes().isEmpty())
                || (filter.productCodes() != null && !filter.productCodes().isEmpty())
                || (filter.releaseCodes() != null && !filter.releaseCodes().isEmpty())
                || (filter.actionTypes() != null && !filter.actionTypes().isEmpty())
                || (filter.contextTags() != null && !filter.contextTags().isEmpty())
                || filter.startDate() != null
                || filter.endDate() != null;

        if (!hasAnyCriteria) {
            log.warn("No filter criteria provided");
            return false;
        }

        return true;
    }
}

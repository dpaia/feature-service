package com.sivalabs.ft.features.domain;

import static java.util.Arrays.stream;
import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.sivalabs.ft.features.api.models.*;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import com.sivalabs.ft.features.domain.models.RiskLevel;
import com.sivalabs.ft.features.domain.models.TimelineAdherence;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoadmapService {

    private final ReleaseRepository releaseRepository;
    private final FeatureRepository featureRepository;

    public RoadmapService(ReleaseRepository releaseRepository, FeatureRepository featureRepository) {
        this.releaseRepository = releaseRepository;
        this.featureRepository = featureRepository;
    }

    public RoadmapResponse getRoadmap(
            String[] productCodes,
            String[] statuses,
            LocalDate dateFrom,
            LocalDate dateTo,
            String groupBy,
            String owner) {

        // Validate input parameters
        validateInputParameters(productCodes, statuses, dateFrom, dateTo, groupBy);

        // Create roadmap items with metrics and health indicators
        List<RoadmapItem> roadmapItems = releaseRepository.findAll().stream()
                .map(this::createRoadmapItem)
                .filter(release -> filterByProductCodes(release, productCodes))
                .filter(release -> filterByStatuses(release, statuses))
                .filter(release -> filterByDateRange(release, dateFrom, dateTo))
                .filter(release -> filterByOwner(release, owner))
                .sorted(comparing(this::getPriorityDateForSorting, reverseOrder()))
                .collect(toList());

        // Apply grouping if specified
        if (groupBy != null && !groupBy.isEmpty()) {
            roadmapItems = applyGrouping(roadmapItems, groupBy.toLowerCase());
        }

        // Calculate summary
        RoadmapSummary summary = calculateSummary(roadmapItems);

        // Create applied filters
        AppliedFilters appliedFilters = new AppliedFilters(productCodes, statuses, dateFrom, dateTo, groupBy, owner);

        return new RoadmapResponse(roadmapItems, summary, appliedFilters);
    }

    private void validateInputParameters(
            String[] productCodes, String[] statuses, LocalDate dateFrom, LocalDate dateTo, String groupBy) {

        // Validate statuses against enum values
        if (statuses != null) {
            Set<String> validStatuses = stream(ReleaseStatus.values())
                    .map(Enum::name)
                    .map(String::toUpperCase)
                    .collect(toSet());

            for (String status : statuses) {
                if (!validStatuses.contains(status.toUpperCase())) {
                    throw new IllegalArgumentException("Invalid status: " + status);
                }
            }
        }

        // Validate date range
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("dateFrom cannot be after dateTo");
        }

        // Validate groupBy values
        if (groupBy != null && !groupBy.isEmpty()) {
            Set<String> validGroupBy = Set.of("productcode", "status", "owner");
            if (!validGroupBy.contains(groupBy.toLowerCase())) {
                throw new IllegalArgumentException("Invalid groupBy value: " + groupBy);
            }
        }
    }

    private boolean filterByProductCodes(RoadmapItem item, String[] productCodes) {
        if (productCodes == null || productCodes.length == 0) {
            return true;
        }
        return Arrays.asList(productCodes).contains(item.product().code());
    }

    private boolean filterByStatuses(RoadmapItem item, String[] statuses) {
        if (statuses == null || statuses.length == 0) {
            return true;
        }
        return stream(statuses)
                .anyMatch(status -> status.equalsIgnoreCase(item.release().status()));
    }

    private boolean filterByDateRange(RoadmapItem item, LocalDate dateFrom, LocalDate dateTo) {
        Instant priorityDate = getPriorityDateForSorting(item);
        if (priorityDate == null) {
            return true; // Include releases without dates when filtering by date
        }

        LocalDate releaseDate = priorityDate.atZone(ZoneId.systemDefault()).toLocalDate();

        boolean afterFrom = dateFrom == null || !releaseDate.isBefore(dateFrom);
        boolean beforeTo = dateTo == null || !releaseDate.isAfter(dateTo);

        return afterFrom && beforeTo;
    }

    private boolean filterByOwner(RoadmapItem item, String owner) {
        if (owner == null || owner.isEmpty()) {
            return true;
        }
        return Objects.equals(item.release().owner(), owner);
    }

    private Instant getPriorityDateForSorting(RoadmapItem item) {
        return Stream.of(
                        item.release().actualReleaseDate(),
                        item.release().releasedAt(),
                        item.release().plannedReleaseDate(),
                        item.release().createdAt())
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("release.createdAt should never be null, but was for release "
                                + item.release().code()));
    }

    private RoadmapItem createRoadmapItem(Release release) {
        // Create product info
        ProductInfo productInfo = new ProductInfo(
                release.getProduct().getId(), release.getProduct().getCode());

        // Create RoadmapRelease directly from Release entity
        RoadmapRelease roadmapRelease = new RoadmapRelease(
                release.getId(),
                release.getCode(),
                release.getDescription(),
                release.getStatus().name(),
                release.getReleasedAt(),
                release.getPlannedStartDate(),
                release.getPlannedReleaseDate(),
                release.getActualReleaseDate(),
                release.getOwner(),
                release.getNotes(),
                release.getCreatedBy(),
                release.getCreatedAt(),
                release.getUpdatedBy(),
                release.getUpdatedAt());

        // Get features for this release
        List<Feature> features = featureRepository.findByReleaseCode(release.getCode());
        List<RoadmapFeature> roadmapFeatures =
                features.stream().map(this::convertToRoadmapFeature).collect(toList());

        // Calculate progress metrics
        ProgressMetrics progressMetrics = calculateProgressMetrics(features);

        // Calculate health indicators
        HealthIndicators healthIndicators = calculateHealthIndicators(release, features);

        return new RoadmapItem(productInfo, roadmapRelease, progressMetrics, healthIndicators, roadmapFeatures);
    }

    private RoadmapFeature convertToRoadmapFeature(Feature feature) {
        return new RoadmapFeature(
                feature.getId(),
                feature.getCode(),
                feature.getTitle(),
                feature.getDescription(),
                feature.getStatus().name(),
                feature.getRelease() != null ? feature.getRelease().getCode() : null,
                feature.getAssignedTo(),
                feature.getCreatedBy(),
                feature.getCreatedAt(),
                feature.getUpdatedBy(),
                feature.getUpdatedAt());
    }

    private ProgressMetrics calculateProgressMetrics(List<Feature> features) {
        int totalFeatures = features.size();
        if (totalFeatures == 0) {
            return new ProgressMetrics(0, 0, 0, 0, 0, 0.0);
        }

        Map<FeatureStatus, Long> statusCounts =
                features.stream().collect(groupingBy(Feature::getStatus, Collectors.counting()));

        int completedFeatures =
                statusCounts.getOrDefault(FeatureStatus.RELEASED, 0L).intValue();
        int inProgressFeatures =
                statusCounts.getOrDefault(FeatureStatus.IN_PROGRESS, 0L).intValue();
        int newFeatures = statusCounts.getOrDefault(FeatureStatus.NEW, 0L).intValue();
        int onHoldFeatures =
                statusCounts.getOrDefault(FeatureStatus.ON_HOLD, 0L).intValue();

        double completionPercentage = (double) completedFeatures / totalFeatures * 100.0;

        return new ProgressMetrics(
                totalFeatures,
                completedFeatures,
                inProgressFeatures,
                newFeatures,
                onHoldFeatures,
                Math.round(completionPercentage * 100.0) / 100.0 // Round to 2 decimal places
                );
    }

    private HealthIndicators calculateHealthIndicators(Release release, List<Feature> features) {
        RiskLevel riskLevel = calculateRiskLevel(features);
        TimelineAdherence timelineAdherence = calculateTimelineAdherence(release);

        return new HealthIndicators(
                riskLevel != null ? riskLevel.name() : null,
                timelineAdherence != null ? timelineAdherence.name() : null);
    }

    private RiskLevel calculateRiskLevel(List<Feature> features) {
        if (features.isEmpty()) {
            return RiskLevel.ZERO;
        }

        long onHoldCount = features.stream()
                .mapToLong(f -> f.getStatus() == FeatureStatus.ON_HOLD ? 1 : 0)
                .sum();

        double onHoldPercentage = (double) onHoldCount / features.size() * 100.0;

        if (onHoldCount == 0) {
            return RiskLevel.ZERO;
        } else if (onHoldPercentage < 10.0) {
            return RiskLevel.LOW;
        } else if (onHoldPercentage > 10.0 && onHoldPercentage <= 30.0) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.HIGH;
        }
    }

    private TimelineAdherence calculateTimelineAdherence(Release release) {
        Instant plannedReleaseDate = release.getPlannedReleaseDate();
        if (plannedReleaseDate == null) {
            return null;
        }

        Instant actualDate = release.getActualReleaseDate();
        if (actualDate == null) {
            actualDate = release.getReleasedAt();
        }

        // For finished releases, compare actual vs planned
        if (actualDate != null) {
            long daysDifference =
                    (actualDate.toEpochMilli() - plannedReleaseDate.toEpochMilli()) / (24 * 60 * 60 * 1000);
            return getTimelineAdherenceStatus(daysDifference);
        }

        // For upcoming releases, compare current date vs planned
        long daysDifference =
                (Instant.now().toEpochMilli() - plannedReleaseDate.toEpochMilli()) / (24 * 60 * 60 * 1000);
        return getTimelineAdherenceStatus(daysDifference);
    }

    private TimelineAdherence getTimelineAdherenceStatus(long daysDifference) {
        if (daysDifference <= 0) {
            return TimelineAdherence.ON_SCHEDULE;
        } else if (daysDifference < 14) {
            return TimelineAdherence.DELAYED;
        } else {
            return TimelineAdherence.CRITICAL;
        }
    }

    private List<RoadmapItem> applyGrouping(List<RoadmapItem> items, String groupBy) {
        Map<String, List<RoadmapItem>> grouped =
                switch (groupBy) {
                    case "productcode" ->
                        items.stream().collect(groupingBy(item -> item.product().code()));
                    case "status" ->
                        items.stream().collect(groupingBy(item -> item.release().status()));
                    case "owner" ->
                        items.stream()
                                .collect(groupingBy(item -> item.release().owner() != null
                                        ? item.release().owner()
                                        : ""));
                    default -> throw new IllegalArgumentException("Invalid groupBy value: " + groupBy);
                };

        // Sort groups by the date of the first release in each group (descending)
        // Then flatten grouped results while maintaining sort order within each group
        return grouped.values().stream()
                .sorted(comparing(group -> getPriorityDateForSorting(group.getFirst()), reverseOrder()))
                .flatMap(List::stream)
                .collect(toList());
    }

    private RoadmapSummary calculateSummary(List<RoadmapItem> roadmapItems) {
        int totalReleases = roadmapItems.size();

        long completedReleases = roadmapItems.stream()
                .filter(item -> "COMPLETED".equals(item.release().status())
                        || "RELEASED".equals(item.release().status()))
                .count();

        long draftReleases = roadmapItems.stream()
                .filter(item -> "DRAFT".equals(item.release().status()))
                .count();

        int totalFeatures = roadmapItems.stream()
                .mapToInt(item -> item.progressMetrics().totalFeatures())
                .sum();

        double overallCompletionPercentage =
                totalReleases > 0 ? (double) completedReleases / totalReleases * 100.0 : 0.0;

        return new RoadmapSummary(
                totalReleases,
                (int) completedReleases,
                (int) draftReleases,
                totalFeatures,
                Math.round(overallCompletionPercentage * 100.0) / 100.0);
    }
}

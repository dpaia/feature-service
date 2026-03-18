package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.AppliedFilters;
import com.sivalabs.ft.features.domain.dtos.HealthIndicators;
import com.sivalabs.ft.features.domain.dtos.ProductRef;
import com.sivalabs.ft.features.domain.dtos.ProgressMetrics;
import com.sivalabs.ft.features.domain.dtos.RoadmapFeatureDto;
import com.sivalabs.ft.features.domain.dtos.RoadmapItem;
import com.sivalabs.ft.features.domain.dtos.RoadmapResponse;
import com.sivalabs.ft.features.domain.dtos.RoadmapSummary;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.mappers.ReleaseMapper;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import com.sivalabs.ft.features.domain.models.RiskLevel;
import com.sivalabs.ft.features.domain.models.TimelineAdherence;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoadmapService {

    private static final List<String> VALID_GROUP_BY_VALUES = List.of("productcode", "status", "owner");

    private final ReleaseRepository releaseRepository;
    private final ReleaseMapper releaseMapper;

    RoadmapService(ReleaseRepository releaseRepository, ReleaseMapper releaseMapper) {
        this.releaseRepository = releaseRepository;
        this.releaseMapper = releaseMapper;
    }

    @Transactional(readOnly = true)
    public RoadmapResponse getRoadmap(RoadmapFilter filter) {
        validateFilter(filter);

        List<Release> allReleases = releaseRepository.findAllWithFeaturesAndProduct();

        List<Release> filtered = allReleases.stream()
                .filter(r -> filter.productCodes() == null
                        || filter.productCodes().isEmpty()
                        || filter.productCodes().contains(r.getProduct().getCode()))
                .filter(r -> filter.statuses() == null
                        || filter.statuses().isEmpty()
                        || filter.statuses().contains(r.getStatus()))
                .filter(r -> filter.owner() == null || filter.owner().equals(r.getOwner()))
                .filter(r -> matchesDateRange(r, filter.dateFrom(), filter.dateTo()))
                .toList();

        List<Release> sorted = filtered.stream()
                .sorted(Comparator.comparing(this::getReleaseSortDate).reversed())
                .toList();

        List<RoadmapItem> items;
        if (filter.groupBy() != null) {
            items = applyGroupBy(sorted, filter.groupBy());
        } else {
            items = sorted.stream().map(this::toRoadmapItem).toList();
        }

        RoadmapSummary summary = buildSummary(items);
        AppliedFilters appliedFilters = buildAppliedFilters(filter);

        return new RoadmapResponse(items, summary, appliedFilters);
    }

    private void validateFilter(RoadmapFilter filter) {
        if (filter.dateFrom() != null
                && filter.dateTo() != null
                && filter.dateFrom().isAfter(filter.dateTo())) {
            throw new BadRequestException("dateFrom must not be after dateTo");
        }
        if (filter.groupBy() != null
                && !VALID_GROUP_BY_VALUES.contains(filter.groupBy().toLowerCase())) {
            throw new BadRequestException(
                    "Invalid groupBy value: " + filter.groupBy() + ". Allowed values: productCode, status, owner");
        }
    }

    private boolean matchesDateRange(Release release, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null && dateTo == null) {
            return true;
        }
        LocalDate releaseDate =
                getReleaseSortDate(release).atZone(ZoneOffset.UTC).toLocalDate();
        if (dateFrom != null && releaseDate.isBefore(dateFrom)) {
            return false;
        }
        if (dateTo != null && releaseDate.isAfter(dateTo)) {
            return false;
        }
        return true;
    }

    Instant getReleaseSortDate(Release release) {
        if (release.getActualReleaseDate() != null) return release.getActualReleaseDate();
        if (release.getReleasedAt() != null) return release.getReleasedAt();
        if (release.getPlannedReleaseDate() != null) return release.getPlannedReleaseDate();
        return release.getCreatedAt();
    }

    private List<RoadmapItem> applyGroupBy(List<Release> sorted, String groupBy) {
        Map<String, List<Release>> grouped = sorted.stream()
                .collect(Collectors.groupingBy(r -> getGroupKey(r, groupBy), LinkedHashMap::new, Collectors.toList()));

        List<Map.Entry<String, List<Release>>> groupEntries = new ArrayList<>(grouped.entrySet());
        groupEntries.sort((e1, e2) -> {
            Instant d1 = getReleaseSortDate(e1.getValue().get(0));
            Instant d2 = getReleaseSortDate(e2.getValue().get(0));
            return d2.compareTo(d1);
        });

        return groupEntries.stream()
                .flatMap(e -> e.getValue().stream().map(this::toRoadmapItem))
                .toList();
    }

    private String getGroupKey(Release release, String groupBy) {
        return switch (groupBy.toLowerCase()) {
            case "productcode" -> release.getProduct().getCode();
            case "status" -> release.getStatus().name();
            case "owner" -> release.getOwner() != null ? release.getOwner() : "";
            default -> throw new BadRequestException("Invalid groupBy value: " + groupBy);
        };
    }

    RoadmapItem toRoadmapItem(Release release) {
        List<Feature> features = new ArrayList<>(release.getFeatures());
        ProductRef productRef = new ProductRef(
                release.getProduct().getId(), release.getProduct().getCode());
        ProgressMetrics progressMetrics = buildProgressMetrics(features);
        HealthIndicators healthIndicators = calculateHealthIndicators(release, features);
        List<RoadmapFeatureDto> featureDtos =
                features.stream().map(this::toFeatureDto).toList();
        return new RoadmapItem(
                productRef, releaseMapper.toDto(release), progressMetrics, healthIndicators, featureDtos);
    }

    private ProgressMetrics buildProgressMetrics(List<Feature> features) {
        int total = features.size();
        int completed = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.RELEASED)
                .count();
        int inProgress = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.IN_PROGRESS)
                .count();
        int newCount = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.NEW)
                .count();
        int onHold = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.ON_HOLD)
                .count();
        double completionPercentage = total == 0 ? 0.0 : (double) completed / total * 100.0;
        return new ProgressMetrics(total, completed, inProgress, newCount, onHold, completionPercentage);
    }

    private HealthIndicators calculateHealthIndicators(Release release, List<Feature> features) {
        RiskLevel riskLevel = calculateRiskLevel(features);
        TimelineAdherence timelineAdherence = calculateTimelineAdherence(release);
        return new HealthIndicators(riskLevel, timelineAdherence);
    }

    private RiskLevel calculateRiskLevel(List<Feature> features) {
        int total = features.size();
        if (total == 0) {
            return RiskLevel.ZERO;
        }
        int onHold = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.ON_HOLD)
                .count();
        if (onHold == 0) {
            return RiskLevel.ZERO;
        }
        double pct = (double) onHold / total * 100.0;
        if (pct > 30) {
            return RiskLevel.HIGH;
        } else if (pct > 10) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.LOW;
        }
    }

    private TimelineAdherence calculateTimelineAdherence(Release release) {
        if (release.getPlannedReleaseDate() == null) {
            return null;
        }
        Instant plannedDate = release.getPlannedReleaseDate();
        Instant actualDate =
                release.getActualReleaseDate() != null ? release.getActualReleaseDate() : release.getReleasedAt();
        Instant compareDate = actualDate != null ? actualDate : Instant.now();

        if (!compareDate.isAfter(plannedDate)) {
            return TimelineAdherence.ON_SCHEDULE;
        }
        long daysDelay = ChronoUnit.DAYS.between(plannedDate, compareDate);
        return daysDelay >= 14 ? TimelineAdherence.CRITICAL : TimelineAdherence.DELAYED;
    }

    private RoadmapFeatureDto toFeatureDto(Feature feature) {
        return new RoadmapFeatureDto(
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

    private RoadmapSummary buildSummary(List<RoadmapItem> items) {
        int totalReleases = items.size();
        int completedReleases = (int) items.stream()
                .filter(item -> item.release().status() == ReleaseStatus.COMPLETED
                        || item.release().status() == ReleaseStatus.RELEASED)
                .count();
        int draftReleases = (int) items.stream()
                .filter(item -> item.release().status() == ReleaseStatus.DRAFT)
                .count();
        int totalFeatures = items.stream()
                .mapToInt(item -> item.progressMetrics().totalFeatures())
                .sum();
        double overallCompletionPercentage =
                totalReleases == 0 ? 0.0 : (double) completedReleases / totalReleases * 100.0;
        return new RoadmapSummary(
                totalReleases, completedReleases, draftReleases, totalFeatures, overallCompletionPercentage);
    }

    private AppliedFilters buildAppliedFilters(RoadmapFilter filter) {
        List<String> statusNames = filter.statuses() == null
                ? null
                : filter.statuses().stream().map(ReleaseStatus::name).toList();
        return new AppliedFilters(
                filter.productCodes(),
                statusNames,
                filter.dateFrom(),
                filter.dateTo(),
                filter.groupBy(),
                filter.owner());
    }
}

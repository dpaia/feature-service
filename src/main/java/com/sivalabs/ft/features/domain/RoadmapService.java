package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.*;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.mappers.FeatureMapper;
import com.sivalabs.ft.features.domain.mappers.ReleaseMapper;
import com.sivalabs.ft.features.domain.models.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoadmapService {
    private static final Logger log = LoggerFactory.getLogger(RoadmapService.class);

    private final ReleaseRepository releaseRepository;
    private final FeatureRepository featureRepository;
    private final ProductRepository productRepository;
    private final ReleaseMapper releaseMapper;
    private final FeatureMapper featureMapper;
    private final FavoriteFeatureService favoriteFeatureService;

    public RoadmapService(
            ReleaseRepository releaseRepository,
            FeatureRepository featureRepository,
            ProductRepository productRepository,
            ReleaseMapper releaseMapper,
            FeatureMapper featureMapper,
            FavoriteFeatureService favoriteFeatureService) {
        this.releaseRepository = releaseRepository;
        this.featureRepository = featureRepository;
        this.productRepository = productRepository;
        this.releaseMapper = releaseMapper;
        this.featureMapper = featureMapper;
        this.favoriteFeatureService = favoriteFeatureService;
    }

    @Transactional(readOnly = true)
    public RoadmapResponseDto getRoadmap(String username, RoadmapFilterDto filter) {
        log.debug("Getting roadmap with filter: {}", filter);

        List<Release> releases = findReleasesWithFilter(filter);
        List<RoadmapItemDto> roadmapItems = releases.stream()
                .map(release -> createRoadmapItem(username, release, filter))
                .collect(Collectors.toList());

        // Apply grouping if specified
        if (filter.groupBy() != null) {
            roadmapItems = applyGrouping(roadmapItems, filter.groupBy());
        }

        RoadmapSummaryDto summary = calculateSummary(roadmapItems);

        return new RoadmapResponseDto(roadmapItems, summary, filter);
    }

    @Transactional(readOnly = true)
    public RoadmapResponseDto getMultiProductRoadmap(
            String username, List<String> productCodes, RoadmapFilterDto filter) {
        log.debug("Getting multi-product roadmap for products: {} with filter: {}", productCodes, filter);

        // Create a new filter with multiple product codes
        RoadmapFilterDto multiProductFilter = new RoadmapFilterDto(
                null, // single productCode set to null
                productCodes,
                filter.startDate(),
                filter.endDate(),
                filter.includeCompleted(),
                filter.groupBy(),
                filter.owner());

        return getRoadmap(username, multiProductFilter);
    }

    @Transactional(readOnly = true)
    public RoadmapResponseDto getRoadmapByOwner(String username, String owner, RoadmapFilterDto filter) {
        log.debug("Getting roadmap by owner: {} with filter: {}", owner, filter);

        // Create a new filter with owner
        RoadmapFilterDto ownerFilter = new RoadmapFilterDto(
                filter.productCode(),
                filter.productCodes(),
                filter.startDate(),
                filter.endDate(),
                filter.includeCompleted(),
                filter.groupBy(),
                owner);

        return getRoadmap(username, ownerFilter);
    }

    @Transactional(readOnly = true)
    public ByOwnerRoadmapResponseDto getStructuredRoadmapByOwner(
            String username, String owner, RoadmapFilterDto filter) {
        log.debug("Getting structured roadmap by owner: {} with filter: {}", owner, filter);

        // Create a new filter with owner
        RoadmapFilterDto ownerFilter = new RoadmapFilterDto(
                filter.productCode(),
                filter.productCodes(),
                filter.startDate(),
                filter.endDate(),
                filter.includeCompleted(),
                filter.groupBy(),
                owner);

        // Get the roadmap data
        RoadmapResponseDto roadmapResponse = getRoadmap(username, ownerFilter);

        // Return structured response with owner field
        return new ByOwnerRoadmapResponseDto(
                owner, roadmapResponse.roadmapItems(), roadmapResponse.summary(), ownerFilter);
    }

    @Transactional(readOnly = true)
    public MultiProductRoadmapResponseDto getGroupedMultiProductRoadmap(
            String username, List<String> productCodes, RoadmapFilterDto filter) {
        log.debug("Getting grouped multi-product roadmap for products: {} with filter: {}", productCodes, filter);

        // Get all releases for the specified products
        List<Release> releases = findReleasesWithFilter(filter);

        // Group releases by product
        Map<Product, List<Release>> releasesByProduct = releases.stream()
                .filter(release -> productCodes.contains(release.getProduct().getCode()))
                .collect(Collectors.groupingBy(Release::getProduct));

        // Create ProductRoadmapDto for each product
        List<ProductRoadmapDto> products = releasesByProduct.entrySet().stream()
                .map(entry -> {
                    Product product = entry.getKey();
                    List<Release> productReleases = entry.getValue();

                    List<RoadmapItemDto> roadmapItems = productReleases.stream()
                            .map(release -> createRoadmapItem(username, release, filter))
                            .collect(Collectors.toList());

                    // Apply grouping if specified
                    if (filter.groupBy() != null) {
                        roadmapItems = applyGrouping(roadmapItems, filter.groupBy());
                    }

                    return new ProductRoadmapDto(product.getId(), product.getName(), product.getCode(), roadmapItems);
                })
                .sorted(Comparator.comparing(ProductRoadmapDto::productName))
                .collect(Collectors.toList());

        // Calculate overall summary across all products
        List<RoadmapItemDto> allRoadmapItems = products.stream()
                .flatMap(product -> product.roadmapItems().stream())
                .collect(Collectors.toList());

        RoadmapSummaryDto summary = calculateSummary(allRoadmapItems);

        return new MultiProductRoadmapResponseDto(products, summary, filter);
    }

    private List<Release> findReleasesWithFilter(RoadmapFilterDto filter) {
        // Start with all releases
        List<Release> releases = releaseRepository.findAll();

        return releases.stream()
                .filter(release -> {
                    if (!filter.includeCompleted()) {
                        return release.getStatus() != ReleaseStatus.RELEASED;
                    }
                    return true;
                })
                .filter(release -> applyReleaseFilters(release, filter))
                .sorted(Comparator.comparing(Release::getCreatedAt))
                .collect(Collectors.toList());
    }

    private boolean applyReleaseFilters(Release release, RoadmapFilterDto filter) {
        // Product code filter
        if (filter.productCode() != null
                && !filter.productCode().trim().isEmpty()
                && !filter.productCode().equals(release.getProduct().getCode())) {
            return false;
        }

        // Multiple product codes filter
        if (filter.productCodes() != null
                && !filter.productCodes().isEmpty()
                && !filter.productCodes().contains(release.getProduct().getCode())) {
            return false;
        }

        // Date range filter
        if (filter.startDate() != null) {
            Instant startInstant = filter.startDate().atStartOfDay().toInstant(ZoneOffset.UTC);
            if (release.getCreatedAt().isBefore(startInstant)) {
                return false;
            }
        }

        if (filter.endDate() != null) {
            Instant endInstant = filter.endDate().atStartOfDay().plusDays(1).toInstant(ZoneOffset.UTC);
            if (release.getCreatedAt().isAfter(endInstant)) {
                return false;
            }
        }

        // Owner filter - check if any features in this release are assigned to the owner
        if (filter.owner() != null) {
            List<Feature> releaseFeatures = featureRepository.findByReleaseCode(release.getCode());
            boolean hasOwnerFeatures =
                    releaseFeatures.stream().anyMatch(feature -> filter.owner().equals(feature.getAssignedTo()));
            if (!hasOwnerFeatures) {
                return false;
            }
        }

        return true;
    }

    private List<RoadmapItemDto> createRoadmapItems(List<Release> releases) {
        return releases.stream().map(this::createRoadmapItem).collect(Collectors.toList());
    }

    private RoadmapItemDto createRoadmapItem(Release release) {
        return createRoadmapItem(release);
    }

    private RoadmapItemDto createRoadmapItem(String username, Release release, RoadmapFilterDto filter) {
        ReleaseDto releaseDto = releaseMapper.toDto(release);

        // Get features for this release
        List<Feature> features = featureRepository.findByReleaseCode(release.getCode());

        // Apply owner filter to features if specified
        if (filter != null && filter.owner() != null) {
            features = features.stream()
                    .filter(feature -> filter.owner().equals(feature.getAssignedTo()))
                    .collect(Collectors.toList());
        }

        List<FeatureDto> featureDtos = updateFavoriteStatus(features, username);

        // Calculate progress metrics
        ProgressMetricsDto progressMetrics = calculateProgressMetrics(features);

        // Calculate health indicators
        HealthIndicatorsDto healthIndicators = calculateHealthIndicators(release, features);

        return new RoadmapItemDto(releaseDto, progressMetrics, healthIndicators, featureDtos);
    }

    private ProgressMetricsDto calculateProgressMetrics(List<Feature> features) {
        int totalFeatures = features.size();
        int completedFeatures = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.RELEASED)
                .count();
        int inProgressFeatures = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.IN_PROGRESS)
                .count();
        int newFeatures = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.NEW)
                .count();
        int onHoldFeatures = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.ON_HOLD)
                .count();

        double completionPercentage = totalFeatures > 0 ? (completedFeatures * 100.0) / totalFeatures : 0.0;

        return new ProgressMetricsDto(
                totalFeatures,
                completedFeatures,
                inProgressFeatures,
                newFeatures,
                onHoldFeatures,
                completionPercentage);
    }

    private HealthIndicatorsDto calculateHealthIndicators(Release release, List<Feature> features) {
        TimelineAdherence timelineAdherence = calculateTimelineAdherence(release);
        RiskLevel riskLevel = calculateRiskLevel(release, features);
        int blockedFeatures = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.ON_HOLD)
                .count();

        return new HealthIndicatorsDto(timelineAdherence, riskLevel, blockedFeatures);
    }

    private TimelineAdherence calculateTimelineAdherence(Release release) {
        if (release.getReleasedAt() == null) {
            return TimelineAdherence.ON_SCHEDULE; // Default for unreleased items
        }

        // Simple timeline calculation - could be enhanced with more sophisticated logic
        Instant now = Instant.now();
        if (release.getStatus() == ReleaseStatus.RELEASED) {
            return TimelineAdherence.ON_SCHEDULE; // Released items are considered on time
        }

        // For draft releases, compare current date with created date + some threshold
        long daysSinceCreation =
                java.time.Duration.between(release.getCreatedAt(), now).toDays();
        if (daysSinceCreation > 180) { // 6 months threshold - critical delay
            return TimelineAdherence.CRITICAL;
        } else if (daysSinceCreation > 30) { // 1 month threshold - delayed
            return TimelineAdherence.DELAYED;
        }

        return TimelineAdherence.ON_SCHEDULE;
    }

    private RiskLevel calculateRiskLevel(Release release, List<Feature> features) {
        int totalFeatures = features.size();
        if (totalFeatures == 0) {
            return RiskLevel.LOW;
        }

        int blockedFeatures = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.ON_HOLD)
                .count();

        double blockedPercentage = (blockedFeatures * 100.0) / totalFeatures;

        if (blockedPercentage > 30) {
            return RiskLevel.HIGH;
        } else if (blockedPercentage > 10) {
            return RiskLevel.MEDIUM;
        }

        return RiskLevel.LOW;
    }

    private List<RoadmapItemDto> applyGrouping(List<RoadmapItemDto> items, GroupByOption groupBy) {
        // For now, just return sorted items - grouping UI logic can be handled on frontend
        // or we can implement more sophisticated grouping logic here
        return switch (groupBy) {
            case PRODUCT ->
                items.stream()
                        .sorted(Comparator.comparing(item -> item.release().code()))
                        .collect(Collectors.toList());
            case STATUS ->
                items.stream()
                        .sorted(Comparator.comparing(
                                item -> item.release().status().toString()))
                        .collect(Collectors.toList());
            case ASSIGNEE ->
                items.stream()
                        .sorted(Comparator.comparing(item -> {
                            // Get the primary assignee (first assignee found) for sorting
                            return item.features().stream()
                                    .map(feature -> feature.assignedTo() != null ? feature.assignedTo() : "Unassigned")
                                    .findFirst()
                                    .orElse("Unassigned");
                        }))
                        .collect(Collectors.toList());
        };
    }

    private List<FeatureDto> updateFavoriteStatus(List<Feature> features, String username) {
        if (username == null || features.isEmpty()) {
            return features.stream().map(featureMapper::toDto).toList();
        }
        Set<String> featureCodes = features.stream().map(Feature::getCode).collect(Collectors.toSet());
        Map<String, Boolean> favoriteFeatures = favoriteFeatureService.getFavoriteFeatures(username, featureCodes);
        return features.stream()
                .map(feature -> {
                    var dto = featureMapper.toDto(feature);
                    dto = dto.makeFavorite(favoriteFeatures.get(feature.getCode()));
                    return dto;
                })
                .toList();
    }

    private RoadmapSummaryDto calculateSummary(List<RoadmapItemDto> roadmapItems) {
        int totalReleases = roadmapItems.size();
        int completedReleases = (int) roadmapItems.stream()
                .filter(item -> item.release().status() == ReleaseStatus.RELEASED)
                .count();
        int draftReleases = totalReleases - completedReleases;

        int totalFeatures = roadmapItems.stream()
                .mapToInt(item -> item.progressMetrics().totalFeatures())
                .sum();

        int totalCompletedFeatures = roadmapItems.stream()
                .mapToInt(item -> item.progressMetrics().completedFeatures())
                .sum();

        double overallCompletionPercentage = totalFeatures > 0 ? (totalCompletedFeatures * 100.0) / totalFeatures : 0.0;

        return new RoadmapSummaryDto(
                totalReleases, completedReleases, draftReleases, totalFeatures, overallCompletionPercentage);
    }
}

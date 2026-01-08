package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.Commands.CreateReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand;
import com.sivalabs.ft.features.domain.dtos.FeatureAdoptionSummaryDto;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.dtos.ReleaseStatsDto;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.ReleaseMapper;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReleaseService {
    private static final Logger log = LoggerFactory.getLogger(ReleaseService.class);
    public static final String RELEASE_SEPARATOR = "-";
    private final ReleaseRepository releaseRepository;
    private final ProductRepository productRepository;
    private final FeatureRepository featureRepository;
    private final FeatureUsageRepository featureUsageRepository;
    private final ReleaseMapper releaseMapper;

    ReleaseService(
            ReleaseRepository releaseRepository,
            ProductRepository productRepository,
            FeatureRepository featureRepository,
            FeatureUsageRepository featureUsageRepository,
            ReleaseMapper releaseMapper) {
        this.releaseRepository = releaseRepository;
        this.productRepository = productRepository;
        this.featureRepository = featureRepository;
        this.featureUsageRepository = featureUsageRepository;
        this.releaseMapper = releaseMapper;
    }

    @Transactional(readOnly = true)
    public List<ReleaseDto> findReleasesByProductCode(String productCode) {
        return releaseRepository.findByProductCode(productCode).stream()
                .map(releaseMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ReleaseDto> findReleaseByCode(String code) {
        return releaseRepository.findByCode(code).map(releaseMapper::toDto);
    }

    @Transactional(readOnly = true)
    public boolean isReleaseExists(String code) {
        return releaseRepository.existsByCode(code);
    }

    @Transactional
    public String createRelease(CreateReleaseCommand cmd) {
        Product product = productRepository.findByCode(cmd.productCode()).orElseThrow();
        String code = cmd.code();
        if (!cmd.code().startsWith(product.getPrefix() + RELEASE_SEPARATOR)) {
            code = product.getPrefix() + RELEASE_SEPARATOR + cmd.code();
        }
        Release release = new Release();
        release.setProduct(product);
        release.setCode(code);
        release.setDescription(cmd.description());
        release.setStatus(ReleaseStatus.DRAFT);
        release.setCreatedBy(cmd.createdBy());
        release.setCreatedAt(Instant.now());
        releaseRepository.save(release);
        return code;
    }

    @Transactional
    public void updateRelease(UpdateReleaseCommand cmd) {
        Release release = releaseRepository.findByCode(cmd.code()).orElseThrow();
        release.setDescription(cmd.description());
        release.setStatus(cmd.status());
        release.setReleasedAt(cmd.releasedAt());
        release.setUpdatedBy(cmd.updatedBy());
        release.setUpdatedAt(Instant.now());
        releaseRepository.save(release);
    }

    @Transactional
    public void deleteRelease(String code) {
        if (!releaseRepository.existsByCode(code)) {
            throw new ResourceNotFoundException("Release with code " + code + " not found");
        }
        featureRepository.unsetRelease(code);
        releaseRepository.deleteByCode(code);
    }

    /**
     * Get comprehensive statistics for a release including feature adoption metrics.
     *
     * @param releaseCode Release code to analyze
     * @return ReleaseStatsDto with aggregated statistics
     * @throws ResourceNotFoundException if release not found
     */
    @Transactional(readOnly = true)
    public ReleaseStatsDto getReleaseStats(String releaseCode) {
        log.debug("Getting release statistics for: {}", releaseCode);

        // Get release entity
        Release release = releaseRepository
                .findByCode(releaseCode)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found: " + releaseCode));

        // Get all features in this release
        List<Feature> features = release.getFeatures().stream().toList();
        int totalFeatures = features.size();

        if (totalFeatures == 0) {
            log.info("Release {} has no features", releaseCode);
            return new ReleaseStatsDto(releaseCode, release.getReleasedAt(), 0, 0, 0L, 0L, 0.0, List.of(), 0.0);
        }

        // Get aggregated usage statistics for all features in release
        Instant releaseDate = release.getReleasedAt();
        List<Object[]> aggregatedStats =
                featureUsageRepository.findAggregatedStatsByReleaseCode(releaseCode, releaseDate, null);

        // Create map of feature code -> stats
        Map<String, Object[]> statsMap =
                aggregatedStats.stream().collect(Collectors.toMap(row -> (String) row[0], row -> row));

        // Build feature adoption summaries
        List<FeatureAdoptionSummaryDto> featureSummaries = new ArrayList<>();
        long totalUniqueUsers = 0;
        long totalUsage = 0;
        double totalAdoptionScore = 0.0;
        int featuresWithUsage = 0;

        for (Feature feature : features) {
            String featureCode = feature.getCode();
            Object[] stats = statsMap.get(featureCode);

            if (stats != null) {
                long uniqueUsers = ((Number) stats[1]).longValue();
                long usageCount = ((Number) stats[2]).longValue();

                // Calculate simple adoption score based on usage
                double adoptionScore = calculateSimpleAdoptionScore(uniqueUsers, usageCount);
                double adoptionRate = calculateSimpleAdoptionRate(uniqueUsers);

                featureSummaries.add(new FeatureAdoptionSummaryDto(
                        featureCode, feature.getTitle(), uniqueUsers, usageCount, adoptionScore, adoptionRate));

                totalUniqueUsers = Math.max(totalUniqueUsers, uniqueUsers);
                totalUsage += usageCount;
                totalAdoptionScore += adoptionScore;
                featuresWithUsage++;
            } else {
                // Feature has no usage yet
                featureSummaries.add(new FeatureAdoptionSummaryDto(featureCode, feature.getTitle(), 0L, 0L, 0.0, 0.0));
            }
        }

        // Sort by adoption score descending
        featureSummaries.sort(Comparator.comparingDouble(FeatureAdoptionSummaryDto::adoptionScore)
                .reversed());

        // Calculate averages and overall score
        double averageAdoptionScore = featuresWithUsage > 0 ? totalAdoptionScore / featuresWithUsage : 0.0;
        double overallReleaseScore =
                calculateOverallReleaseScore(featuresWithUsage, totalFeatures, averageAdoptionScore);

        log.info(
                "Release {} stats: {} features, {} with usage, avg score: {}, overall: {}",
                releaseCode,
                totalFeatures,
                featuresWithUsage,
                averageAdoptionScore,
                overallReleaseScore);

        return new ReleaseStatsDto(
                releaseCode,
                releaseDate,
                totalFeatures,
                featuresWithUsage,
                totalUniqueUsers,
                totalUsage,
                averageAdoptionScore,
                featureSummaries,
                overallReleaseScore);
    }

    private double calculateSimpleAdoptionScore(long uniqueUsers, long totalUsage) {
        if (uniqueUsers == 0) {
            return 0.0;
        }
        double usagePerUser = (double) totalUsage / uniqueUsers;
        return Math.min((usagePerUser / 10.0) * 100, 100.0);
    }

    private double calculateSimpleAdoptionRate(long uniqueUsers) {
        return Math.min((uniqueUsers / 100.0) * 100, 100.0);
    }

    private double calculateOverallReleaseScore(int featuresWithUsage, int totalFeatures, double averageAdoptionScore) {
        if (totalFeatures == 0) {
            return 0.0;
        }
        double coverageScore = ((double) featuresWithUsage / totalFeatures) * 100;
        return (coverageScore * 0.6) + (averageAdoptionScore * 0.4);
    }
}

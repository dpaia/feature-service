package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.AdoptionMetricsDto;
import com.sivalabs.ft.features.domain.dtos.AdoptionRateDto;
import com.sivalabs.ft.features.domain.dtos.AdoptionRateDto.AdoptionWindowMetrics;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for calculating feature adoption rates after release.
 * Tracks how quickly and effectively users adopt newly released features.
 */
@Service
@Transactional(readOnly = true)
public class AdoptionRateService {
    private static final Logger log = LoggerFactory.getLogger(AdoptionRateService.class);

    // Standard adoption windows in days
    private static final int[] DEFAULT_WINDOWS = {7, 30, 90};

    // Weights for adoption score calculation (more recent windows have higher weight)
    private static final Map<Integer, Double> WINDOW_WEIGHTS = Map.of(
            7, 0.5, // 50% weight for first week
            30, 0.3, // 30% weight for first month
            90, 0.2); // 20% weight for first quarter

    private final FeatureUsageRepository featureUsageRepository;
    private final ReleaseRepository releaseRepository;
    private final FeatureRepository featureRepository;

    public AdoptionRateService(
            FeatureUsageRepository featureUsageRepository,
            ReleaseRepository releaseRepository,
            FeatureRepository featureRepository) {
        this.featureUsageRepository = featureUsageRepository;
        this.releaseRepository = releaseRepository;
        this.featureRepository = featureRepository;
    }

    /**
     * Calculate comprehensive adoption rate for a feature.
     *
     * @param featureCode Feature code to analyze
     * @return AdoptionRateDto with adoption metrics
     * @throws ResourceNotFoundException if feature or release not found
     * @throws BadRequestException if feature has no release date
     */
    public AdoptionRateDto calculateAdoptionRate(String featureCode) {
        log.debug("Calculating adoption rate for feature: {}", featureCode);

        // Get feature and validate it has a release
        Feature feature = featureRepository
                .findByCode(featureCode)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + featureCode));

        if (feature.getRelease() == null) {
            throw new BadRequestException("Feature " + featureCode + " has no associated release");
        }

        Release release = feature.getRelease();
        Instant releaseDate = release.getReleasedAt();

        if (releaseDate == null) {
            throw new BadRequestException(
                    "Release " + release.getCode() + " has no release date. Cannot calculate adoption rate.");
        }

        log.info("Feature {} was released on {}. Calculating adoption metrics.", featureCode, releaseDate);

        // Calculate metrics for each window
        Map<Integer, AdoptionWindowMetrics> adoptionWindows = new HashMap<>();
        AdoptionWindowMetrics previousMetrics = null;

        for (int windowDays : DEFAULT_WINDOWS) {
            AdoptionWindowMetrics metrics = calculateMetricsForWindow(featureCode, releaseDate, windowDays);

            // Calculate growth rate compared to previous window
            double growthRate = 0.0;
            if (previousMetrics != null && previousMetrics.uniqueUsers() > 0) {
                growthRate = ((double) (metrics.uniqueUsers() - previousMetrics.uniqueUsers())
                                / previousMetrics.uniqueUsers())
                        * 100;
            }

            adoptionWindows.put(
                    windowDays,
                    new AdoptionWindowMetrics(
                            metrics.windowDays(),
                            metrics.uniqueUsers(),
                            metrics.totalUsage(),
                            metrics.adoptionRate(),
                            growthRate));

            previousMetrics = metrics;
        }

        // Calculate total unique users since release
        long totalUniqueUsers = featureUsageRepository.countUniqueUsersAfterRelease(featureCode, releaseDate, null);

        // Calculate overall adoption score
        double overallAdoptionScore = calculateAdoptionScore(adoptionWindows);

        // Calculate overall adoption growth rate (first week to 90 days)
        double adoptionGrowthRate = 0.0;
        AdoptionWindowMetrics firstWeek = adoptionWindows.get(7);
        AdoptionWindowMetrics ninetyDays = adoptionWindows.get(90);
        if (firstWeek != null && ninetyDays != null && firstWeek.uniqueUsers() > 0) {
            adoptionGrowthRate =
                    ((double) (ninetyDays.uniqueUsers() - firstWeek.uniqueUsers()) / firstWeek.uniqueUsers()) * 100;
        }

        log.info(
                "Adoption rate calculated: score={}, totalUsers={}, growthRate={}%",
                overallAdoptionScore, totalUniqueUsers, adoptionGrowthRate);

        return new AdoptionRateDto(
                featureCode, releaseDate, adoptionWindows, overallAdoptionScore, totalUniqueUsers, adoptionGrowthRate);
    }

    /**
     * Calculate adoption metrics for a specific time window.
     *
     * @param featureCode Feature code
     * @param releaseDate Release date
     * @param windowDays Number of days in the window
     * @return AdoptionWindowMetrics for the window
     */
    private AdoptionWindowMetrics calculateMetricsForWindow(String featureCode, Instant releaseDate, int windowDays) {
        Instant windowEnd = releaseDate.plus(windowDays, ChronoUnit.DAYS);

        // Count unique users in this window
        long uniqueUsers = featureUsageRepository.countUniqueUsersAfterRelease(featureCode, releaseDate, windowEnd);

        // Count total usage in this window
        long totalUsage = featureUsageRepository.countUsageAfterRelease(featureCode, releaseDate, windowEnd);

        // Calculate adoption rate
        // Note: This is simplified. In production, you'd compare against total active users
        // For now, we'll calculate rate based on usage intensity
        double adoptionRate = calculateAdoptionRatePercentage(uniqueUsers, totalUsage, windowDays);

        log.debug(
                "Window {} days: uniqueUsers={}, totalUsage={}, adoptionRate={}%",
                windowDays, uniqueUsers, totalUsage, adoptionRate);

        return new AdoptionWindowMetrics(windowDays, uniqueUsers, totalUsage, adoptionRate, 0.0);
    }

    /**
     * Calculate adoption rate percentage.
     * Uses a formula that considers both user count and usage intensity.
     *
     * @param uniqueUsers Number of unique users
     * @param totalUsage Total usage count
     * @param windowDays Window size in days
     * @return Adoption rate (0-100)
     */
    private double calculateAdoptionRatePercentage(long uniqueUsers, long totalUsage, int windowDays) {
        if (uniqueUsers == 0) {
            return 0.0;
        }

        // Calculate average usage per user
        double avgUsagePerUser = (double) totalUsage / uniqueUsers;

        // Normalize based on window size (expected: ~1 usage per user per day = 100% adoption)
        // This is a simplified model. Adjust the formula based on business requirements.
        double normalizedRate = (avgUsagePerUser / windowDays) * 100;

        // Cap at 100%
        return Math.min(normalizedRate, 100.0);
    }

    /**
     * Calculate overall adoption score (0-100) using weighted average of window metrics.
     *
     * @param adoptionWindows Map of window metrics
     * @return Overall adoption score (0-100)
     */
    private double calculateAdoptionScore(Map<Integer, AdoptionWindowMetrics> adoptionWindows) {
        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (Map.Entry<Integer, Double> entry : WINDOW_WEIGHTS.entrySet()) {
            int windowDays = entry.getKey();
            double weight = entry.getValue();
            AdoptionWindowMetrics metrics = adoptionWindows.get(windowDays);

            if (metrics != null) {
                // Use adoption rate from the window
                weightedSum += metrics.adoptionRate() * weight;
                totalWeight += weight;
            }
        }

        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    /**
     * Compare adoption metrics between multiple features.
     *
     * @param featureCodes List of feature codes to compare
     * @param windowDays Optional window size (defaults to 30 days)
     * @return List of AdoptionMetricsDto sorted by adoption score (descending)
     */
    public List<AdoptionMetricsDto> compareFeatures(List<String> featureCodes, Integer windowDays) {
        int window = (windowDays != null && windowDays > 0) ? windowDays : 30;

        log.info("Comparing adoption rates for {} features with {} day window", featureCodes.size(), window);

        List<AdoptionMetricsDto> comparisonList = new ArrayList<>();

        for (String featureCode : featureCodes) {
            try {
                AdoptionRateDto adoptionRate = calculateAdoptionRate(featureCode);

                // Extract metrics for the specified window
                AdoptionWindowMetrics windowMetrics =
                        adoptionRate.adoptionWindows().get(window);

                if (windowMetrics == null) {
                    log.warn("No metrics found for feature {} in {} day window", featureCode, window);
                    continue;
                }

                comparisonList.add(new AdoptionMetricsDto(
                        featureCode,
                        adoptionRate.releaseDate(),
                        window,
                        windowMetrics.uniqueUsers(),
                        windowMetrics.totalUsage(),
                        windowMetrics.adoptionRate(),
                        adoptionRate.overallAdoptionScore(),
                        windowMetrics.growthRate(),
                        null // Rank will be assigned after sorting
                        ));
            } catch (ResourceNotFoundException | BadRequestException e) {
                log.warn("Skipping feature {} due to error: {}", featureCode, e.getMessage());
                // Continue with other features
            }
        }

        // Sort by adoption score (descending) and assign ranks
        comparisonList.sort(
                Comparator.comparingDouble(AdoptionMetricsDto::adoptionScore).reversed());

        List<AdoptionMetricsDto> rankedList = new ArrayList<>();
        for (int i = 0; i < comparisonList.size(); i++) {
            AdoptionMetricsDto metrics = comparisonList.get(i);
            rankedList.add(new AdoptionMetricsDto(
                    metrics.featureCode(),
                    metrics.releaseDate(),
                    metrics.windowDays(),
                    metrics.uniqueUsers(),
                    metrics.totalUsage(),
                    metrics.adoptionRate(),
                    metrics.adoptionScore(),
                    metrics.growthRate(),
                    i + 1 // Rank (1-based)
                    ));
        }

        log.info("Comparison complete. {} features ranked.", rankedList.size());

        return rankedList;
    }

    /**
     * Calculate adoption rate for a specific custom window.
     *
     * @param featureCode Feature code
     * @param windowDays Custom window size in days
     * @return AdoptionWindowMetrics for the custom window
     */
    public Optional<AdoptionWindowMetrics> calculateCustomWindow(String featureCode, int windowDays) {
        try {
            Feature feature = featureRepository
                    .findByCode(featureCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + featureCode));

            if (feature.getRelease() == null || feature.getRelease().getReleasedAt() == null) {
                return Optional.empty();
            }

            Instant releaseDate = feature.getRelease().getReleasedAt();
            return Optional.of(calculateMetricsForWindow(featureCode, releaseDate, windowDays));
        } catch (Exception e) {
            log.error("Error calculating custom window for feature {}: {}", featureCode, e.getMessage());
            return Optional.empty();
        }
    }
}

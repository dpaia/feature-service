package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.api.models.ReleaseDashboardResponse;
import com.sivalabs.ft.features.api.models.ReleaseMetricsResponse;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import com.sivalabs.ft.features.domain.models.Priority;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final ReleaseRepository releaseRepository;
    private final FeatureRepository featureRepository;

    public DashboardService(ReleaseRepository releaseRepository, FeatureRepository featureRepository) {
        this.releaseRepository = releaseRepository;
        this.featureRepository = featureRepository;
    }

    public ReleaseDashboardResponse getReleaseDashboard(String releaseCode) {
        Release release = releaseRepository
                .findByCode(releaseCode)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found with code: " + releaseCode));

        List<Feature> features = featureRepository.findByReleaseId(release.getId());

        return new ReleaseDashboardResponse(
                release.getCode(),
                release.getDescription(),
                release.getStatus(),
                buildOverview(features, release),
                buildHealthIndicators(features, release),
                buildTimeline(release, features),
                buildFeatureBreakdown(features));
    }

    public ReleaseMetricsResponse getReleaseMetrics(String releaseCode) {
        Release release = releaseRepository
                .findByCode(releaseCode)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found with code: " + releaseCode));

        List<Feature> features = featureRepository.findByReleaseId(release.getId());

        return new ReleaseMetricsResponse(
                release.getCode(),
                release.getStatus(),
                calculateCompletionRate(features),
                buildVelocity(features, release),
                buildBlockedTime(features),
                buildWorkloadDistribution(features));
    }

    private ReleaseDashboardResponse.Overview buildOverview(List<Feature> features, Release release) {
        int totalFeatures = features.size();
        int completedFeatures = (int) features.stream()
                .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.DONE)
                .count();
        int inProgressFeatures = (int) features.stream()
                .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.IN_PROGRESS)
                .count();
        int blockedFeatures = (int) features.stream()
                .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.BLOCKED)
                .count();
        int pendingFeatures = (int) features.stream()
                .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.NOT_STARTED)
                .count();

        double completionPercentage =
                totalFeatures > 0 ? round((double) completedFeatures / totalFeatures * 100, 1) : 0.0;

        int estimatedDaysRemaining = calculateEstimatedDaysRemaining(features, release);

        return new ReleaseDashboardResponse.Overview(
                totalFeatures,
                completedFeatures,
                inProgressFeatures,
                blockedFeatures,
                pendingFeatures,
                completionPercentage,
                estimatedDaysRemaining);
    }

    private ReleaseDashboardResponse.HealthIndicators buildHealthIndicators(List<Feature> features, Release release) {
        String timelineAdherence = calculateTimelineAdherence(features, release);
        String riskLevel = calculateRiskLevel(features);
        int blockedFeatures = (int) features.stream()
                .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.BLOCKED)
                .count();

        return new ReleaseDashboardResponse.HealthIndicators(timelineAdherence, riskLevel, blockedFeatures);
    }

    private ReleaseDashboardResponse.Timeline buildTimeline(Release release, List<Feature> features) {
        Instant startDate = release.getCreatedAt();
        Instant plannedEndDate = calculatePlannedEndDate(release.getCreatedAt());
        Instant estimatedEndDate = calculateEstimatedEndDate(release, features);
        Instant actualEndDate = release.getStatus() == ReleaseStatus.RELEASED ? release.getReleasedAt() : null;

        return new ReleaseDashboardResponse.Timeline(startDate, plannedEndDate, estimatedEndDate, actualEndDate);
    }

    private ReleaseDashboardResponse.FeatureBreakdown buildFeatureBreakdown(List<Feature> features) {
        Map<String, Integer> byStatus = features.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getPlanningStatus() != null
                                ? f.getPlanningStatus().name()
                                : "NOT_STARTED",
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)));

        Map<String, Integer> byOwner = features.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getFeatureOwner() != null ? f.getFeatureOwner() : "unassigned",
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)));

        Map<String, Integer> byPriority = features.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getPriority() != null ? f.getPriority().name() : Priority.MEDIUM.name(),
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)));

        return new ReleaseDashboardResponse.FeatureBreakdown(byStatus, byOwner, byPriority);
    }

    private ReleaseMetricsResponse.Velocity buildVelocity(List<Feature> features, Release release) {
        double featuresPerWeek = calculateFeaturesPerWeek(features, release);
        double averageCycleTime = calculateAverageCycleTime(features);

        return new ReleaseMetricsResponse.Velocity(featuresPerWeek, averageCycleTime);
    }

    private ReleaseMetricsResponse.BlockedTime buildBlockedTime(List<Feature> features) {
        List<Feature> blockedFeatures = features.stream()
                .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.BLOCKED)
                .toList();

        int totalBlockedDays = blockedFeatures.size() * 5; // Simplified calculation
        double averageBlockedDuration = blockedFeatures.isEmpty() ? 0.0 : 5.0; // Simplified
        double percentageOfTime = features.isEmpty()
                ? 0.0
                : round((double) totalBlockedDays / (features.size() * 30) * 100, 1); // Simplified

        Map<String, Integer> blockageReasons = blockedFeatures.stream()
                .filter(f -> f.getBlockageReason() != null)
                .collect(Collectors.groupingBy(
                        Feature::getBlockageReason,
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)));

        return new ReleaseMetricsResponse.BlockedTime(
                totalBlockedDays, averageBlockedDuration, percentageOfTime, blockageReasons);
    }

    private ReleaseMetricsResponse.WorkloadDistribution buildWorkloadDistribution(List<Feature> features) {
        Map<String, List<Feature>> featuresByOwner = features.stream()
                .collect(Collectors.groupingBy(f -> f.getFeatureOwner() != null ? f.getFeatureOwner() : "unassigned"));

        List<ReleaseMetricsResponse.OwnerWorkload> ownerWorkloads = featuresByOwner.entrySet().stream()
                .map(entry -> {
                    String owner = entry.getKey();
                    List<Feature> ownerFeatures = entry.getValue();

                    int assignedFeatures = ownerFeatures.size();
                    int completedFeatures = (int) ownerFeatures.stream()
                            .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.DONE)
                            .count();
                    int inProgressFeatures = (int) ownerFeatures.stream()
                            .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.IN_PROGRESS)
                            .count();
                    int blockedFeatures = (int) ownerFeatures.stream()
                            .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.BLOCKED)
                            .count();

                    double utilizationRate =
                            assignedFeatures > 0 ? round((double) completedFeatures / assignedFeatures * 100, 1) : 0.0;

                    return new ReleaseMetricsResponse.OwnerWorkload(
                            owner,
                            assignedFeatures,
                            completedFeatures,
                            inProgressFeatures,
                            blockedFeatures,
                            utilizationRate);
                })
                .toList();

        return new ReleaseMetricsResponse.WorkloadDistribution(ownerWorkloads);
    }

    private String calculateTimelineAdherence(List<Feature> features, Release release) {
        if (release.getStatus() == ReleaseStatus.RELEASED) {
            return "ON_TRACK";
        }

        Instant now = Instant.now();
        Instant plannedEndDate = calculatePlannedEndDate(release.getCreatedAt());
        double completionPercentage = calculateCompletionRate(features);

        if (now.isAfter(plannedEndDate)) {
            return "DELAYED";
        }

        long daysUntilDeadline = ChronoUnit.DAYS.between(now, plannedEndDate);
        if (daysUntilDeadline <= 14 && completionPercentage < 70) {
            return "AT_RISK";
        }

        return "ON_TRACK";
    }

    private String calculateRiskLevel(List<Feature> features) {
        if (features.isEmpty()) {
            return "LOW";
        }

        int totalFeatures = features.size();
        int blockedFeatures = (int) features.stream()
                .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.BLOCKED)
                .count();
        double completionPercentage = calculateCompletionRate(features);

        if (blockedFeatures > totalFeatures * 0.5 || completionPercentage < 30) {
            return "HIGH";
        }
        if (blockedFeatures > totalFeatures * 0.25 || completionPercentage < 60) {
            return "MEDIUM";
        }

        return "LOW";
    }

    private int calculateEstimatedDaysRemaining(List<Feature> features, Release release) {
        if (release.getStatus() == ReleaseStatus.RELEASED) {
            return 0;
        }

        double featuresPerWeek = calculateFeaturesPerWeek(features, release);
        if (featuresPerWeek <= 0) {
            return 0;
        }

        int remainingFeatures = (int) features.stream()
                .filter(f -> f.getPlanningStatus() != FeaturePlanningStatus.DONE)
                .count();

        double weeksRemaining = remainingFeatures / featuresPerWeek;
        return (int) Math.ceil(weeksRemaining * 5); // 5 business days per week
    }

    private double calculateFeaturesPerWeek(List<Feature> features, Release release) {
        final Instant startDate = release.getCreatedAt();
        final Instant endDate = (release.getStatus() == ReleaseStatus.RELEASED && release.getReleasedAt() != null)
                ? release.getReleasedAt()
                : Instant.now();

        final double businessWeeksElapsed = calculateBusinessWeeksElapsed(startDate, endDate);
        if (businessWeeksElapsed < 2.0) {
            return 0.0; // Requires at least 2 weeks of data
        }

        int completedFeatures = (int) features.stream()
                .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.DONE)
                .count();

        return round((double) completedFeatures / businessWeeksElapsed, 1);
    }

    private double calculateAverageCycleTime(List<Feature> features) {
        List<Feature> completedFeatures = features.stream()
                .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.DONE)
                .toList();

        if (completedFeatures.isEmpty()) {
            return 0.0;
        }

        double totalDays = completedFeatures.stream()
                .mapToLong(f -> ChronoUnit.DAYS.between(
                        f.getCreatedAt(), f.getUpdatedAt() != null ? f.getUpdatedAt() : Instant.now()))
                .average()
                .orElse(0.0);

        return round(totalDays, 1);
    }

    private double calculateCompletionRate(List<Feature> features) {
        if (features.isEmpty()) {
            return 0.0;
        }

        int completedFeatures = (int) features.stream()
                .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.DONE)
                .count();

        return round((double) completedFeatures / features.size() * 100, 1);
    }

    private Instant calculatePlannedEndDate(Instant startDate) {
        LocalDate startLocalDate = startDate.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = addBusinessDays(startLocalDate, 90);
        return endDate.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant calculateEstimatedEndDate(Release release, List<Feature> features) {
        if (release.getStatus() == ReleaseStatus.RELEASED) {
            return release.getReleasedAt();
        }

        int estimatedDaysRemaining = calculateEstimatedDaysRemaining(features, release);
        LocalDate currentDate = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate estimatedEndDate = addBusinessDays(currentDate, estimatedDaysRemaining);

        return estimatedEndDate.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private double calculateBusinessWeeksElapsed(Instant startDate, Instant endDate) {
        long businessDaysElapsed = calculateBusinessDaysElapsed(startDate, endDate);
        return businessDaysElapsed / 5.0;
    }

    private long calculateBusinessDaysElapsed(Instant startDate, Instant endDate) {
        LocalDate start = startDate.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = endDate.atZone(ZoneOffset.UTC).toLocalDate();

        long businessDays = 0;
        LocalDate current = start;

        while (!current.isAfter(end)) {
            if (current.getDayOfWeek() != DayOfWeek.SATURDAY && current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                businessDays++;
            }
            current = current.plusDays(1);
        }

        return businessDays;
    }

    private LocalDate addBusinessDays(LocalDate startDate, int businessDays) {
        LocalDate result = startDate;
        int addedDays = 0;

        while (addedDays < businessDays) {
            result = result.plusDays(1);
            if (result.getDayOfWeek() != DayOfWeek.SATURDAY && result.getDayOfWeek() != DayOfWeek.SUNDAY) {
                addedDays++;
            }
        }

        return result;
    }

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}

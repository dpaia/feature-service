package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.ReleaseDashboardResponseDto;
import com.sivalabs.ft.features.domain.dtos.ReleaseMetricsResponseDto;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import com.sivalabs.ft.features.domain.models.RiskLevel;
import com.sivalabs.ft.features.domain.models.TimelineAdherence;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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

    public ReleaseDashboardResponseDto getReleaseDashboard(String releaseCode) {
        Release release = releaseRepository
                .findByCode(releaseCode)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found with code: " + releaseCode));

        List<Feature> features = featureRepository.findByReleaseCode(releaseCode);

        var overview = buildOverview(features);
        var healthIndicators = buildHealthIndicators(release, features);
        var timeline = buildTimeline(release);
        var featureBreakdown = buildFeatureBreakdown(features);

        return new ReleaseDashboardResponseDto(
                release.getCode(),
                release.getDescription(),
                release.getStatus(),
                overview,
                healthIndicators,
                timeline,
                featureBreakdown);
    }

    public ReleaseMetricsResponseDto getReleaseMetrics(String releaseCode) {
        Release release = releaseRepository
                .findByCode(releaseCode)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found with code: " + releaseCode));

        List<Feature> features = featureRepository.findByReleaseCode(releaseCode);

        double completionRate = calculateCompletionRate(features);
        var velocity = calculateVelocity(features, release);
        var blockedTime = calculateBlockedTime(features);
        var workloadDistribution = calculateWorkloadDistribution(features);

        return new ReleaseMetricsResponseDto(
                release.getCode(), release.getStatus(), completionRate, velocity, blockedTime, workloadDistribution);
    }

    private ReleaseDashboardResponseDto.OverviewDto buildOverview(List<Feature> features) {
        int totalFeatures = features.size();
        int completedFeatures = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.RELEASED)
                .count();
        int inProgressFeatures = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.IN_PROGRESS)
                .count();
        int blockedFeatures = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.ON_HOLD)
                .count();
        int pendingFeatures = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.NEW)
                .count();

        double completionPercentage = totalFeatures > 0 ? (double) completedFeatures / totalFeatures * 100 : 0;

        return new ReleaseDashboardResponseDto.OverviewDto(
                totalFeatures,
                completedFeatures,
                inProgressFeatures,
                blockedFeatures,
                pendingFeatures,
                completionPercentage);
    }

    private com.sivalabs.ft.features.domain.dtos.HealthIndicatorsDto buildHealthIndicators(
            Release release, List<Feature> features) {
        TimelineAdherence timelineAdherence = calculateTimelineAdherence(release);
        RiskLevel riskLevel = calculateRiskLevel(features);
        int blockedFeatures = (int) features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.ON_HOLD)
                .count();

        return new com.sivalabs.ft.features.domain.dtos.HealthIndicatorsDto(
                timelineAdherence, riskLevel, blockedFeatures);
    }

    private ReleaseDashboardResponseDto.TimelineDto buildTimeline(Release release) {
        Instant startDate = release.getCreatedAt();
        Instant plannedEndDate = release.getReleasedAt(); // This should be updated to have separate planned end date
        Instant estimatedEndDate = calculateEstimatedEndDate(release);
        Instant actualEndDate = release.getStatus().name().equals("RELEASED") ? release.getReleasedAt() : null;

        return new ReleaseDashboardResponseDto.TimelineDto(startDate, plannedEndDate, estimatedEndDate, actualEndDate);
    }

    private ReleaseDashboardResponseDto.FeatureBreakdownDto buildFeatureBreakdown(List<Feature> features) {
        Map<String, Integer> byStatus = features.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getStatus().name(),
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)));

        Map<String, Integer> byOwner = features.stream()
                .filter(f -> f.getAssignedTo() != null)
                .collect(Collectors.groupingBy(
                        Feature::getAssignedTo, Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)));

        return new ReleaseDashboardResponseDto.FeatureBreakdownDto(byStatus, byOwner);
    }

    private double calculateCompletionRate(List<Feature> features) {
        if (features.isEmpty()) return 0.0;
        long completedFeatures = features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.RELEASED)
                .count();
        return (double) completedFeatures / features.size() * 100;
    }

    private ReleaseMetricsResponseDto.VelocityDto calculateVelocity(List<Feature> features, Release release) {
        // Calculate features completed per week
        long daysSinceStart = ChronoUnit.DAYS.between(release.getCreatedAt(), Instant.now());
        double weeks = Math.max(1, daysSinceStart / 7.0);
        long completedFeatures = features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.RELEASED)
                .count();
        double featuresPerWeek = completedFeatures / weeks;

        // Calculate average cycle time from released features
        double averageCycleTime = calculateAverageCycleTime(features);

        return new ReleaseMetricsResponseDto.VelocityDto(featuresPerWeek, averageCycleTime);
    }

    private double calculateAverageCycleTime(List<Feature> features) {
        List<Feature> completedFeatures = features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.RELEASED)
                .toList();

        if (completedFeatures.isEmpty()) {
            return 0.0;
        }

        double averageDays = completedFeatures.stream()
                .mapToLong(f -> ChronoUnit.DAYS.between(f.getCreatedAt(), f.getUpdatedAt()))
                .average()
                .orElse(0.0);

        return averageDays;
    }

    private ReleaseMetricsResponseDto.BlockedTimeDto calculateBlockedTime(List<Feature> features) {
        List<Feature> blockedFeatures = features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.ON_HOLD)
                .toList();

        if (blockedFeatures.isEmpty()) {
            return new ReleaseMetricsResponseDto.BlockedTimeDto(0, 0.0);
        }

        // Calculate total blocked days from last update (when transitioned to ON_HOLD) to current time
        long totalBlockedDays = blockedFeatures.stream()
                .mapToLong(f -> ChronoUnit.DAYS.between(f.getUpdatedAt(), Instant.now()))
                .sum();

        // Calculate average blocked duration per feature
        double averageBlockedDuration = (double) totalBlockedDays / blockedFeatures.size();

        return new ReleaseMetricsResponseDto.BlockedTimeDto((int) totalBlockedDays, averageBlockedDuration);
    }

    private ReleaseMetricsResponseDto.WorkloadDistributionDto calculateWorkloadDistribution(List<Feature> features) {
        Map<String, List<Feature>> featuresByOwner = features.stream()
                .filter(f -> f.getAssignedTo() != null)
                .collect(Collectors.groupingBy(Feature::getAssignedTo));

        List<ReleaseMetricsResponseDto.OwnerWorkloadDto> workloads = featuresByOwner.entrySet().stream()
                .map(entry -> {
                    String owner = entry.getKey();
                    List<Feature> ownerFeatures = entry.getValue();

                    int assignedFeatures = ownerFeatures.size();
                    int completedFeatures = (int) ownerFeatures.stream()
                            .filter(f -> f.getStatus() == FeatureStatus.RELEASED)
                            .count();
                    int inProgressFeatures = (int) ownerFeatures.stream()
                            .filter(f -> f.getStatus() == FeatureStatus.IN_PROGRESS)
                            .count();
                    int blockedFeatures = (int) ownerFeatures.stream()
                            .filter(f -> f.getStatus() == FeatureStatus.ON_HOLD)
                            .count();
                    // Active rate = features not blocked / total assigned
                    double utilizationRate = assignedFeatures > 0
                            ? Math.round((double) (assignedFeatures - blockedFeatures) / assignedFeatures * 100 * 100.0)
                                    / 100.0
                            : 0.0;

                    return new ReleaseMetricsResponseDto.OwnerWorkloadDto(
                            owner,
                            assignedFeatures,
                            completedFeatures,
                            inProgressFeatures,
                            blockedFeatures,
                            utilizationRate);
                })
                .toList();

        return new ReleaseMetricsResponseDto.WorkloadDistributionDto(workloads);
    }

    private TimelineAdherence calculateTimelineAdherence(Release release) {
        if (release.getReleasedAt() == null) {
            return TimelineAdherence.ON_SCHEDULE;
        }

        // Mock calculation - check if release is delayed
        long daysSinceCreation = ChronoUnit.DAYS.between(release.getCreatedAt(), Instant.now());
        if (daysSinceCreation > 90) {
            return TimelineAdherence.DELAYED;
        }
        return TimelineAdherence.ON_SCHEDULE;
    }

    private RiskLevel calculateRiskLevel(List<Feature> features) {
        long blockedFeatures = features.stream()
                .filter(f -> f.getStatus() == FeatureStatus.ON_HOLD)
                .count();
        double blockedPercentage = features.size() > 0 ? (double) blockedFeatures / features.size() : 0;

        if (blockedPercentage > 0.3) return RiskLevel.HIGH;
        if (blockedPercentage > 0.1) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    private Instant calculateEstimatedEndDate(Release release) {
        // Mock calculation - add 3 months to creation date
        return release.getCreatedAt().plus(90, ChronoUnit.DAYS);
    }
}

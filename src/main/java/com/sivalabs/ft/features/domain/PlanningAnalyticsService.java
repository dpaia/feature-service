package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.CapacityPlanningResponseDto;
import com.sivalabs.ft.features.domain.dtos.PlanningHealthResponseDto;
import com.sivalabs.ft.features.domain.dtos.PlanningTrendsResponseDto;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.models.OverallocationRisk;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PlanningAnalyticsService {

    private final ReleaseRepository releaseRepository;
    private final FeatureRepository featureRepository;

    public PlanningAnalyticsService(ReleaseRepository releaseRepository, FeatureRepository featureRepository) {
        this.releaseRepository = releaseRepository;
        this.featureRepository = featureRepository;
    }

    public PlanningHealthResponseDto getPlanningHealth() {
        List<Release> allReleases = releaseRepository.findAll();

        var releasesByStatus = buildReleasesByStatus(allReleases);
        var atRiskReleases = buildAtRiskReleases(allReleases);
        var planningAccuracy = buildPlanningAccuracy(allReleases);

        return new PlanningHealthResponseDto(releasesByStatus, atRiskReleases, planningAccuracy);
    }

    public PlanningTrendsResponseDto getPlanningTrends() {
        List<Release> allReleases = releaseRepository.findAll();

        var releasesCompleted = buildReleasesCompletedTrend(allReleases);
        var averageReleaseDuration = buildAverageReleaseDurationTrend(allReleases);
        var planningAccuracyTrend = buildPlanningAccuracyTrend(allReleases);

        return new PlanningTrendsResponseDto(releasesCompleted, averageReleaseDuration, planningAccuracyTrend);
    }

    public CapacityPlanningResponseDto getCapacityPlanning() {
        List<Feature> allFeatures = featureRepository.findAll();
        List<Release> allReleases = releaseRepository.findAll();

        // Build workload first, then use it to calculate overall capacity
        var workloadByOwner = buildWorkloadByOwner(allFeatures);
        var overallCapacity = buildOverallCapacity(workloadByOwner);
        var commitments = buildCommitments(allReleases, allFeatures);
        var overallocationWarnings = buildOverallocationWarnings(workloadByOwner);

        return new CapacityPlanningResponseDto(overallCapacity, workloadByOwner, commitments, overallocationWarnings);
    }

    private Map<String, Integer> buildReleasesByStatus(List<Release> releases) {
        Map<String, Integer> statusCounts = new HashMap<>();
        statusCounts.put("DRAFT", 0);
        statusCounts.put("IN_PROGRESS", 0);
        statusCounts.put("RELEASED", 0);
        statusCounts.put("CANCELLED", 0);

        for (Release release : releases) {
            String status = release.getStatus().name();
            statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
        }

        return statusCounts;
    }

    private PlanningHealthResponseDto.AtRiskReleasesDto buildAtRiskReleases(List<Release> releases) {
        Instant now = Instant.now();
        int overdue = 0;
        int criticallyDelayed = 0;

        for (Release release : releases) {
            if (release.getStatus() != ReleaseStatus.RELEASED && release.getReleasedAt() != null) {
                long daysOverdue = ChronoUnit.DAYS.between(release.getReleasedAt(), now);
                if (daysOverdue > 0) {
                    overdue++;
                    if (daysOverdue > 30) {
                        criticallyDelayed++;
                    }
                }
            }
        }

        // Total at-risk releases includes both overdue and critically delayed
        int total = overdue + criticallyDelayed;
        return new PlanningHealthResponseDto.AtRiskReleasesDto(overdue, criticallyDelayed, total);
    }

    private PlanningHealthResponseDto.PlanningAccuracyDto buildPlanningAccuracy(List<Release> releases) {
        List<Release> completedReleases = releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.RELEASED)
                .toList();

        if (completedReleases.isEmpty()) {
            return new PlanningHealthResponseDto.PlanningAccuracyDto(0.0, 0.0, 0.0);
        }

        int onTimeReleases = 0;
        double totalDelay = 0;
        int delayedReleases = 0;

        for (Release release : completedReleases) {
            if (release.getReleasedAt() != null) {
                // Mock calculation - assume creation date + 90 days is the planned date
                Instant plannedDate = release.getCreatedAt().plus(90, ChronoUnit.DAYS);
                long delay = ChronoUnit.DAYS.between(plannedDate, release.getReleasedAt());

                if (delay <= 0) {
                    onTimeReleases++;
                } else {
                    totalDelay += delay;
                    delayedReleases++;
                }
            }
        }

        double onTimeDelivery = (double) onTimeReleases / completedReleases.size() * 100;
        // Average delay only for delayed releases
        double averageDelay = delayedReleases > 0 ? totalDelay / delayedReleases : 0;
        // Estimation accuracy = on-time delivery rate
        double estimationAccuracy = onTimeDelivery;

        return new PlanningHealthResponseDto.PlanningAccuracyDto(onTimeDelivery, averageDelay, estimationAccuracy);
    }

    private PlanningTrendsResponseDto.ReleasesCompletedDto buildReleasesCompletedTrend(List<Release> releases) {
        List<Release> completedReleases = releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.RELEASED)
                .toList();

        Map<String, Long> releasesByMonth = completedReleases.stream()
                .filter(r -> r.getReleasedAt() != null)
                .collect(Collectors.groupingBy(
                        r -> LocalDate.ofInstant(r.getReleasedAt(), ZoneOffset.UTC)
                                .format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.counting()));

        List<PlanningTrendsResponseDto.TrendDataPointDto> trend = releasesByMonth.entrySet().stream()
                .map(entry -> new PlanningTrendsResponseDto.TrendDataPointDto(entry.getKey(), entry.getValue()))
                .toList();

        return new PlanningTrendsResponseDto.ReleasesCompletedDto(trend, completedReleases.size());
    }

    private PlanningTrendsResponseDto.AverageReleaseDurationDto buildAverageReleaseDurationTrend(
            List<Release> releases) {
        List<Release> completedReleases = releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.RELEASED && r.getReleasedAt() != null)
                .toList();

        Map<String, List<Release>> releasesByMonth = completedReleases.stream()
                .collect(Collectors.groupingBy(r -> LocalDate.ofInstant(r.getReleasedAt(), ZoneOffset.UTC)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM"))));

        List<PlanningTrendsResponseDto.TrendDataPointDto> trend = releasesByMonth.entrySet().stream()
                .map(entry -> {
                    double avgDuration = entry.getValue().stream()
                            .mapToLong(r -> ChronoUnit.DAYS.between(r.getCreatedAt(), r.getReleasedAt()))
                            .average()
                            .orElse(0.0);
                    return new PlanningTrendsResponseDto.TrendDataPointDto(entry.getKey(), avgDuration);
                })
                .toList();

        double currentAverage = completedReleases.stream()
                .mapToLong(r -> ChronoUnit.DAYS.between(r.getCreatedAt(), r.getReleasedAt()))
                .average()
                .orElse(0.0);

        return new PlanningTrendsResponseDto.AverageReleaseDurationDto(trend, currentAverage);
    }

    private PlanningTrendsResponseDto.PlanningAccuracyTrendDto buildPlanningAccuracyTrend(List<Release> releases) {
        List<Release> completedReleases = releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.RELEASED && r.getReleasedAt() != null)
                .toList();

        Map<String, List<Release>> releasesByMonth = completedReleases.stream()
                .collect(Collectors.groupingBy(r -> LocalDate.ofInstant(r.getReleasedAt(), ZoneOffset.UTC)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM"))));

        List<PlanningTrendsResponseDto.TrendDataPointDto> onTimeDelivery = releasesByMonth.entrySet().stream()
                .map(entry -> {
                    List<Release> monthReleases = entry.getValue();
                    long onTimeCount = monthReleases.stream()
                            .filter(r -> {
                                Instant plannedDate = r.getCreatedAt().plus(90, ChronoUnit.DAYS);
                                return !r.getReleasedAt().isAfter(plannedDate);
                            })
                            .count();
                    double onTimePercentage = (double) onTimeCount / monthReleases.size() * 100;
                    return new PlanningTrendsResponseDto.TrendDataPointDto(entry.getKey(), onTimePercentage);
                })
                .toList();

        return new PlanningTrendsResponseDto.PlanningAccuracyTrendDto(onTimeDelivery);
    }

    private CapacityPlanningResponseDto.OverallCapacityDto buildOverallCapacity(
            List<CapacityPlanningResponseDto.WorkloadByOwnerDto> workloadByOwner) {
        int totalResources = workloadByOwner.size();

        if (totalResources == 0) {
            return new CapacityPlanningResponseDto.OverallCapacityDto(0, 0.0, 100.0, 0);
        }

        // Calculate average utilization rate across all resources
        double totalUtilization = workloadByOwner.stream()
                .mapToDouble(CapacityPlanningResponseDto.WorkloadByOwnerDto::utilizationRate)
                .sum();
        double averageUtilizationRate = totalUtilization / totalResources;

        // Calculate available capacity
        double availableCapacity = 100 - averageUtilizationRate;

        // Count resources that are overallocated (utilization > 100%)
        int overallocatedResources = (int)
                workloadByOwner.stream().filter(w -> w.utilizationRate() > 100).count();

        return new CapacityPlanningResponseDto.OverallCapacityDto(
                totalResources, averageUtilizationRate, availableCapacity, overallocatedResources);
    }

    private List<CapacityPlanningResponseDto.WorkloadByOwnerDto> buildWorkloadByOwner(List<Feature> features) {
        Map<String, List<Feature>> featuresByOwner = features.stream()
                .filter(f -> f.getAssignedTo() != null)
                .collect(Collectors.groupingBy(Feature::getAssignedTo));

        final int DEFAULT_CAPACITY = 10; // Configurable capacity per resource

        return featuresByOwner.entrySet().stream()
                .map(entry -> {
                    String owner = entry.getKey();
                    List<Feature> ownerFeatures = entry.getValue();

                    int currentWorkload = ownerFeatures.size();
                    int capacity = DEFAULT_CAPACITY;
                    double utilizationRate = Math.round((double) currentWorkload / capacity * 100 * 100.0) / 100.0;

                    OverallocationRisk risk = OverallocationRisk.NONE;
                    if (utilizationRate > 120) risk = OverallocationRisk.HIGH;
                    else if (utilizationRate > 100) risk = OverallocationRisk.MEDIUM;
                    else if (utilizationRate > 80) risk = OverallocationRisk.LOW;

                    return new CapacityPlanningResponseDto.WorkloadByOwnerDto(
                            owner, currentWorkload, capacity, utilizationRate, risk);
                })
                .toList();
    }

    private CapacityPlanningResponseDto.CommitmentsDto buildCommitments(
            List<Release> releases, List<Feature> features) {
        int activeReleases = (int) releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.IN_PROGRESS)
                .count();

        int plannedReleases = (int) releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.DRAFT)
                .count();

        int totalFeatures = features.size();

        // Estimate effort based on feature count and average complexity
        // Average story points per feature = 5.5, assume 1 point = 1 effort unit
        double estimatedEffort = totalFeatures * 5.5;

        return new CapacityPlanningResponseDto.CommitmentsDto(
                activeReleases, plannedReleases, totalFeatures, estimatedEffort);
    }

    private List<CapacityPlanningResponseDto.OverallocationWarningDto> buildOverallocationWarnings(
            List<CapacityPlanningResponseDto.WorkloadByOwnerDto> workloads) {

        return workloads.stream()
                .filter(w -> w.utilizationRate() > 100)
                .map(w -> {
                    String severity = w.utilizationRate() > 120 ? "HIGH" : "MEDIUM";
                    return new CapacityPlanningResponseDto.OverallocationWarningDto(
                            w.owner(), severity, w.utilizationRate());
                })
                .toList();
    }
}

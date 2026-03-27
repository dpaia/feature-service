package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.ApplicationProperties;
import com.sivalabs.ft.features.api.models.CapacityPlanningResponse;
import com.sivalabs.ft.features.api.models.PlanningHealthResponse;
import com.sivalabs.ft.features.api.models.PlanningTrendsResponse;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PlanningAnalyticsService {

    private final ReleaseRepository releaseRepository;
    private final FeatureRepository featureRepository;
    private final ApplicationProperties applicationProperties;

    public PlanningAnalyticsService(
            ReleaseRepository releaseRepository,
            FeatureRepository featureRepository,
            ApplicationProperties applicationProperties) {
        this.releaseRepository = releaseRepository;
        this.featureRepository = featureRepository;
        this.applicationProperties = applicationProperties;
    }

    public PlanningHealthResponse getPlanningHealth() {
        List<Release> allReleases = releaseRepository.findAll();

        Map<String, Integer> releasesByStatus = allReleases.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getStatus().name(),
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)));

        Map<String, Integer> releaseStatistics = new HashMap<>();
        for (ReleaseStatus possibleStatus : ReleaseStatus.values()) {
            releaseStatistics.put(possibleStatus.name(), releasesByStatus.getOrDefault(possibleStatus.name(), 0));
        }

        PlanningHealthResponse.AtRiskReleases atRiskReleases = calculateAtRiskReleases(allReleases);
        PlanningHealthResponse.PlanningAccuracy planningAccuracy = calculatePlanningAccuracy(allReleases);

        return new PlanningHealthResponse(releaseStatistics, atRiskReleases, planningAccuracy);
    }

    public PlanningTrendsResponse getPlanningTrends() {
        List<Release> allReleases = releaseRepository.findAll();

        PlanningTrendsResponse.ReleasesCompleted releasesCompleted = calculateReleasesCompletedTrend(allReleases);
        PlanningTrendsResponse.AverageReleaseDuration averageReleaseDuration =
                calculateAverageReleaseDurationTrend(allReleases);
        PlanningTrendsResponse.PlanningAccuracyTrend planningAccuracyTrend =
                calculatePlanningAccuracyTrend(allReleases);

        return new PlanningTrendsResponse(releasesCompleted, averageReleaseDuration, planningAccuracyTrend);
    }

    public CapacityPlanningResponse getCapacityPlanning() {
        List<Feature> allFeatures = featureRepository.findAll();
        List<Release> activeReleases = releaseRepository.findAll().stream()
                .filter(r -> r.getStatus() == ReleaseStatus.IN_PROGRESS || r.getStatus() == ReleaseStatus.DRAFT)
                .toList();

        CapacityPlanningResponse.OverallCapacity overallCapacity = calculateOverallCapacity(allFeatures);
        List<CapacityPlanningResponse.WorkloadByOwner> workloadByOwner = calculateWorkloadByOwner(allFeatures);
        CapacityPlanningResponse.Commitments commitments = calculateCommitments(activeReleases, allFeatures);
        List<CapacityPlanningResponse.OverallocationWarning> overallocationWarnings =
                calculateOverallocationWarnings(workloadByOwner);

        return new CapacityPlanningResponse(overallCapacity, workloadByOwner, commitments, overallocationWarnings);
    }

    private PlanningHealthResponse.AtRiskReleases calculateAtRiskReleases(List<Release> releases) {
        Instant now = Instant.now();
        int draftOverdueDays = applicationProperties.planning().draftOverdueDays();
        int draftCriticallyDelayedDays = applicationProperties.planning().draftCriticallyDelayedDays();

        int overdue = 0;
        int criticallyDelayed = 0;

        for (Release release : releases) {
            if (release.getStatus() == ReleaseStatus.RELEASED || release.getStatus() == ReleaseStatus.CANCELLED) {
                continue; // Skip released and cancelled releases
            }

            if (release.getStatus() == ReleaseStatus.DRAFT) {
                long ageInDays = ChronoUnit.DAYS.between(release.getCreatedAt(), now);
                if (ageInDays > draftCriticallyDelayedDays) {
                    criticallyDelayed++;
                } else if (ageInDays > draftOverdueDays) {
                    overdue++;
                }
            } else if (release.getStatus() == ReleaseStatus.IN_PROGRESS) {
                Instant plannedEndDate = calculatePlannedEndDate(release.getCreatedAt());
                if (now.isAfter(plannedEndDate)) {
                    overdue++;
                }
            }
        }

        return new PlanningHealthResponse.AtRiskReleases(overdue, criticallyDelayed, overdue + criticallyDelayed);
    }

    private PlanningHealthResponse.PlanningAccuracy calculatePlanningAccuracy(List<Release> releases) {
        List<Release> releasedReleases = releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.RELEASED)
                .toList();

        if (releasedReleases.isEmpty()) {
            return new PlanningHealthResponse.PlanningAccuracy(0.0, 0.0, 0.0);
        }

        int onTimeCount = 0;
        double totalDelay = 0.0;
        double totalEstimationAccuracy = 0.0;

        for (Release release : releasedReleases) {
            Instant plannedEndDate = calculatePlannedEndDate(release.getCreatedAt());
            Instant actualEndDate = release.getReleasedAt();

            if (actualEndDate != null) {
                long delayDays = ChronoUnit.DAYS.between(plannedEndDate, actualEndDate);
                if (delayDays <= 0) {
                    onTimeCount++;
                }
                totalDelay += Math.max(0, delayDays);

                // Simplified estimation accuracy calculation
                double accuracy = delayDays <= 0 ? 100.0 : Math.max(0, 100.0 - (delayDays * 2));
                totalEstimationAccuracy += accuracy;
            }
        }

        double onTimeDelivery = round((double) onTimeCount / releasedReleases.size() * 100, 1);
        double averageDelay = round(totalDelay / releasedReleases.size(), 1);
        double estimationAccuracy = round(totalEstimationAccuracy / releasedReleases.size(), 1);

        return new PlanningHealthResponse.PlanningAccuracy(onTimeDelivery, averageDelay, estimationAccuracy);
    }

    private PlanningTrendsResponse.ReleasesCompleted calculateReleasesCompletedTrend(List<Release> releases) {
        Map<String, Long> releasesByMonth = releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.RELEASED && r.getReleasedAt() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getReleasedAt().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.counting()));

        List<PlanningTrendsResponse.TrendData> trend = releasesByMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new PlanningTrendsResponse.TrendData(
                        entry.getKey(), entry.getValue().doubleValue()))
                .toList();

        int total = (int) releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.RELEASED)
                .count();

        return new PlanningTrendsResponse.ReleasesCompleted(trend, total);
    }

    private PlanningTrendsResponse.AverageReleaseDuration calculateAverageReleaseDurationTrend(List<Release> releases) {
        Map<String, List<Release>> releasesByMonth = releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.RELEASED && r.getReleasedAt() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getReleasedAt().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM"))));

        List<PlanningTrendsResponse.TrendData> trend = releasesByMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    double avgDuration = entry.getValue().stream()
                            .mapToLong(r -> ChronoUnit.DAYS.between(r.getCreatedAt(), r.getReleasedAt()))
                            .average()
                            .orElse(0.0);
                    return new PlanningTrendsResponse.TrendData(entry.getKey(), round(avgDuration, 1));
                })
                .toList();

        double current = releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.RELEASED && r.getReleasedAt() != null)
                .mapToLong(r -> ChronoUnit.DAYS.between(r.getCreatedAt(), r.getReleasedAt()))
                .average()
                .orElse(0.0);

        return new PlanningTrendsResponse.AverageReleaseDuration(trend, round(current, 1));
    }

    private PlanningTrendsResponse.PlanningAccuracyTrend calculatePlanningAccuracyTrend(List<Release> releases) {
        Map<String, List<Release>> releasesByMonth = releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.RELEASED && r.getReleasedAt() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getReleasedAt().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM"))));

        List<PlanningTrendsResponse.TrendData> onTimeDelivery = releasesByMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<Release> monthReleases = entry.getValue();
                    long onTimeCount = monthReleases.stream()
                            .filter(r -> {
                                Instant plannedEndDate = calculatePlannedEndDate(r.getCreatedAt());
                                return !r.getReleasedAt().isAfter(plannedEndDate);
                            })
                            .count();
                    double onTimePercentage = round((double) onTimeCount / monthReleases.size() * 100, 1);
                    return new PlanningTrendsResponse.TrendData(entry.getKey(), onTimePercentage);
                })
                .toList();

        return new PlanningTrendsResponse.PlanningAccuracyTrend(onTimeDelivery);
    }

    private CapacityPlanningResponse.OverallCapacity calculateOverallCapacity(List<Feature> features) {
        Set<String> uniqueOwners = features.stream()
                .map(Feature::getFeatureOwner)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int totalResources = uniqueOwners.size();

        // Simplified capacity calculation
        double totalUtilization = features.stream()
                .filter(f -> f.getFeatureOwner() != null)
                .collect(Collectors.groupingBy(Feature::getFeatureOwner))
                .values()
                .stream()
                .mapToDouble(ownerFeatures -> {
                    int assigned = ownerFeatures.size();
                    int completed = (int) ownerFeatures.stream()
                            .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.DONE)
                            .count();
                    return assigned > 0 ? (double) completed / assigned * 100 : 0;
                })
                .average()
                .orElse(0.0);

        double utilizationRate = round(totalUtilization, 1);
        double availableCapacity = round(100.0 - utilizationRate, 1);

        int overallocatedResources = (int) features.stream()
                .filter(f -> f.getFeatureOwner() != null)
                .collect(Collectors.groupingBy(Feature::getFeatureOwner))
                .entrySet()
                .stream()
                .filter(entry -> {
                    List<Feature> ownerFeatures = entry.getValue();
                    int assigned = ownerFeatures.size();
                    return assigned > 10; // Simplified threshold
                })
                .count();

        return new CapacityPlanningResponse.OverallCapacity(
                totalResources, utilizationRate, availableCapacity, overallocatedResources);
    }

    private List<CapacityPlanningResponse.WorkloadByOwner> calculateWorkloadByOwner(List<Feature> features) {
        Map<String, List<Feature>> featuresByOwner = features.stream()
                .filter(f -> f.getFeatureOwner() != null)
                .collect(Collectors.groupingBy(Feature::getFeatureOwner));

        return featuresByOwner.entrySet().stream()
                .map(entry -> {
                    String owner = entry.getKey();
                    List<Feature> ownerFeatures = entry.getValue();

                    int currentWorkload = ownerFeatures.size();
                    int capacity = 10; // Simplified capacity
                    double utilizationRate = round((double) currentWorkload / capacity * 100, 1);
                    int futureCommitments = currentWorkload + 2; // Simplified

                    String overallocationRisk = calculateOverallocationRisk(utilizationRate);

                    return new CapacityPlanningResponse.WorkloadByOwner(
                            owner, currentWorkload, capacity, utilizationRate, futureCommitments, overallocationRisk);
                })
                .toList();
    }

    private CapacityPlanningResponse.Commitments calculateCommitments(
            List<Release> activeReleases, List<Feature> allFeatures) {
        int activeReleasesCount = (int) activeReleases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.IN_PROGRESS)
                .count();

        int plannedReleasesCount = (int) activeReleases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.DRAFT)
                .count();

        int totalFeatures = allFeatures.size();
        double estimatedEffort = totalFeatures * 5.0; // Simplified: 5 days per feature

        return new CapacityPlanningResponse.Commitments(
                activeReleasesCount, plannedReleasesCount, totalFeatures, estimatedEffort);
    }

    private List<CapacityPlanningResponse.OverallocationWarning> calculateOverallocationWarnings(
            List<CapacityPlanningResponse.WorkloadByOwner> workloadByOwner) {

        double highThreshold =
                applicationProperties.planning().overallocationThresholds().highThreshold();

        return workloadByOwner.stream()
                .filter(workload -> workload.utilizationRate() >= highThreshold)
                .map(workload -> new CapacityPlanningResponse.OverallocationWarning(
                        workload.owner(), "HIGH", workload.utilizationRate()))
                .toList();
    }

    private String calculateOverallocationRisk(double utilizationRate) {
        double mediumThreshold =
                applicationProperties.planning().overallocationThresholds().mediumThreshold();
        double highThreshold =
                applicationProperties.planning().overallocationThresholds().highThreshold();

        if (utilizationRate >= highThreshold) {
            return "HIGH";
        } else if (utilizationRate >= mediumThreshold) {
            return "MEDIUM";
        } else {
            return "NONE";
        }
    }

    private Instant calculatePlannedEndDate(Instant startDate) {
        LocalDate startLocalDate = startDate.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = startLocalDate.plusDays(90); // Simplified: 90 calendar days
        return endDate.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}

package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.config.PlanningProperties;
import com.sivalabs.ft.features.domain.dtos.CapacityPlanningDto;
import com.sivalabs.ft.features.domain.dtos.CapacityPlanningDto.*;
import com.sivalabs.ft.features.domain.dtos.PlanningHealthDto;
import com.sivalabs.ft.features.domain.dtos.PlanningHealthDto.*;
import com.sivalabs.ft.features.domain.dtos.PlanningTrendsDto;
import com.sivalabs.ft.features.domain.dtos.PlanningTrendsDto.*;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class PlanningAnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(PlanningAnalyticsService.class);

    private final ReleaseRepository releaseRepository;
    private final FeatureRepository featureRepository;
    private final PlanningProperties properties;

    public PlanningAnalyticsService(
            ReleaseRepository releaseRepository, FeatureRepository featureRepository, PlanningProperties properties) {
        this.releaseRepository = releaseRepository;
        this.featureRepository = featureRepository;
        this.properties = properties;
    }

    public PlanningHealthDto getPlanningHealth() {
        log.info("Calculating planning health report");
        List<Release> allReleases = releaseRepository.findAll();

        Map<String, Integer> releasesByStatus = calculateReleasesByStatus(allReleases);
        AtRiskReleases atRiskReleases = calculateAtRiskReleases(allReleases);
        PlanningAccuracy planningAccuracy = calculatePlanningAccuracy(allReleases);

        return new PlanningHealthDto(releasesByStatus, atRiskReleases, planningAccuracy);
    }

    public PlanningTrendsDto getPlanningTrends() {
        log.info("Calculating planning trends");
        List<Release> allReleases = releaseRepository.findAll();

        ReleasesCompleted releasesCompleted = calculateReleasesCompleted(allReleases);
        AverageReleaseDuration averageReleaseDuration = calculateAverageReleaseDuration(allReleases);
        PlanningAccuracyTrend planningAccuracyTrend = calculatePlanningAccuracyTrend(allReleases);

        return new PlanningTrendsDto(releasesCompleted, averageReleaseDuration, planningAccuracyTrend);
    }

    public CapacityPlanningDto getCapacityPlanning() {
        log.info("Calculating capacity planning");
        List<Feature> allFeatures = featureRepository.findAll();
        List<Release> allReleases = releaseRepository.findAll();

        OverallCapacity overallCapacity = calculateOverallCapacity(allFeatures);
        List<OwnerWorkload> workloadByOwner = calculateWorkloadByOwner(allFeatures);
        Commitments commitments = calculateCommitments(allReleases, allFeatures);
        List<OverallocationWarning> overallocationWarnings = calculateOverallocationWarnings(workloadByOwner);

        return new CapacityPlanningDto(overallCapacity, workloadByOwner, commitments, overallocationWarnings);
    }

    private Map<String, Integer> calculateReleasesByStatus(List<Release> releases) {
        Map<String, Integer> map = releases.stream()
                .collect(Collectors.groupingBy(r -> r.getStatus().name(), Collectors.summingInt(r -> 1)));
        for (ReleaseStatus status : ReleaseStatus.values()) {
            map.putIfAbsent(status.name(), 0);
        }
        return map;
    }

    private AtRiskReleases calculateAtRiskReleases(List<Release> releases) {
        Instant now = Instant.now();
        int overdue = 0;
        int criticallyDelayed = 0;

        for (Release release : releases) {
            if (release.getStatus() == ReleaseStatus.DRAFT) {
                long days = Duration.between(release.getCreatedAt(), now).toDays();
                if (days > properties.draftCriticallyDelayedDays()) criticallyDelayed++;
                else if (days > properties.draftOverdueDays()) overdue++;
            } else if (release.getStatus() != ReleaseStatus.RELEASED
                    && release.getStatus() != ReleaseStatus.CANCELLED) {
                Instant plannedEnd = calculateBusinessDaysEndDate(release.getCreatedAt(), 90);
                if (now.isAfter(plannedEnd)) overdue++;
            }
        }
        return new AtRiskReleases(overdue, criticallyDelayed, overdue + criticallyDelayed);
    }

    private Instant calculateBusinessDaysEndDate(Instant startDate, int businessDays) {
        LocalDate current = startDate.atZone(ZoneOffset.UTC).toLocalDate();
        int daysAdded = 0;
        while (daysAdded < businessDays) {
            current = current.plusDays(1);
            int dayOfWeek = current.getDayOfWeek().getValue();
            if (dayOfWeek >= 1 && dayOfWeek <= 5) {
                daysAdded++;
            }
        }
        return current.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private PlanningAccuracy calculatePlanningAccuracy(List<Release> releases) {
        List<Release> released = releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.RELEASED && r.getReleasedAt() != null)
                .toList();
        if (released.isEmpty()) return new PlanningAccuracy(0.0, 0.0, 0.0);

        int onTime = 0;
        long totalDelay = 0;
        for (Release r : released) {
            long duration =
                    Duration.between(r.getCreatedAt(), r.getReleasedAt()).toDays();
            if (duration <= properties.onTimeDeliveryDays()) onTime++;
            else totalDelay += (duration - properties.onTimeDeliveryDays());
        }

        double onTimeDelivery = (onTime * 100.0) / released.size();
        double avgDelay = released.size() > onTime ? (double) totalDelay / (released.size() - onTime) : 0.0;
        double accuracy = 100.0 - ((double) totalDelay / (released.size() * properties.onTimeDeliveryDays()) * 100.0);

        return new PlanningAccuracy(
                Math.round(onTimeDelivery * 10.0) / 10.0,
                Math.round(avgDelay * 10.0) / 10.0,
                Math.round(Math.max(0, accuracy) * 10.0) / 10.0);
    }

    private ReleasesCompleted calculateReleasesCompleted(List<Release> releases) {
        Map<String, Long> byMonth = releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.RELEASED && r.getReleasedAt() != null)
                .filter(r -> isWithinLast12Months(r.getReleasedAt()))
                .collect(Collectors.groupingBy(
                        r -> YearMonth.from(r.getReleasedAt().atZone(ZoneOffset.UTC))
                                .format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.counting()));

        List<TrendData> trend = byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TrendData(e.getKey(), e.getValue().doubleValue()))
                .toList();

        return new ReleasesCompleted(trend, (int) releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.RELEASED)
                .count());
    }

    private AverageReleaseDuration calculateAverageReleaseDuration(List<Release> releases) {
        Map<String, List<Long>> byMonth = releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.RELEASED && r.getReleasedAt() != null)
                .filter(r -> isWithinLast12Months(r.getReleasedAt()))
                .collect(Collectors.groupingBy(
                        r -> YearMonth.from(r.getReleasedAt().atZone(ZoneOffset.UTC))
                                .format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.mapping(
                                r -> Duration.between(r.getCreatedAt(), r.getReleasedAt())
                                        .toDays(),
                                Collectors.toList())));

        List<TrendData> trend = byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    double avg = e.getValue().stream()
                            .mapToLong(Long::longValue)
                            .average()
                            .orElse(0.0);
                    return new TrendData(e.getKey(), Math.round(avg * 10.0) / 10.0);
                })
                .toList();

        double current = trend.isEmpty() ? 0.0 : trend.getLast().value();
        return new AverageReleaseDuration(trend, current);
    }

    private PlanningAccuracyTrend calculatePlanningAccuracyTrend(List<Release> releases) {
        Map<String, List<Release>> byMonth = releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.RELEASED && r.getReleasedAt() != null)
                .filter(r -> isWithinLast12Months(r.getReleasedAt()))
                .collect(Collectors.groupingBy(
                        r -> YearMonth.from(r.getReleasedAt().atZone(ZoneOffset.UTC))
                                .format(DateTimeFormatter.ofPattern("yyyy-MM"))));

        List<TrendData> trend = byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    long onTime = e.getValue().stream()
                            .filter(r -> Duration.between(r.getCreatedAt(), r.getReleasedAt())
                                            .toDays()
                                    <= properties.onTimeDeliveryDays())
                            .count();
                    double pct = (onTime * 100.0) / e.getValue().size();
                    return new TrendData(e.getKey(), Math.round(pct * 10.0) / 10.0);
                })
                .toList();

        return new PlanningAccuracyTrend(trend);
    }

    private OverallCapacity calculateOverallCapacity(List<Feature> features) {
        Set<String> owners = features.stream()
                .map(Feature::getFeatureOwner)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        int totalResources = owners.size();
        if (totalResources == 0) return new OverallCapacity(0, 0.0, 100.0, 0);

        long activeFeatures = features.stream()
                .filter(f -> f.getPlanningStatus() != FeaturePlanningStatus.DONE)
                .count();
        double utilization = (activeFeatures * 100.0) / (totalResources * properties.defaultCapacity());

        int overallocated = 0;
        Map<String, Long> featuresPerOwner = features.stream()
                .filter(f ->
                        StringUtils.hasText(f.getFeatureOwner()) && f.getPlanningStatus() != FeaturePlanningStatus.DONE)
                .collect(Collectors.groupingBy(Feature::getFeatureOwner, Collectors.counting()));

        for (long count : featuresPerOwner.values()) {
            if (count > properties.defaultCapacity()) overallocated++;
        }

        double roundedUtilization = Math.round(utilization * 10.0) / 10.0;
        return new OverallCapacity(
                totalResources,
                roundedUtilization,
                Math.round(Math.max(0, 100.0 - roundedUtilization) * 10.0) / 10.0,
                overallocated);
    }

    private List<OwnerWorkload> calculateWorkloadByOwner(List<Feature> features) {
        Map<String, List<Feature>> byOwner = features.stream()
                .filter(f -> StringUtils.hasText(f.getFeatureOwner()))
                .collect(Collectors.groupingBy(Feature::getFeatureOwner));

        return byOwner.entrySet().stream()
                .map(entry -> {
                    int current = (int) entry.getValue().stream()
                            .filter(f -> f.getPlanningStatus() != FeaturePlanningStatus.DONE)
                            .count();
                    double utilization = (current * 100.0) / properties.defaultCapacity();
                    int future = (int) entry.getValue().stream()
                            .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.NOT_STARTED)
                            .count();
                    String risk =
                            utilization >= properties.overallocationThresholds().highThreshold()
                                    ? "HIGH"
                                    : (utilization
                                                    >= properties
                                                            .overallocationThresholds()
                                                            .mediumThreshold()
                                            ? "MEDIUM"
                                            : "NONE");
                    return new OwnerWorkload(
                            entry.getKey(),
                            current,
                            properties.defaultCapacity(),
                            Math.round(utilization * 10.0) / 10.0,
                            future,
                            risk);
                })
                .toList();
    }

    private Commitments calculateCommitments(List<Release> releases, List<Feature> features) {
        int active = (int) releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.IN_PROGRESS)
                .count();
        int planned = (int) releases.stream()
                .filter(r -> r.getStatus() == ReleaseStatus.DRAFT)
                .count();
        int totalFeatures = features.size();
        double effort = features.stream()
                        .filter(f -> f.getPlanningStatus() != FeaturePlanningStatus.DONE)
                        .count()
                * properties.estimationAccuracyDefaultEffort();
        return new Commitments(active, planned, totalFeatures, Math.round(effort * 10.0) / 10.0);
    }

    private List<OverallocationWarning> calculateOverallocationWarnings(List<OwnerWorkload> workloads) {
        return workloads.stream()
                .filter(w -> !w.overallocationRisk().equals("NONE"))
                .map(w -> new OverallocationWarning(w.owner(), w.overallocationRisk(), w.utilizationRate()))
                .toList();
    }

    private boolean isWithinLast12Months(Instant date) {
        if (date == null) return false;
        YearMonth releaseMonth = YearMonth.from(date.atZone(ZoneOffset.UTC));
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        YearMonth earliestAllowed = currentMonth.minusMonths(11);
        return !releaseMonth.isBefore(earliestAllowed);
    }
}

package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.config.DashboardProperties;
import com.sivalabs.ft.features.domain.dtos.ReleaseDashboardDto;
import com.sivalabs.ft.features.domain.dtos.ReleaseDashboardDto.*;
import com.sivalabs.ft.features.domain.dtos.ReleaseMetricsDto;
import com.sivalabs.ft.features.domain.dtos.ReleaseMetricsDto.*;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.ReleaseDashboardMapper;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import com.sivalabs.ft.features.domain.models.RiskLevel;
import com.sivalabs.ft.features.domain.models.TimelineAdherence;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final ReleaseRepository releaseRepository;
    private final FeatureRepository featureRepository;
    private final ReleaseDashboardMapper releaseDashboardMapper;
    private final DashboardProperties properties;

    public DashboardService(
            ReleaseRepository releaseRepository,
            FeatureRepository featureRepository,
            ReleaseDashboardMapper releaseDashboardMapper,
            DashboardProperties properties) {
        this.releaseRepository = releaseRepository;
        this.featureRepository = featureRepository;
        this.releaseDashboardMapper = releaseDashboardMapper;
        this.properties = properties;
    }

    public ReleaseDashboardDto getReleaseDashboard(String releaseCode) {
        validateReleaseCode(releaseCode);
        log.info("Fetching dashboard for release: {}", releaseCode);

        Release release = releaseRepository
                .findByCode(releaseCode)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found: " + releaseCode));

        List<Feature> features = featureRepository.findByReleaseCode(releaseCode);

        ReleaseDashboardDto baseDto = releaseDashboardMapper.toDto(release);
        DashboardOverview overview = calculateOverview(features, release);
        HealthIndicators healthIndicators = calculateHealthIndicators(overview, release);
        Timeline timeline = calculateTimeline(release, features, overview);
        FeatureBreakdown featureBreakdown = calculateFeatureBreakdown(features);

        return new ReleaseDashboardDto(
                baseDto.releaseCode(),
                baseDto.releaseName(),
                release.getStatus(),
                overview,
                healthIndicators,
                timeline,
                featureBreakdown);
    }

    public ReleaseMetricsDto getReleaseMetrics(String releaseCode) {
        validateReleaseCode(releaseCode);
        log.info("Fetching metrics for release: {}", releaseCode);

        Release release = releaseRepository
                .findByCode(releaseCode)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found: " + releaseCode));

        List<Feature> features = featureRepository.findByReleaseCode(releaseCode);

        double completionRate = calculateCompletionRate(features);
        Velocity velocity = calculateVelocity(features, release);
        BlockedTime blockedTime = calculateBlockedTime(features);
        WorkloadDistribution workloadDistribution = calculateWorkloadDistribution(features);

        return new ReleaseMetricsDto(
                release.getCode(), release.getStatus(), completionRate, velocity, blockedTime, workloadDistribution);
    }

    private DashboardOverview calculateOverview(List<Feature> features, Release release) {
        if (features.isEmpty()) {
            return new DashboardOverview(0, 0, 0, 0, 0, 0.0, 0);
        }

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

        double completionPercentage = calculatePercentage(completedFeatures, totalFeatures);

        int estimatedDaysRemaining = 0;
        if (release.getStatus() != ReleaseStatus.RELEASED && release.getReleasedAt() == null) {
            Velocity velocity = calculateVelocity(features, release);
            int remaining = totalFeatures - completedFeatures;
            if (remaining > 0) {
                if (velocity.featuresPerWeek() > 0) {
                    estimatedDaysRemaining = (int) Math.ceil((remaining / velocity.featuresPerWeek())
                            * properties.velocity().daysPerWeek());
                } else {
                    estimatedDaysRemaining = properties.defaultTimelineEstimateDays();
                }
            }
        }

        return new DashboardOverview(
                totalFeatures,
                completedFeatures,
                inProgressFeatures,
                blockedFeatures,
                pendingFeatures,
                completionPercentage,
                estimatedDaysRemaining);
    }

    private double calculatePercentage(int numerator, int denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        double percentage = (numerator * 100.0) / denominator;
        return Math.round(percentage * 10.0) / 10.0;
    }

    private HealthIndicators calculateHealthIndicators(DashboardOverview overview, Release release) {
        TimelineAdherence timelineAdherence = calculateTimelineAdherence(overview, release);
        RiskLevel riskLevel = calculateRiskLevel(overview);

        return new HealthIndicators(timelineAdherence, riskLevel, overview.blockedFeatures());
    }

    private TimelineAdherence calculateTimelineAdherence(DashboardOverview overview, Release release) {
        if (release.getStatus() == ReleaseStatus.RELEASED) {
            return TimelineAdherence.ON_TRACK;
        }

        Instant now = Instant.now();
        Instant plannedEnd = getPlannedEndDate(release);

        if (now.isAfter(plannedEnd)) {
            return TimelineAdherence.DELAYED;
        }

        long daysUntilDeadline = Duration.between(now, plannedEnd).toDays();
        if (daysUntilDeadline <= 14 && overview.completionPercentage() < 70.0) {
            return TimelineAdherence.AT_RISK;
        }
        return TimelineAdherence.ON_TRACK;
    }

    private void validateReleaseCode(String releaseCode) {
        if (!StringUtils.hasText(releaseCode)) {
            throw new BadRequestException("Release code cannot be empty");
        }
        if (releaseCode.length() > 50) {
            throw new BadRequestException("Release code cannot exceed 50 characters");
        }
        if (!releaseCode.matches("[A-Za-z0-9-_.]+")) {
            throw new BadRequestException("Release code contains invalid characters");
        }
    }

    private RiskLevel calculateRiskLevel(DashboardOverview overview) {
        int totalFeatures = overview.totalFeatures();
        if (totalFeatures == 0) return RiskLevel.LOW;

        double blockedPercentage = (overview.blockedFeatures() * 100.0) / totalFeatures;
        double completionPercentage = overview.completionPercentage();

        if (blockedPercentage > 50.0 || completionPercentage < 30.0) {
            return RiskLevel.HIGH;
        }
        if (blockedPercentage > 25.0 || completionPercentage < 60.0) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private Instant getPlannedEndDate(Release release) {
        return calculateBusinessDaysEndDate(release.getCreatedAt(), 90);
    }

    private Instant calculateBusinessDaysEndDate(Instant startDate, int businessDays) {
        LocalDate current = startDate.atZone(ZoneId.systemDefault()).toLocalDate();
        int daysAdded = 0;
        while (daysAdded < businessDays) {
            current = current.plusDays(1);
            int dayOfWeek = current.getDayOfWeek().getValue();
            if (dayOfWeek >= 1 && dayOfWeek <= 5) {
                daysAdded++;
            }
        }
        return current.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private Timeline calculateTimeline(Release release, List<Feature> features, DashboardOverview overview) {
        Instant startDate = release.getCreatedAt();
        Instant plannedEndDate = getPlannedEndDate(release);
        Instant actualEndDate = release.getReleasedAt();
        Instant estimatedEndDate;

        if (release.getStatus() == ReleaseStatus.RELEASED) {
            estimatedEndDate = actualEndDate;
        } else {
            int remaining = overview.totalFeatures() - overview.completedFeatures();
            if (remaining == 0) {
                estimatedEndDate = Instant.now();
            } else {
                Velocity velocity = calculateVelocity(features, release);
                if (velocity.featuresPerWeek() > 0) {
                    long daysToComplete = (long) Math.ceil((remaining / velocity.featuresPerWeek())
                            * properties.velocity().daysPerWeek());
                    estimatedEndDate = Instant.now().plus(Duration.ofDays(daysToComplete));
                } else {
                    estimatedEndDate = plannedEndDate;
                }
            }
        }

        return new Timeline(startDate, plannedEndDate, estimatedEndDate, actualEndDate);
    }

    private FeatureBreakdown calculateFeatureBreakdown(List<Feature> features) {
        Map<String, Integer> byStatus = features.stream()
                .collect(Collectors.groupingBy(f -> f.getPlanningStatus().name(), Collectors.summingInt(f -> 1)));

        Map<String, Integer> byOwner = new HashMap<>();
        int unassigned = 0;
        for (Feature f : features) {
            if (StringUtils.hasText(f.getFeatureOwner())) {
                byOwner.merge(f.getFeatureOwner(), 1, Integer::sum);
            } else {
                unassigned++;
            }
        }
        if (unassigned > 0) byOwner.put("unassigned", unassigned);

        Map<String, Integer> byPriority = features.stream()
                .collect(Collectors.groupingBy(f -> f.getPriority().name(), Collectors.summingInt(f -> 1)));

        return new FeatureBreakdown(byStatus, byOwner, byPriority);
    }

    private double calculateCompletionRate(List<Feature> features) {
        if (features.isEmpty()) return 0.0;
        long completed = features.stream()
                .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.DONE)
                .count();
        return calculatePercentage((int) completed, features.size());
    }

    private Velocity calculateVelocity(List<Feature> features, Release release) {
        long completed = features.stream()
                .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.DONE)
                .count();
        if (completed == 0) return new Velocity(0.0, 0.0);

        Instant endDate = (release.getStatus() == ReleaseStatus.RELEASED && release.getReleasedAt() != null)
                ? release.getReleasedAt()
                : Instant.now();

        long businessDaysElapsed = calculateBusinessDaysBetween(release.getCreatedAt(), endDate);
        double weeksElapsed =
                (double) businessDaysElapsed / properties.velocity().daysPerWeek();

        double featuresPerWeek = 0.0;
        if (weeksElapsed >= properties.velocity().minWeeksForCalculation()) {
            featuresPerWeek = completed / weeksElapsed;
        }

        double avgCycleTime = features.stream()
                .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.DONE && f.getUpdatedAt() != null)
                .mapToLong(f ->
                        Duration.between(f.getCreatedAt(), f.getUpdatedAt()).toDays())
                .average()
                .orElse(0.0);

        return new Velocity(Math.round(featuresPerWeek * 10.0) / 10.0, Math.round(avgCycleTime * 10.0) / 10.0);
    }

    private long calculateBusinessDaysBetween(Instant start, Instant end) {
        LocalDate startDate = start.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = end.atZone(ZoneId.systemDefault()).toLocalDate();
        long businessDays = 0;
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            int dayOfWeek = current.getDayOfWeek().getValue();
            if (dayOfWeek >= 1 && dayOfWeek <= 5) businessDays++;
            current = current.plusDays(1);
        }
        return businessDays;
    }

    private BlockedTime calculateBlockedTime(List<Feature> features) {
        List<Feature> blocked = features.stream()
                .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.BLOCKED)
                .toList();
        if (blocked.isEmpty()) return new BlockedTime(0, 0.0, 0.0, Map.of());

        long totalBlockedDays = 0;
        Map<String, Integer> reasons = new HashMap<>();
        for (Feature f : blocked) {
            long days = Duration.between(f.getUpdatedAt() != null ? f.getUpdatedAt() : f.getCreatedAt(), Instant.now())
                    .toDays();
            totalBlockedDays += days;
            if (StringUtils.hasText(f.getBlockageReason())) {
                reasons.merge(f.getBlockageReason(), 1, Integer::sum);
            }
        }

        double avgDuration = (double) totalBlockedDays / blocked.size();

        long totalFeatureDays = features.stream()
                .mapToLong(f -> {
                    Instant end = (f.getPlanningStatus() == FeaturePlanningStatus.DONE && f.getUpdatedAt() != null)
                            ? f.getUpdatedAt()
                            : Instant.now();
                    return Duration.between(f.getCreatedAt(), end).toDays();
                })
                .filter(d -> d > 0)
                .sum();

        double percentage = totalFeatureDays > 0 ? (totalBlockedDays * 100.0) / totalFeatureDays : 0.0;

        return new BlockedTime(
                totalBlockedDays, Math.round(avgDuration * 10.0) / 10.0, Math.round(percentage * 10.0) / 10.0, reasons);
    }

    private WorkloadDistribution calculateWorkloadDistribution(List<Feature> features) {
        Map<String, List<Feature>> byOwner = features.stream()
                .filter(f -> StringUtils.hasText(f.getFeatureOwner()))
                .collect(Collectors.groupingBy(Feature::getFeatureOwner));

        List<OwnerWorkload> workloads = new ArrayList<>();
        for (Map.Entry<String, List<Feature>> entry : byOwner.entrySet()) {
            List<Feature> ownerFeatures = entry.getValue();
            int assigned = ownerFeatures.size();
            int completed = (int) ownerFeatures.stream()
                    .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.DONE)
                    .count();
            int inProgress = (int) ownerFeatures.stream()
                    .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.IN_PROGRESS)
                    .count();
            int blocked = (int) ownerFeatures.stream()
                    .filter(f -> f.getPlanningStatus() == FeaturePlanningStatus.BLOCKED)
                    .count();
            double utilization = calculatePercentage(completed, assigned);
            workloads.add(new OwnerWorkload(entry.getKey(), assigned, completed, inProgress, blocked, utilization));
        }

        return new WorkloadDistribution(workloads);
    }
}

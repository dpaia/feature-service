package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.AdvancedFilterDto;
import com.sivalabs.ft.features.domain.dtos.SegmentAnalyticsDto;
import com.sivalabs.ft.features.domain.dtos.TopItemDto;
import com.sivalabs.ft.features.domain.dtos.UserSegmentDto;
import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import com.sivalabs.ft.features.domain.models.ActionType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for analyzing user segments and their usage patterns.
 * Provides insights into how different user groups interact with features.
 */
@Service
@Transactional(readOnly = true)
public class SegmentAnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(SegmentAnalyticsService.class);

    private final AdvancedFilterService advancedFilterService;

    // Predefined segments for quick analysis
    private static final Map<String, UserSegmentDto> PREDEFINED_SEGMENTS = Map.of(
            "mobile",
            new UserSegmentDto(
                    "mobile", "Mobile Users", "Users accessing via mobile devices", Map.of("device", "mobile")),
            "desktop",
            new UserSegmentDto(
                    "desktop", "Desktop Users", "Users accessing via desktop browsers", Map.of("device", "desktop")),
            "power-users",
            new UserSegmentDto(
                    "power-users", "Power Users", "Users with high activity levels", Map.of("userType", "power")),
            "new-users",
            new UserSegmentDto("new-users", "New Users", "Recently registered users", Map.of("userType", "new")));

    public SegmentAnalyticsService(AdvancedFilterService advancedFilterService) {
        this.advancedFilterService = advancedFilterService;
    }

    /**
     * Analyze multiple segments and return analytics for each.
     *
     * @param segmentNames List of predefined segment names to analyze
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @return List of segment analytics
     */
    public List<SegmentAnalyticsDto> analyzeSegments(List<String> segmentNames, Instant startDate, Instant endDate) {
        log.info("Analyzing {} segments", segmentNames != null ? segmentNames.size() : 0);

        if (segmentNames == null || segmentNames.isEmpty()) {
            // Return analytics for all predefined segments
            segmentNames = new ArrayList<>(PREDEFINED_SEGMENTS.keySet());
        }

        List<SegmentAnalyticsDto> results = new ArrayList<>();

        for (String segmentName : segmentNames) {
            UserSegmentDto segment = PREDEFINED_SEGMENTS.get(segmentName);
            if (segment == null) {
                log.warn("Unknown segment: {}", segmentName);
                continue;
            }

            SegmentAnalyticsDto analytics = analyzeSegment(segment, startDate, endDate);
            results.add(analytics);
        }

        log.info("Segment analysis complete. {} segments analyzed.", results.size());

        return results;
    }

    /**
     * Analyze a specific segment defined by custom criteria.
     *
     * @param segmentName Segment name
     * @param criteria Custom criteria map (e.g., {"device": "mobile", "region": "US"})
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @return Segment analytics
     */
    public SegmentAnalyticsDto analyzeCustomSegment(
            String segmentName, Map<String, String> criteria, Instant startDate, Instant endDate) {
        log.info("Analyzing custom segment '{}' with {} criteria", segmentName, criteria.size());

        UserSegmentDto customSegment =
                new UserSegmentDto(segmentName.toLowerCase(), segmentName, "Custom segment", criteria);

        return analyzeSegment(customSegment, startDate, endDate);
    }

    /**
     * Analyze a single segment.
     *
     * @param segment Segment definition
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @return Segment analytics
     */
    private SegmentAnalyticsDto analyzeSegment(UserSegmentDto segment, Instant startDate, Instant endDate) {
        log.debug("Analyzing segment: {}", segment.segmentName());

        // Build filter from segment criteria
        AdvancedFilterDto filter = new AdvancedFilterDto(
                null, // featureCodes
                null, // productCodes
                null, // releaseCodes
                null, // actionTypes
                startDate,
                endDate,
                segment.criteria(),
                segment.segmentId(),
                null // groupBy
                );

        // Get filtered events
        List<FeatureUsage> events = advancedFilterService.applyAdvancedFilters(filter);

        if (events.isEmpty()) {
            log.info("Segment '{}' has no matching events", segment.segmentName());
            return new SegmentAnalyticsDto(segment.segmentName(), segment.criteria(), 0L, 0L, List.of(), Map.of());
        }

        // Calculate metrics
        long totalUsage = events.size();
        long uniqueUsers =
                events.stream().map(FeatureUsage::getUserId).distinct().count();

        // Calculate top features
        Map<String, Long> featureUsageMap = events.stream()
                .filter(e -> e.getFeatureCode() != null)
                .collect(Collectors.groupingBy(FeatureUsage::getFeatureCode, Collectors.counting()));

        List<TopItemDto> topFeatures = featureUsageMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new TopItemDto(e.getKey(), e.getValue()))
                .toList();

        // Calculate usage by action type
        Map<ActionType, Long> usageByActionType =
                events.stream().collect(Collectors.groupingBy(FeatureUsage::getActionType, Collectors.counting()));

        log.info(
                "Segment '{}' analytics: {} usage, {} unique users, {} top features",
                segment.segmentName(),
                totalUsage,
                uniqueUsers,
                topFeatures.size());

        return new SegmentAnalyticsDto(
                segment.segmentName(), segment.criteria(), totalUsage, uniqueUsers, topFeatures, usageByActionType);
    }

    /**
     * Get list of predefined segments available for analysis.
     *
     * @return List of predefined segment definitions
     */
    public List<UserSegmentDto> getPredefinedSegments() {
        return new ArrayList<>(PREDEFINED_SEGMENTS.values());
    }

    /**
     * Check if a segment name is predefined.
     *
     * @param segmentName Segment name to check
     * @return true if predefined, false otherwise
     */
    public boolean isPredefinedSegment(String segmentName) {
        return PREDEFINED_SEGMENTS.containsKey(segmentName);
    }
}

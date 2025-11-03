package com.sivalabs.ft.features.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.domain.dtos.FeatureUsageDto;
import com.sivalabs.ft.features.domain.dtos.UsageStatsDto;
import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import com.sivalabs.ft.features.domain.mappers.FeatureUsageMapper;
import com.sivalabs.ft.features.domain.models.ActionType;
import com.sivalabs.ft.features.domain.utils.AnonymizationUtils;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureUsageService {
    private static final Logger log = LoggerFactory.getLogger(FeatureUsageService.class);

    private final FeatureUsageRepository featureUsageRepository;
    private final FeatureUsageMapper featureUsageMapper;
    private final ObjectMapper objectMapper;
    private final com.sivalabs.ft.features.ApplicationProperties applicationProperties;

    public FeatureUsageService(
            FeatureUsageRepository featureUsageRepository,
            FeatureUsageMapper featureUsageMapper,
            ObjectMapper objectMapper,
            com.sivalabs.ft.features.ApplicationProperties applicationProperties) {
        this.featureUsageRepository = featureUsageRepository;
        this.featureUsageMapper = featureUsageMapper;
        this.objectMapper = objectMapper;
        this.applicationProperties = applicationProperties;
    }

    @Transactional
    public FeatureUsageDto logUsage(
            String userId,
            String featureCode,
            String productCode,
            String releaseCode,
            ActionType actionType,
            Map<String, Object> contextData,
            String ipAddress,
            String userAgent) {
        if (!applicationProperties.usageTracking().enabled()) {
            return null;
        }
        try {
            boolean gdprEnabled = applicationProperties.gdpr() != null
                    && applicationProperties.gdpr().enabled();

            Map<String, Object> enrichedContext =
                    contextData != null ? new java.util.LinkedHashMap<>(contextData) : new java.util.LinkedHashMap<>();

            if (ipAddress != null) {
                String processedIp = ipAddress;
                if (gdprEnabled && applicationProperties.gdpr().anonymizeIpAddresses()) {
                    processedIp = AnonymizationUtils.anonymizeIpAddress(ipAddress);
                }
                enrichedContext.put("ipAddress", processedIp);
            }
            if (userAgent != null) {
                String processedUserAgent = userAgent;
                if (gdprEnabled) {
                    processedUserAgent = AnonymizationUtils.anonymizeUserAgent(userAgent);
                }
                enrichedContext.put("userAgent", processedUserAgent);
            }

            String contextJson = null;
            if (!enrichedContext.isEmpty()) {
                try {
                    contextJson = objectMapper.writeValueAsString(enrichedContext);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize context data", e);
                }
            }

            String processedUserId = userId;
            if (gdprEnabled && applicationProperties.gdpr().anonymizeUserIds() && !"anonymous".equals(userId)) {
                processedUserId = AnonymizationUtils.anonymizeUserId(userId);
            }

            var featureUsage = new FeatureUsage();
            featureUsage.setUserId(processedUserId);
            featureUsage.setFeatureCode(featureCode);
            featureUsage.setProductCode(productCode);
            featureUsage.setReleaseCode(releaseCode);
            featureUsage.setActionType(actionType);
            featureUsage.setTimestamp(Instant.now());
            featureUsage.setContext(contextJson);

            FeatureUsage saved = featureUsageRepository.save(featureUsage);
            log.debug(
                    "Logged usage: user={}, feature={}, product={}, release={}, action={}",
                    userId,
                    featureCode,
                    productCode,
                    releaseCode,
                    actionType);
            return featureUsageMapper.toDto(saved);
        } catch (Exception e) {
            log.error("Failed to log usage event", e);
            // Don't throw exception to avoid breaking the main flow
            return null;
        }
    }

    @Transactional
    public void logUsage(String userId, String featureCode, String productCode, ActionType actionType) {
        logUsage(userId, featureCode, productCode, null, actionType, null, null, null);
    }

    @Transactional
    public void logUsage(
            String userId, String featureCode, String productCode, ActionType actionType, Map<String, Object> context) {
        logUsage(userId, featureCode, productCode, null, actionType, context, null, null);
    }

    @Transactional
    public void logUsage(
            String userId, String featureCode, String productCode, String releaseCode, ActionType actionType) {
        logUsage(userId, featureCode, productCode, releaseCode, actionType, null, null, null);
    }

    @Transactional(readOnly = true)
    public Page<FeatureUsageDto> findUsageEvents(
            String userId,
            String featureCode,
            String productCode,
            ActionType actionType,
            Instant startDate,
            Instant endDate,
            Pageable pageable) {
        Page<FeatureUsage> usagePage = featureUsageRepository.findByFilters(
                userId, featureCode, productCode, actionType, startDate, endDate, pageable);
        return usagePage.map(featureUsageMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<FeatureUsageDto> findAllUsageEvents(
            String userId,
            String featureCode,
            String productCode,
            ActionType actionType,
            Instant startDate,
            Instant endDate) {
        Page<FeatureUsage> usagePage = featureUsageRepository.findByFilters(
                userId, featureCode, productCode, actionType, startDate, endDate, Pageable.unpaged());
        return usagePage.getContent().stream().map(featureUsageMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<FeatureUsageDto> findByUserId(String userId, Pageable pageable) {
        return featureUsageRepository.findByUserId(userId, pageable).map(featureUsageMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<FeatureUsageDto> findByFeatureCode(String featureCode, Pageable pageable) {
        return featureUsageRepository.findByFeatureCode(featureCode, pageable).map(featureUsageMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<FeatureUsageDto> findByProductCode(String productCode, Pageable pageable) {
        return featureUsageRepository.findByProductCode(productCode, pageable).map(featureUsageMapper::toDto);
    }

    @Transactional(readOnly = true)
    public UsageStatsDto getUsageStats(ActionType actionType, Instant startDate, Instant endDate) {
        long totalUsageCount;
        if (actionType != null) {
            Page<FeatureUsage> filteredEvents = featureUsageRepository.findByFilters(
                    null, null, null, actionType, startDate, endDate, Pageable.unpaged());
            totalUsageCount = filteredEvents.getTotalElements();
        } else {
            totalUsageCount = startDate != null && endDate != null
                    ? featureUsageRepository.countByTimestampBetween(startDate, endDate)
                    : featureUsageRepository.count();
        }

        List<Object[]> actionTypeStats = featureUsageRepository.findActionTypeStats(startDate, endDate);
        Map<ActionType, Long> usageByActionType = actionTypeStats.stream()
                .collect(Collectors.toMap(
                        row -> (ActionType) row[0],
                        row -> ((Number) row[1]).longValue(),
                        (a, b) -> a,
                        LinkedHashMap::new));

        List<Object[]> topFeaturesData = featureUsageRepository.findTopFeatures(startDate, endDate);
        Map<String, Long> topFeatures = topFeaturesData.stream()
                .limit(10)
                .collect(Collectors.toMap(
                        row -> (String) row[0], row -> ((Number) row[1]).longValue(), (a, b) -> a, LinkedHashMap::new));

        List<Object[]> topProductsData = featureUsageRepository.findTopProducts(startDate, endDate);
        Map<String, Long> topProducts = topProductsData.stream()
                .limit(10)
                .collect(Collectors.toMap(
                        row -> (String) row[0], row -> ((Number) row[1]).longValue(), (a, b) -> a, LinkedHashMap::new));

        List<Object[]> topUsersData = featureUsageRepository.findTopUsers(startDate, endDate);
        Map<String, Long> topUsers = topUsersData.stream()
                .limit(10)
                .collect(Collectors.toMap(
                        row -> (String) row[0], row -> ((Number) row[1]).longValue(), (a, b) -> a, LinkedHashMap::new));

        long uniqueUserCount = featureUsageRepository.countDistinctUsers(startDate, endDate);
        long uniqueFeatureCount = featureUsageRepository.countDistinctFeatures(startDate, endDate);
        long uniqueProductCount = featureUsageRepository.countDistinctProducts(startDate, endDate);

        return new UsageStatsDto(
                totalUsageCount,
                uniqueUserCount,
                uniqueFeatureCount,
                uniqueProductCount,
                usageByActionType,
                topFeatures,
                topProducts,
                topUsers);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getTopFeatures(Instant startDate, Instant endDate, int limit) {
        List<Object[]> topFeaturesData = featureUsageRepository.findTopFeatures(startDate, endDate);
        return topFeaturesData.stream()
                .limit(limit)
                .collect(Collectors.toMap(
                        row -> (String) row[0], row -> ((Number) row[1]).longValue(), (a, b) -> a, LinkedHashMap::new));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getTopUsers(Instant startDate, Instant endDate, int limit) {
        List<Object[]> topUsersData = featureUsageRepository.findTopUsers(startDate, endDate);
        return topUsersData.stream()
                .limit(limit)
                .collect(Collectors.toMap(
                        row -> (String) row[0], row -> ((Number) row[1]).longValue(), (a, b) -> a, LinkedHashMap::new));
    }

    @Transactional(readOnly = true)
    public java.util.List<FeatureUsageDto> getFeatureUsageEvents(
            String featureCode, ActionType actionType, Instant startDate, Instant endDate) {
        Page<FeatureUsage> events = featureUsageRepository.findByFilters(
                null, featureCode, null, actionType, startDate, endDate, Pageable.unpaged());
        return events.getContent().stream().map(featureUsageMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<FeatureUsageDto> getProductUsageEvents(
            String productCode, ActionType actionType, Instant startDate, Instant endDate) {
        Page<FeatureUsage> events = featureUsageRepository.findByFilters(
                null, null, productCode, actionType, startDate, endDate, Pageable.unpaged());
        return events.getContent().stream().map(featureUsageMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public com.sivalabs.ft.features.domain.dtos.FeatureStatsDto getFeatureStats(
            String featureCode, ActionType actionType, Instant startDate, Instant endDate) {

        long totalCount;
        if (actionType != null) {

            Page<FeatureUsage> filteredEvents = featureUsageRepository.findByFilters(
                    null, featureCode, null, actionType, startDate, endDate, Pageable.unpaged());
            totalCount = filteredEvents.getTotalElements();
        } else {
            totalCount = startDate != null && endDate != null
                    ? featureUsageRepository.countByFeatureCodeAndTimestampBetween(featureCode, startDate, endDate)
                    : featureUsageRepository.countByFeatureCode(featureCode);
        }

        long uniqueUsers = featureUsageRepository.countDistinctUsersByFeatureCode(featureCode, startDate, endDate);

        List<Object[]> actionTypeData =
                featureUsageRepository.findActionTypeStatsByFeatureCode(featureCode, startDate, endDate);
        Map<ActionType, Long> usageByActionType = actionTypeData.stream()
                .collect(Collectors.toMap(
                        row -> (ActionType) row[0],
                        row -> ((Number) row[1]).longValue(),
                        (a, b) -> a,
                        LinkedHashMap::new));

        List<Object[]> topUsersData = featureUsageRepository.findTopUsersByFeatureCode(featureCode, startDate, endDate);
        Map<String, Long> topUsers = topUsersData.stream()
                .limit(10)
                .collect(Collectors.toMap(
                        row -> (String) row[0], row -> ((Number) row[1]).longValue(), (a, b) -> a, LinkedHashMap::new));

        List<Object[]> productData =
                featureUsageRepository.findUsageByProductForFeature(featureCode, startDate, endDate);
        Map<String, Long> usageByProduct = productData.stream()
                .limit(10)
                .collect(Collectors.toMap(
                        row -> (String) row[0], row -> ((Number) row[1]).longValue(), (a, b) -> a, LinkedHashMap::new));

        return new com.sivalabs.ft.features.domain.dtos.FeatureStatsDto(
                featureCode, totalCount, uniqueUsers, usageByActionType, topUsers, usageByProduct);
    }

    @Transactional(readOnly = true)
    public com.sivalabs.ft.features.domain.dtos.ProductStatsDto getProductStats(
            String productCode, ActionType actionType, Instant startDate, Instant endDate) {
        long totalCount;
        if (actionType != null) {
            Page<FeatureUsage> filteredEvents = featureUsageRepository.findByFilters(
                    null, null, productCode, actionType, startDate, endDate, Pageable.unpaged());
            totalCount = filteredEvents.getTotalElements();
        } else {
            totalCount = startDate != null && endDate != null
                    ? featureUsageRepository.countByProductCodeAndTimestampBetween(productCode, startDate, endDate)
                    : featureUsageRepository.countByProductCode(productCode);
        }

        long uniqueUsers = featureUsageRepository.countDistinctUsersByProductCode(productCode, startDate, endDate);

        long uniqueFeatures =
                featureUsageRepository.countDistinctFeaturesByProductCode(productCode, startDate, endDate);

        List<Object[]> actionTypeData =
                featureUsageRepository.findActionTypeStatsByProductCode(productCode, startDate, endDate);
        Map<ActionType, Long> usageByActionType = actionTypeData.stream()
                .collect(Collectors.toMap(
                        row -> (ActionType) row[0],
                        row -> ((Number) row[1]).longValue(),
                        (a, b) -> a,
                        LinkedHashMap::new));

        List<Object[]> topFeaturesData =
                featureUsageRepository.findTopFeaturesByProductCode(productCode, startDate, endDate);
        Map<String, Long> topFeatures = topFeaturesData.stream()
                .limit(10)
                .collect(Collectors.toMap(
                        row -> (String) row[0], row -> ((Number) row[1]).longValue(), (a, b) -> a, LinkedHashMap::new));

        List<Object[]> topUsersData = featureUsageRepository.findTopUsersByProductCode(productCode, startDate, endDate);
        Map<String, Long> topUsers = topUsersData.stream()
                .limit(10)
                .collect(Collectors.toMap(
                        row -> (String) row[0], row -> ((Number) row[1]).longValue(), (a, b) -> a, LinkedHashMap::new));

        return new com.sivalabs.ft.features.domain.dtos.ProductStatsDto(
                productCode, totalCount, uniqueUsers, uniqueFeatures, usageByActionType, topFeatures, topUsers);
    }
}

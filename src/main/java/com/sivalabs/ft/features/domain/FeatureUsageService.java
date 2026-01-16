package com.sivalabs.ft.features.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.domain.dtos.*;
import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import com.sivalabs.ft.features.domain.mappers.FeatureUsageMapper;
import com.sivalabs.ft.features.domain.models.ActionType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureUsageService {
    private static final Logger log = LoggerFactory.getLogger(FeatureUsageService.class);

    private final FeatureUsageRepository featureUsageRepository;
    private final ObjectMapper objectMapper;
    private final com.sivalabs.ft.features.ApplicationProperties applicationProperties;
    private final FeatureUsageMapper featureUsageMapper;

    public FeatureUsageService(
            FeatureUsageRepository featureUsageRepository,
            ObjectMapper objectMapper,
            com.sivalabs.ft.features.ApplicationProperties applicationProperties,
            FeatureUsageMapper featureUsageMapper) {
        this.featureUsageRepository = featureUsageRepository;
        this.objectMapper = objectMapper;
        this.applicationProperties = applicationProperties;
        this.featureUsageMapper = featureUsageMapper;
    }

    @Transactional
    public void logUsage(
            String userId,
            String featureCode,
            String productCode,
            String releaseCode,
            ActionType actionType,
            Map<String, Object> contextData,
            String ipAddress,
            String userAgent) {
        if (!applicationProperties.usageTracking().enabled()) {
            return;
        }
        try {
            // Generate event hash for deduplication (can be used as idempotency key)
            String eventHash = generateEventHash(userId, featureCode, productCode, actionType, Instant.now());

            // Check for duplicates
            if (featureUsageRepository.existsByEventHash(eventHash)) {
                log.debug(
                        "Duplicate usage event detected, skipping: user={}, feature={}, action={}, hash={}",
                        userId,
                        featureCode,
                        actionType,
                        eventHash);
                return; // Skip duplicate
            }

            // Enrich context with anonymized device fingerprint for anonymous users
            Map<String, Object> enrichedContext = contextData != null ? new HashMap<>(contextData) : new HashMap<>();

            if (ipAddress != null && userAgent != null) {
                // Create device fingerprint: hash(device:ip)
                String deviceFingerprint = createDeviceFingerprint(ipAddress, userAgent);
                enrichedContext.put("deviceFingerprint", deviceFingerprint);

                // Extract location from IP (placeholder - would use GeoIP service in production)
                String location = extractLocation(ipAddress);
                if (location != null) {
                    enrichedContext.put("location", location);
                }
            }

            String contextJson = null;
            if (!enrichedContext.isEmpty()) {
                try {
                    contextJson = objectMapper.writeValueAsString(enrichedContext);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize context data", e);
                }
            }

            var featureUsage = new FeatureUsage();
            featureUsage.setUserId(userId);
            featureUsage.setFeatureCode(featureCode);
            featureUsage.setProductCode(productCode);
            featureUsage.setReleaseCode(releaseCode);
            featureUsage.setActionType(actionType);
            featureUsage.setTimestamp(Instant.now());
            featureUsage.setContext(contextJson);
            featureUsage.setEventHash(eventHash);

            featureUsageRepository.save(featureUsage);
            log.debug(
                    "Logged usage: user={}, feature={}, product={}, release={}, action={}, hash={}",
                    userId,
                    featureCode,
                    productCode,
                    releaseCode,
                    actionType,
                    eventHash);
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("uk_feature_usage_event_hash")) {
                log.debug(
                        "Duplicate usage event prevented by database constraint: user={}, feature={}, action={}",
                        userId,
                        featureCode,
                        actionType);
                // This is normal during reprocessing - just ignore
            } else {
                log.error("Failed to log usage event due to data integrity violation", e);
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to log usage event", e);
            // Don't throw exception to avoid breaking the main flow
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

    /**
     * Generate event hash for deduplication (can be used as idempotency key).
     * Creates deterministic hash from event key fields with time window grouping.
     */
    private String generateEventHash(
            String userId, String featureCode, String productCode, ActionType actionType, Instant timestamp) {
        try {
            // Round timestamp to 5-minute windows to group similar events
            long roundedMinutes = timestamp.getEpochSecond() / 300 * 300; // 300 seconds = 5 minutes
            Instant roundedTimestamp = Instant.ofEpochSecond(roundedMinutes);

            // Create deterministic string from key fields
            String combined = userId + ":" + (featureCode != null ? featureCode : "null")
                    + ":" + (productCode != null ? productCode : "null")
                    + ":" + actionType
                    + ":" + roundedTimestamp;

            // Generate SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            // Return first 16 characters for shorter hash
            return hexString.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            log.warn("Failed to generate event hash", e);
            // Fallback: use UUID if hash generation fails
            return UUID.randomUUID().toString().substring(0, 16);
        }
    }

    /**
     * Create device fingerprint using hash(device:ip) for anonymous user tracking.
     * GDPR compliant - uses hash instead of storing actual IP.
     */
    private String createDeviceFingerprint(String ipAddress, String userAgent) {
        try {
            String combined = userAgent + ":" + ipAddress;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            // Return first 16 characters for shorter fingerprint
            return hexString.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            log.warn("Failed to create device fingerprint", e);
            return null;
        }
    }

    /**
     * Extract location from IP address.
     * Placeholder implementation - in production would use GeoIP service.
     * Returns only country code for GDPR compliance (not city/precise location).
     */
    private String extractLocation(String ipAddress) {
        // Placeholder: In production, use MaxMind GeoIP2 or similar service
        // For now, return null or "UNKNOWN"
        // Example: return geoIpService.getCountryCode(ipAddress);
        return null;
    }

    // New API methods for programmatic usage tracking

    @Transactional
    public FeatureUsageDto createUsageEvent(
            String userId,
            String featureCode,
            String productCode,
            String releaseCode,
            ActionType actionType,
            Map<String, Object> context,
            String ipAddress,
            String userAgent) {

        logUsage(userId, featureCode, productCode, releaseCode, actionType, context, ipAddress, userAgent);

        // Find the most recent usage event for this user and action
        Page<FeatureUsage> recentUsage = featureUsageRepository.findWithFiltersPaginated(
                actionType, null, null, userId, featureCode, productCode, PageRequest.of(0, 1));

        if (!recentUsage.isEmpty()) {
            FeatureUsage usage = recentUsage.getContent().get(0);
            return new FeatureUsageDto(
                    usage.getId(),
                    usage.getUserId(),
                    usage.getFeatureCode(),
                    usage.getProductCode(),
                    usage.getReleaseCode(),
                    usage.getActionType(),
                    usage.getTimestamp(),
                    usage.getContext(),
                    ipAddress,
                    userAgent);
        }

        return null;
    }

    // Statistics methods
    public UsageStatsDto getOverallStats(ActionType actionType, Instant startDate, Instant endDate) {
        long totalUsageCount = featureUsageRepository.countWithFilters(actionType, startDate, endDate);
        long uniqueUserCount = featureUsageRepository.countUniqueUsersWithFilters(actionType, startDate, endDate);
        long uniqueFeatureCount = featureUsageRepository.countUniqueFeaturesWithFilters(actionType, startDate, endDate);
        long uniqueProductCount = featureUsageRepository.countUniqueProductsWithFilters(actionType, startDate, endDate);

        Map<ActionType, Long> usageByActionType =
                featureUsageRepository.findUsageByActionType(actionType, startDate, endDate).stream()
                        .collect(Collectors.toMap(row -> (ActionType) row[0], row -> (Long) row[1]));

        List<TopItemDto> topFeatures =
                featureUsageRepository.findTopFeatures(startDate, endDate, PageRequest.of(0, 10)).stream()
                        .map(row -> new TopItemDto((String) row[0], (Long) row[1]))
                        .collect(Collectors.toList());

        List<TopItemDto> topProducts =
                featureUsageRepository.findTopProducts(startDate, endDate, PageRequest.of(0, 10)).stream()
                        .map(row -> new TopItemDto((String) row[0], (Long) row[1]))
                        .collect(Collectors.toList());

        List<TopItemDto> topUsers =
                featureUsageRepository.findTopUsers(startDate, endDate, PageRequest.of(0, 10)).stream()
                        .map(row -> new TopItemDto((String) row[0], (Long) row[1]))
                        .collect(Collectors.toList());

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

    public FeatureStatsDto getFeatureStats(
            String featureCode, ActionType actionType, Instant startDate, Instant endDate) {
        long totalUsageCount =
                featureUsageRepository.countByFeatureCodeWithFilters(featureCode, actionType, startDate, endDate);
        long uniqueUserCount = featureUsageRepository.countUniqueUsersByFeatureCodeWithFilters(
                featureCode, actionType, startDate, endDate);

        Map<ActionType, Long> usageByActionType =
                featureUsageRepository.findUsageByActionType(actionType, startDate, endDate).stream()
                        .collect(Collectors.toMap(row -> (ActionType) row[0], row -> (Long) row[1]));

        List<TopItemDto> topUsers =
                featureUsageRepository
                        .findTopUsersByFeatureCode(featureCode, actionType, startDate, endDate, PageRequest.of(0, 10))
                        .stream()
                        .map(row -> new TopItemDto((String) row[0], (Long) row[1]))
                        .collect(Collectors.toList());

        List<TopItemDto> usageByProduct =
                featureUsageRepository.findFeatureUsageByProduct(featureCode, actionType, startDate, endDate).stream()
                        .map(row -> new TopItemDto((String) row[0], (Long) row[1]))
                        .collect(Collectors.toList());

        return new FeatureStatsDto(
                featureCode, totalUsageCount, uniqueUserCount, usageByActionType, topUsers, usageByProduct);
    }

    public ProductStatsDto getProductStats(
            String productCode, ActionType actionType, Instant startDate, Instant endDate) {
        long totalUsageCount =
                featureUsageRepository.countByProductCodeWithFilters(productCode, actionType, startDate, endDate);
        long uniqueUserCount = featureUsageRepository.countUniqueUsersByProductCodeWithFilters(
                productCode, actionType, startDate, endDate);
        long uniqueFeatureCount = featureUsageRepository.countUniqueFeaturesByProductCodeWithFilters(
                productCode, actionType, startDate, endDate);

        Map<ActionType, Long> usageByActionType =
                featureUsageRepository.findUsageByActionType(actionType, startDate, endDate).stream()
                        .collect(Collectors.toMap(row -> (ActionType) row[0], row -> (Long) row[1]));

        List<TopItemDto> topFeatures = featureUsageRepository
                .findTopFeaturesByProductCode(productCode, actionType, startDate, endDate, PageRequest.of(0, 10))
                .stream()
                .map(row -> new TopItemDto((String) row[0], (Long) row[1]))
                .collect(Collectors.toList());

        List<TopItemDto> topUsers =
                featureUsageRepository
                        .findTopUsersByProductCode(productCode, actionType, startDate, endDate, PageRequest.of(0, 10))
                        .stream()
                        .map(row -> new TopItemDto((String) row[0], (Long) row[1]))
                        .collect(Collectors.toList());

        return new ProductStatsDto(
                productCode,
                totalUsageCount,
                uniqueUserCount,
                uniqueFeatureCount,
                usageByActionType,
                topFeatures,
                topUsers);
    }

    // Events list methods (paginated)
    public Page<FeatureUsageDto> getAllEventsPaginated(
            ActionType actionType,
            Instant startDate,
            Instant endDate,
            String userId,
            String featureCode,
            String productCode,
            Pageable pageable) {
        Page<FeatureUsage> events = featureUsageRepository.findWithFiltersPaginated(
                actionType, startDate, endDate, userId, featureCode, productCode, pageable);
        return events.map(featureUsageMapper::toDto);
    }

    public Page<FeatureUsageDto> getFeatureEventsPaginatedWithFilters(
            String featureCode, ActionType actionType, Instant startDate, Instant endDate, Pageable pageable) {
        Page<FeatureUsage> events = featureUsageRepository.findFeatureEventsWithFiltersPaginated(
                featureCode, actionType, startDate, endDate, pageable);
        return events.map(featureUsageMapper::toDto);
    }

    public Page<FeatureUsageDto> getProductEventsPaginatedWithFilters(
            String productCode, ActionType actionType, Instant startDate, Instant endDate, Pageable pageable) {
        Page<FeatureUsage> events = featureUsageRepository.findProductEventsWithFiltersPaginated(
                productCode, actionType, startDate, endDate, pageable);
        return events.map(featureUsageMapper::toDto);
    }

    // Top rankings methods
    public List<TopItemDto> getTopFeatures(Instant startDate, Instant endDate, int limit) {
        return featureUsageRepository.findTopFeatures(startDate, endDate, PageRequest.of(0, limit)).stream()
                .map(row -> new TopItemDto((String) row[0], (Long) row[1]))
                .collect(Collectors.toList());
    }

    public List<TopItemDto> getTopUsers(Instant startDate, Instant endDate, int limit) {
        return featureUsageRepository.findTopUsers(startDate, endDate, PageRequest.of(0, limit)).stream()
                .map(row -> new TopItemDto((String) row[0], (Long) row[1]))
                .collect(Collectors.toList());
    }

    // Paginated methods
    public Page<FeatureUsageDto> getUserEvents(String userId, Pageable pageable) {
        Page<FeatureUsage> events = featureUsageRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
        return events.map(featureUsageMapper::toDto);
    }

    public Page<FeatureUsageDto> getFeatureEventsPaginated(String featureCode, Pageable pageable) {
        Page<FeatureUsage> events = featureUsageRepository.findByFeatureCodeOrderByTimestampDesc(featureCode, pageable);
        return events.map(featureUsageMapper::toDto);
    }

    public Page<FeatureUsageDto> getProductEventsPaginated(String productCode, Pageable pageable) {
        Page<FeatureUsage> events = featureUsageRepository.findByProductCodeOrderByTimestampDesc(productCode, pageable);
        return events.map(featureUsageMapper::toDto);
    }
}

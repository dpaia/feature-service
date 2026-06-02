package com.sivalabs.ft.features.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import com.sivalabs.ft.features.domain.models.ActionType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureUsageService {
    private static final Logger log = LoggerFactory.getLogger(FeatureUsageService.class);

    private final FeatureUsageRepository featureUsageRepository;
    private final ObjectMapper objectMapper;
    private final com.sivalabs.ft.features.ApplicationProperties applicationProperties;

    public FeatureUsageService(
            FeatureUsageRepository featureUsageRepository,
            ObjectMapper objectMapper,
            com.sivalabs.ft.features.ApplicationProperties applicationProperties) {
        this.featureUsageRepository = featureUsageRepository;
        this.objectMapper = objectMapper;
        this.applicationProperties = applicationProperties;
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

            featureUsageRepository.save(featureUsage);
            log.debug(
                    "Logged usage: user={}, feature={}, product={}, release={}, action={}",
                    userId,
                    featureCode,
                    productCode,
                    releaseCode,
                    actionType);
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
}

package com.sivalabs.ft.features.domain.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnonymizationUtils {
    private static final Logger log = LoggerFactory.getLogger(AnonymizationUtils.class);
    private static final SecureRandom random = new SecureRandom();
    private static final String SALT_PREFIX = "ft-analytics-";

    private AnonymizationUtils() {}

    /**
     * @param userId the original user ID
     * @return hashed user ID or null if input is null
     */
    public static String anonymizeUserId(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String saltedInput = SALT_PREFIX + userId;
            byte[] hash = digest.digest(saltedInput.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to anonymize user ID", e);
            return userId;
        }
    }

    /**
     * @param ipAddress the original IP address
     * @return anonymized IP address or null if input is null
     */
    public static String anonymizeIpAddress(String ipAddress) {
        if (ipAddress == null) {
            return null;
        }

        if (ipAddress.contains(".")) {
            String[] parts = ipAddress.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + "." + parts[2] + ".0";
            }
        }

        if (ipAddress.contains(":")) {
            String[] parts = ipAddress.split(":");
            if (parts.length >= 4) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 4; i++) {
                    if (i > 0) sb.append(":");
                    sb.append(parts[i]);
                }
                sb.append("::0");
                return sb.toString();
            }
        }

        return ipAddress;
    }

    /**
     * @param userAgent the original user agent string
     * @return anonymized user agent or null if input is null
     */
    public static String anonymizeUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }

        String browser = "Unknown";
        if (userAgent.contains("Chrome")) {
            browser = "Chrome";
        } else if (userAgent.contains("Firefox")) {
            browser = "Firefox";
        } else if (userAgent.contains("Safari")) {
            browser = "Safari";
        } else if (userAgent.contains("Edge")) {
            browser = "Edge";
        } else if (userAgent.contains("Opera")) {
            browser = "Opera";
        }

        String os = "Unknown";
        if (userAgent.contains("Windows")) {
            os = "Windows";
        } else if (userAgent.contains("Mac OS")) {
            os = "MacOS";
        } else if (userAgent.contains("Linux")) {
            os = "Linux";
        } else if (userAgent.contains("Android")) {
            os = "Android";
        } else if (userAgent.contains("iOS")) {
            os = "iOS";
        }

        return browser + "/" + os;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

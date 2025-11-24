package com.sivalabs.ft.features.api.utils;

import jakarta.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class SecurityUtils {

    public static String getCurrentUsername() {
        var loginUserDetails = getLoginUserDetails();
        var username = loginUserDetails.get("username");
        if (loginUserDetails.isEmpty() || username == null) {
            return null;
        }
        return String.valueOf(username);
    }

    public static String getCurrentUserId() {
        String username = getCurrentUsername();
        return username != null ? username : "anonymous";
    }

    public static Map<String, Object> createAnonymousContext(HttpServletRequest request) {
        Map<String, Object> context = new HashMap<>();
        context.put("deviceFingerprint", generateDeviceFingerprint(request));
        context.put("location", "RU"); // Default location as per GDPR requirement
        return context;
    }

    private static String generateDeviceFingerprint(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String ipAddress = getClientIpAddress(request);
        String fingerprint = userAgent + ipAddress;

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(fingerprint.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16); // Take first 16 characters
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash
            return String.valueOf(fingerprint.hashCode()).substring(0, 16);
        }
    }

    private static String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    static Map<String, Object> getLoginUserDetails() {
        Map<String, Object> map = new HashMap<>();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return map;
        }
        Jwt jwt = (Jwt) jwtAuth.getPrincipal();

        map.put("username", jwt.getClaimAsString("preferred_username"));
        map.put("email", jwt.getClaimAsString("email"));
        map.put("name", jwt.getClaimAsString("name"));
        map.put("token", jwt.getTokenValue());
        map.put("authorities", authentication.getAuthorities());
        map.put("roles", getRoles(jwt));

        return map;
    }

    private static List<String> getRoles(Jwt jwt) {
        Map<String, Object> realm_access = (Map<String, Object>) jwt.getClaims().get("realm_access");
        if (realm_access != null && !realm_access.isEmpty()) {
            return (List<String>) realm_access.get("roles");
        }
        return List.of();
    }
}

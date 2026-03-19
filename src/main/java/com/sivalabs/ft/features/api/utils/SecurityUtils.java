package com.sivalabs.ft.features.api.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
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

    public static List<String> getCurrentUserRoles() {
        var loginUserDetails = getLoginUserDetails();
        var roles = loginUserDetails.get("roles");
        if (roles instanceof List) {
            return (List<String>) roles;
        }
        return List.of();
    }

    public static boolean hasRole(String role) {
        List<String> userRoles = getCurrentUserRoles();
        return userRoles.contains("ROLE_" + role);
    }

    public static boolean hasAnyRole(String... roles) {
        List<String> userRoles = getCurrentUserRoles();
        for (String role : roles) {
            if (userRoles.contains("ROLE_" + role)) {
                return true;
            }
        }
        return false;
    }

    public static void requireRole(String role) {
        if (!hasRole(role)) {
            throw new AccessDeniedException("Insufficient permissions");
        }
    }

    public static void requireAnyRole(String... roles) {
        if (!hasAnyRole(roles)) {
            throw new AccessDeniedException("Insufficient permissions");
        }
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

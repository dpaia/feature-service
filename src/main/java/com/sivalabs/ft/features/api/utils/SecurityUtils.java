package com.sivalabs.ft.features.api.utils;

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

    static Map<String, Object> getLoginUserDetails() {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private static List<String> getRoles(Jwt jwt) {
        Map<String, Object> realm_access = (Map<String, Object>) jwt.getClaims().get("realm_access");
        if (realm_access != null && !realm_access.isEmpty()) {
            return (List<String>) realm_access.get("roles");
        }
        return List.of();
    }
}

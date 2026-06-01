package com.sivalabs.ft.features.api.utils;

import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

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
        Map<String, Object> map = new HashMap<>();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return map;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            map.put("username", userDetails.getUsername());
            map.put("authorities", authentication.getAuthorities());
        }

        return map;
    }
}

package com.sivalabs.ft.features.api.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecurityUtilsGetLoginUserDetailsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldExtractUserDetailsFromJwt() {
        var jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of(
                        "preferred_username", "testuser",
                        "email", "test@example.com",
                        "name", "Test User",
                        "realm_access", Map.of("roles", List.of("developer", "admin"))));
        var auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Map<String, Object> details = SecurityUtils.getLoginUserDetails();

        assertThat(details).containsEntry("username", "testuser");
        assertThat(details).containsEntry("email", "test@example.com");
        assertThat(details).containsEntry("name", "Test User");
        assertThat(details).containsEntry("token", "token-value");
        assertThat(details).containsKey("authorities");
        assertThat(details).containsEntry("roles", List.of("developer", "admin"));
    }

    @Test
    void shouldReturnEmptyMapWhenNoJwtAuth() {
        SecurityContextHolder.clearContext();

        Map<String, Object> details = SecurityUtils.getLoginUserDetails();

        assertThat(details).isEmpty();
    }
}

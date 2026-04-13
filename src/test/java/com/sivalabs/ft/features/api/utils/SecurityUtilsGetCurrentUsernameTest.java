package com.sivalabs.ft.features.api.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecurityUtilsGetCurrentUsernameTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnUsernameFromJwt() {
        var jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("preferred_username", "testuser"));
        var auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        String username = SecurityUtils.getCurrentUsername();

        assertThat(username).isEqualTo("testuser");
    }

    @Test
    void shouldReturnNullWhenNoAuthentication() {
        SecurityContextHolder.clearContext();

        String username = SecurityUtils.getCurrentUsername();

        assertThat(username).isNull();
    }
}

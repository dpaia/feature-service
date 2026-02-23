package com.sivalabs.ft.features.config;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableWebSecurity
class SecurityConfig {

    static final String ROLE_ADMIN = "ADMIN";
    static final String ROLE_PRODUCT_MANAGER = "PRODUCT_MANAGER";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http.authorizeHttpRequests(c -> c.requestMatchers(
                                "/favicon.ico",
                                "/actuator/**",
                                "/error",
                                "/swagger-ui.*",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs.*")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/releases/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/milestones/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/features/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/comments/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/milestones/**")
                        .hasRole(ROLE_ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/milestones/**")
                        .hasAnyRole(ROLE_PRODUCT_MANAGER, ROLE_ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/milestones/**")
                        .hasAnyRole(ROLE_PRODUCT_MANAGER, ROLE_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/releases/**")
                        .hasRole(ROLE_ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/releases/**")
                        .hasAnyRole(ROLE_PRODUCT_MANAGER, ROLE_ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/releases/**")
                        .hasAnyRole(ROLE_PRODUCT_MANAGER, ROLE_ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/api/releases/**")
                        .hasAnyRole(ROLE_PRODUCT_MANAGER, ROLE_ADMIN)
                        .anyRequest()
                        .authenticated())
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(CorsConfigurer::disable)
                .csrf(CsrfConfigurer::disable)
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                                .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null || realmAccess.isEmpty()) {
                return List.of();
            }
            Collection<String> roles = (Collection<String>) realmAccess.get("roles");
            if (roles == null || roles.isEmpty()) {
                return List.of();
            }
            return roles.stream()
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        });
        return jwtAuthenticationConverter;
    }

    /**
     * Handles AccessDeniedException for authenticated users who lack the required role.
     * Returns a Problem Detail response (403 Forbidden).
     *
     * Note: This is the single handler for access denial. A duplicate @ExceptionHandler
     * in GlobalExceptionHandler would never be reached, as Spring Security intercepts
     * these exceptions in the filter chain before they reach the DispatcherServlet.
     */
    @Bean
    AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(FORBIDDEN, "Insufficient permissions");
            problemDetail.setTitle("Forbidden");
            problemDetail.setProperty("timestamp", Instant.now());

            response.setStatus(FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
        };
    }
}

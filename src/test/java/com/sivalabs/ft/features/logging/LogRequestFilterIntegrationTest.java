package com.sivalabs.ft.features.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.TestcontainersConfiguration;
import com.sivalabs.ft.features.WithMockOAuth2User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

@Import({
    TestcontainersConfiguration.class,
    LogRequestFilterIntegrationTest.TestConfig.class,
    LogRequestFilterIntegrationTest.TestController.class
})
class LogRequestFilterIntegrationTest extends AbstractIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MDCCapturingFilter mdcCapturingFilter;

    @AfterEach
    void tearDown() {
        // Clear security context after each test
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldAddRequestIdToResponseHeadersWhenProvided() throws Exception {
        // Given
        String requestId = "test-request-id-123";

        // When/Then
        mockMvc.perform(get("/test-endpoint").header("X-Request-ID", requestId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", requestId));

        // Verify MDC was set correctly during request processing
        assertThat(mdcCapturingFilter.getCapturedRequestId()).isEqualTo(requestId);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldGenerateRequestIdWhenNotProvided() throws Exception {
        // When
        MvcResult result = mockMvc.perform(get("/test-endpoint"))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String generatedRequestId = result.getResponse().getHeader("X-Request-ID");
        assertThat(generatedRequestId).isNotNull().isNotEmpty();
        assertThat(mdcCapturingFilter.getCapturedRequestId()).isEqualTo(generatedRequestId);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public MDCCapturingFilter mdcCapturingFilter() {
            return new MDCCapturingFilter();
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/test-endpoint")
        public String test() {
            return "test";
        }
    }

    /**
     * Special filter that captures MDC values during request processing
     * This runs after the RequestContextFilter but before the request completes
     */
    static class MDCCapturingFilter extends OncePerRequestFilter {
        private final AtomicReference<String> requestId = new AtomicReference<>();

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws jakarta.servlet.ServletException, java.io.IOException {
            // Capture the MDC values set by the RequestContextFilter
            requestId.set(MDC.get("requestId"));

            filterChain.doFilter(request, response);
        }

        public String getCapturedRequestId() {
            return requestId.get();
        }
    }
}

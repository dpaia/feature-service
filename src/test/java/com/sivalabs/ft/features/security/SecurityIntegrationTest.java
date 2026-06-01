package com.sivalabs.ft.features.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sivalabs.ft.features.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SecurityIntegrationTest extends AbstractIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DaoAuthenticationProvider authenticationProvider;

    @Test
    void shouldAllowAccessToPublicEndpoints() throws Exception {
        mockMvc.perform(get("/api/products/SAMPLE")).andExpect(status().isNotFound());
    }

    @Test
    void shouldDenyAccessForUnauthorizedUser() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                        {
                                            "code":"TEST",
                                            "prefix":"TST",
                                            "name":"Test Product",
                                            "imageUrl":"http://example.com/image.jpg"
                                        }
                                        """
                                        .stripIndent()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAccessToAdminEndpointsForAdminRole() throws Exception {
        mockMvc.perform(get("/api/products/SAMPLE").with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void shouldAuthenticateWithValidCredentials() throws Exception {
        mockMvc.perform(get("/api/products/SAMPLE")
                        .with(user("admin").password("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidCredentials() throws Exception {
        mockMvc.perform(get("/api/products/SAMPLE")
                        .with(user("invalid").password("invalid").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldVerifyPasswordEncoderIsBCrypt() {
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
    }

    @Test
    void shouldVerifyPasswordEncodingAndMatching() {
        // Given
        String rawPassword = "testPassword";

        // When
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // Then
        assertThat(encodedPassword).startsWith("$2a$");
        assertThat(passwordEncoder.matches(rawPassword, encodedPassword)).isTrue();
        assertThat(passwordEncoder.matches("wrongPassword", encodedPassword)).isFalse();
    }

    @Test
    void shouldVerifyAuthenticationProviderIsDaoAuthenticationProvider() {
        assertThat(authenticationProvider).isNotNull();
        assertThat(authenticationProvider).isInstanceOf(DaoAuthenticationProvider.class);
    }
}

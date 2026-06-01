package com.sivalabs.ft.features.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sivalabs.ft.features.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureMockMvc
@Import({SecurityConfigTest.TestController.class})
public class SecurityConfigTest extends AbstractIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAuthenticateWithValidCredentials() throws Exception {
        // Test authentication with admin user
        mockMvc.perform(formLogin("/api/login").user("admin").password("admin123"))
                .andExpect(authenticated())
                .andExpect(authenticated().withRoles("ADMIN", "USER"));

        // Test authentication with regular user
        mockMvc.perform(formLogin("/api/login").user("user").password("user123"))
                .andExpect(authenticated())
                .andExpect(authenticated().withRoles("USER"));
    }

    @Test
    void shouldNotAuthenticateWithInvalidCredentials() throws Exception {
        // Test with invalid username
        mockMvc.perform(formLogin("/api/login").user("nonexistent").password("password"))
                .andExpect(unauthenticated());

        // Test with invalid password
        mockMvc.perform(formLogin("/api/login").user("admin").password("wrongpassword"))
                .andExpect(unauthenticated());
    }

    @Test
    void shouldAllowAccessToPublicEndpoints() throws Exception {
        // Test access to a public endpoint (no authentication required)
        mockMvc.perform(get("/api/products")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(
            username = "user",
            roles = {"USER"})
    void shouldAllowAccessToProtectedEndpointsForAuthenticatedUsers() throws Exception {
        // Test access to a protected endpoint with authenticated user
        mockMvc.perform(get("/api/protected-resource")).andExpect(status().isOk());
    }

    @Test
    void shouldAuthenticateAdminUser() throws Exception {
        mockMvc.perform(formLogin("/api/login").user("admin").password("admin123"))
                .andExpect(authenticated())
                .andExpect(authenticated().withUsername("admin"))
                .andExpect(authenticated().withRoles("ADMIN", "USER"));
    }

    @Test
    void shouldAuthenticateRegularUser() throws Exception {
        mockMvc.perform(formLogin("/api/login").user("user").password("user123"))
                .andExpect(authenticated())
                .andExpect(authenticated().withUsername("user"))
                .andExpect(authenticated().withRoles("USER"));
    }

    @Test
    void shouldConfigureLogout() throws Exception {
        mockMvc.perform(get("/api/logout").with(user("admin").roles("ADMIN", "USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(result ->
                        assertTrue(result.getResponse().getRedirectedUrl().contains("/login")));

        mockMvc.perform(get("/api/logout").with(user("user").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(result ->
                        assertTrue(result.getResponse().getRedirectedUrl().contains("/login")));
    }

    @Test
    void shouldSupportAPILoginEndpoint() throws Exception {
        // Admin login via API
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "admin")
                        .param("password", "admin123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertEquals("/", result.getResponse().getRedirectedUrl()));

        // Regular user login via API
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "user")
                        .param("password", "user123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertEquals("/", result.getResponse().getRedirectedUrl()));
    }

    @RestController
    public static class TestController {

        @GetMapping("/api/protected-resource")
        @ResponseStatus(HttpStatus.OK)
        public String protectedResource() {
            return "protected-resource";
        }

        @GetMapping("/api/protected-endpoint")
        @ResponseStatus(HttpStatus.OK)
        public String protectedEndpoint() {
            return "protected-endpoint";
        }
    }
}

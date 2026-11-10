package com.edu360.api.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.edu360.api.audit.AuditLogService;

/**
 * M-03 through the real Spring Security filter chains (not just the controller).
 *
 * WHY: RegM03Test calls the controller directly, so it can't catch the security layer
 * rejecting a request before it gets there. Spring's bearer-token filter on the main chain
 * would treat the service token as a user token and return 401 (wrong azp, no user record).
 * SecurityConfig therefore gives identity events their own chain, where
 * IdentityEventTokenValidator alone decides. These tests break if that chain is removed or
 * its paths drift from the controller's.
 *
 * The validator is mocked, and the "service token" is deliberately not a real JWT. If the
 * main chain's bearer filter ever ran on these paths, it would fail to decode it and
 * REG-M03-8 would get 401 instead of 200.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegM03SecurityChainTest {

    private static final String SERVICE = "Bearer service-token-accepted-by-validator";
    private static final String USER = "Bearer product-owner-user-token";
    private static final String BODY = "{\"eventType\":\"LOGIN\",\"userId\":\"user-1\"}";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private IdentityEventTokenValidator identityEventTokenValidator;
    @MockitoBean private AuthService authService;
    @MockitoBean private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        when(identityEventTokenValidator.isValidServiceToken(SERVICE)).thenReturn(true);
        when(identityEventTokenValidator.isValidServiceToken(USER)).thenReturn(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/internal/identity-events", "/v1/auth/internal/identity-events"})
    @DisplayName("REG-M03-8 a valid service token passes the filter chain and gets 200")
    void REG_M03_8(String path) throws Exception {
        mockMvc.perform(post(path).header("Authorization", SERVICE)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        verify(authService).clearLockout("user-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/internal/identity-events", "/v1/auth/internal/identity-events"})
    @DisplayName("REG-M03-9 no token, or only an X-Service-Auth secret, gets 401")
    void REG_M03_9(String path) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(path).header("X-Service-Auth", "edu360-internal-service-secret-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(authService, never()).clearLockout(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/internal/identity-events", "/v1/auth/internal/identity-events"})
    @DisplayName("REG-M03-10 a user token (e.g. Product Owner) gets 401")
    void REG_M03_10(String path) throws Exception {
        mockMvc.perform(post(path).header("Authorization", USER)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(authService, never()).clearLockout(anyString());
    }
}

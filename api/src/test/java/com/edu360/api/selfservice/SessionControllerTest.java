package com.edu360.api.selfservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.identity.UserType;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.selfservice.dto.SessionResponse;

/**
 * Plain unit tests for SessionController - same no-Spring-context style as AuthControllerTest.
 */
@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    @Mock private SessionService sessionService;
    @Mock private Jwt jwt;

    private SessionController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ACCOUNT_ID = UUID.randomUUID();

    private SessionController controller() {
        return new SessionController(sessionService);
    }

    // --------------------------------------------------------------------------------------- GET

    @Test
    void listSessions_noAuthentication_returns401() {
        controller = controller();
        ResponseEntity<List<SessionResponse>> response = controller.listSessions(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listSessions_authenticated_delegatesToServiceWithJwt() {
        controller = controller();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Test User", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());
        List<SessionResponse> expected = List.of(SessionResponse.builder().sessionId("s-1").build());
        when(sessionService.listCurrentSessions(jwt)).thenReturn(expected);

        ResponseEntity<List<SessionResponse>> response = controller.listSessions(authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    // ---------------------------------------------------------------------------------- DELETE

    @Test
    void revokeCurrentSession_noAuthentication_returns401WithoutCallingService() {
        controller = controller();
        ResponseEntity<Void> response = controller.revokeCurrentSession(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(sessionService, never()).revokeCurrentSession(any(TenantPrincipal.class), any());
        verify(sessionService, never()).revokeCurrentSession(any(PlatformPrincipal.class), any());
    }

    @Test
    void revokeCurrentSession_tenantPrincipal_callsTenantOverload() {
        controller = controller();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Test User", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());

        ResponseEntity<Void> response = controller.revokeCurrentSession(authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(sessionService).revokeCurrentSession(principal, jwt);
        verify(sessionService, never()).revokeCurrentSession(any(PlatformPrincipal.class), any());
    }

    @Test
    void revokeCurrentSession_platformPrincipal_callsPlatformOverload() {
        controller = controller();
        PlatformPrincipal principal = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PLATFORM_ADMIN,
                "admin@edu360-platform.local", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());

        ResponseEntity<Void> response = controller.revokeCurrentSession(authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(sessionService).revokeCurrentSession(principal, jwt);
        verify(sessionService, never()).revokeCurrentSession(any(TenantPrincipal.class), any());
    }
}

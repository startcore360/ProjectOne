package com.edu360.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.auth.dto.AuthTokenResponse;
import com.edu360.api.auth.dto.AuthUserResponse;
import com.edu360.api.auth.dto.ChangePasswordRequest;
import com.edu360.api.auth.dto.LoginRequest;
import com.edu360.api.auth.dto.RefreshRequest;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.identity.UserType;
import com.edu360.api.platform.PlatformRole;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Plain unit tests for AuthController - no Spring context/MockMvc, since the real security
 * pipeline (JWT verification, tenant resolution, RLS) is already covered by
 * TenantIssuerAuthenticationManagerResolverIT and the Step 8 live manual verification. These
 * tests exist to pin down this controller's own logic: switching correctly on principal type,
 * and refusing (401) rather than throwing when no identity is present.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private AuditLogService auditLogService;
    @Mock private HttpServletRequest httpServletRequest;
    @Mock private Jwt jwt;

    private AuthController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ACCOUNT_ID = UUID.randomUUID();

    private AuthController controller() {
        return new AuthController(authService, auditLogService);
    }

    // ------------------------------------------------------------------------------- login/refresh

    @Test
    void login_delegatesToAuthServiceWithHostAndRemoteAddress() {
        controller = controller();
        LoginRequest request = new LoginRequest();
        request.setEmail("teacher1");
        request.setPassword("pw");
        when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.5");
        AuthTokenResponse expected = AuthTokenResponse.builder().accessToken("at").build();
        when(authService.login(httpServletRequest, "teacher1", "pw", "10.0.0.5")).thenReturn(expected);

        ResponseEntity<AuthTokenResponse> response = controller.login(request, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void refresh_delegatesToAuthService() {
        controller = controller();
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("rt");
        AuthTokenResponse expected = AuthTokenResponse.builder().accessToken("at2").build();
        when(authService.refresh(httpServletRequest, "rt")).thenReturn(expected);

        ResponseEntity<AuthTokenResponse> response = controller.refresh(request, httpServletRequest);

        assertThat(response.getBody()).isSameAs(expected);
    }

    // --------------------------------------------------------------------------- change-password

    @Test
    void changePassword_noAuthentication_returns401WithoutCallingAuthService() {
        controller = controller();
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old");
        request.setNewPassword("new");

        ResponseEntity<AuthUserResponse> response = controller.changePassword(request, null, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(authService, never()).changeTenantPassword(any(), anyString(), anyString(), anyString(), anyString());
        verify(authService, never()).changePlatformPassword(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void changePassword_tenantPrincipal_callsChangeTenantPassword() {
        controller = controller();
        when(jwt.getSubject()).thenReturn("kc-subject");
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Test User", true);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());
        when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.5");
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old");
        request.setNewPassword("new");
        AuthUserResponse expected = AuthUserResponse.fromTenant(principal, "Test User");
        when(authService.changeTenantPassword(eq(principal), eq("kc-subject"), eq("old"), eq("new"), eq("10.0.0.5")))
                .thenReturn(expected);

        ResponseEntity<AuthUserResponse> response = controller.changePassword(request, authentication, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(authService, never()).changePlatformPassword(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void changePassword_platformPrincipal_callsChangePlatformPassword() {
        controller = controller();
        when(jwt.getSubject()).thenReturn("kc-subject");
        PlatformPrincipal principal = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PLATFORM_ADMIN,
                "admin@edu360-platform.local", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());
        when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.5");
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old");
        request.setNewPassword("new");
        AuthUserResponse expected = AuthUserResponse.fromPlatform(principal);
        when(authService.changePlatformPassword(eq(principal), eq("kc-subject"), eq("old"), eq("new"), eq("10.0.0.5")))
                .thenReturn(expected);

        ResponseEntity<AuthUserResponse> response = controller.changePassword(request, authentication, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(authService, never()).changeTenantPassword(any(), anyString(), anyString(), anyString(), anyString());
    }

    // ----------------------------------------------------------------------------------------- me

    @Test
    void me_noAuthentication_returns401() {
        controller = controller();
        ResponseEntity<AuthUserResponse> response = controller.me(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void me_tenantPrincipal_returnsTenantResponse() {
        controller = controller();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STUDENT,
                "Test Student", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());
        AuthUserResponse expected = AuthUserResponse.fromTenant(principal, "Test Student", "CLASS_TEACHER", "Class Teacher", "/me/profile/photo");
        when(authService.currentTenantUser(principal)).thenReturn(expected);

        ResponseEntity<AuthUserResponse> response = controller.me(authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().tenantId()).isEqualTo(TENANT_ID);
        assertThat(response.getBody().platformRole()).isNull();
        // AuthController must delegate role resolution to AuthService.currentTenantUser(...)
        // rather than building the response itself with AuthUserResponse.fromTenant directly -
        // that older shortcut never looked up a role at all.
        assertThat(response.getBody().roleCode()).isEqualTo("CLASS_TEACHER");
        assertThat(response.getBody().photoUrl()).isEqualTo("/me/profile/photo");
    }

    @Test
    void me_platformPrincipal_returnsPlatformResponse() {
        controller = controller();
        PlatformPrincipal principal = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.SUPPORT_MANAGER,
                "support@edu360-platform.local", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());

        ResponseEntity<AuthUserResponse> response = controller.me(authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().tenantId()).isNull();
        assertThat(response.getBody().platformRole()).isEqualTo(PlatformRole.SUPPORT_MANAGER);
    }

    // ------------------------------------------------------------------------------------- logout

    @Test
    void logout_noAuthentication_noAuditRecordButStillNoContent() {
        controller = controller();
        ResponseEntity<Void> response = controller.logout(null, httpServletRequest);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(auditLogService, never()).record(any(), any(), anyString(), anyString(), any());
    }

    @Test
    void logout_tenantPrincipal_recordsAuditWithDisplayName() {
        controller = controller();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Test User", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());
        when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.5");

        controller.logout(authentication, httpServletRequest);

        verify(auditLogService).record(AuditEventType.LOGOUT, null, "Test User", "10.0.0.5", null);
    }
}

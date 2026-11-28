package com.edu360.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakAdminClient.KeycloakUser;
import com.edu360.api.config.keycloak.KeycloakAuthClient;
import com.edu360.api.config.keycloak.KeycloakAuthClient.PasswordCheck;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.exception.AuthChallengeException;
import com.edu360.api.config.keycloak.KeycloakRealmSecurityService;
import com.edu360.api.tenant.LoginTenantResolver;

import jakarta.servlet.http.HttpServletRequest;

/** MFA-01: signing in with a backup code never issues tokens; it leads to setting up a new authenticator. */
@ExtendWith(MockitoExtension.class)
class AuthServiceRecoveryCodeTest {

    private static final String REALM = "edu360-platform";
    private static final String EMAIL = "owner@edu360-platform.local";

    @Mock private LoginTenantResolver loginTenantResolver;
    @Mock private KeycloakAdminClient keycloakAdminClient;
    @Mock private KeycloakAuthClient keycloakAuthClient;
    @Mock private KeycloakProperties keycloakProperties;
    @Mock private AuditLogService auditLogService;
    @Mock private KeycloakRealmSecurityService realmSecurityService;
    @Mock private HttpServletRequest request;
    @Mock private MfaRecoveryService recovery;

    @InjectMocks private AuthService authService;

    private final KeycloakUser owner = new KeycloakUser("kc-owner", EMAIL, true, Map.of(), List.of());

    @BeforeEach
    void setUp() {
        authService.setMfaRecoveryService(recovery);
        when(loginTenantResolver.resolve(request)).thenReturn(new LoginTenantResolver.PlatformLogin(REALM));
    }

    private void passwordIs(PasswordCheck result) {
        when(keycloakAuthClient.verifyPassword(eq(REALM), eq(EMAIL), eq("pw"), any())).thenReturn(result);
    }

    @Test
    void aValidCodeRemovesTheOldAuthenticator_endsSessions_andAsksToSetUpANewOne() {
        passwordIs(PasswordCheck.VERIFIED);
        when(keycloakAdminClient.findUser(REALM, EMAIL)).thenReturn(Optional.of(owner));
        when(recovery.consume(REALM, "kc-owner", "K7QD4-9MXRT")).thenReturn(true);
        when(keycloakAdminClient.startOtpEnrolment(REALM, EMAIL)).thenReturn(Optional.of(
                new KeycloakAdminClient.OtpEnrolment("enrol-1", "data:image/png;base64,QR", "SECRET", 600)));

        Throwable thrown = catchThrowable(() ->
                authService.loginWithRecoveryCode(request, EMAIL, "pw", "K7QD4-9MXRT", "127.0.0.1"));

        assertThat(thrown).isInstanceOf(AuthChallengeException.class);
        AuthChallengeException challenge = (AuthChallengeException) thrown;
        assertThat(challenge.getCode()).isEqualTo("MFA_ENROLMENT_REQUIRED");
        assertThat(challenge.getDetails()).containsEntry("enrolmentId", "enrol-1");
        verify(keycloakAdminClient).removeOtpCredentials(REALM, "kc-owner");
        verify(recovery).clear(REALM, "kc-owner");
        verify(keycloakAdminClient).logoutUser(REALM, "kc-owner");
        verify(auditLogService).record(eq(AuditEventType.MFA_RECOVERY_CODE_USED), any(), eq(EMAIL), anyString(), anyString());
        // A backup code alone never produces tokens.
        verify(keycloakAuthClient, never()).login(anyString(), anyString(), anyString());
        verify(keycloakAuthClient, never()).login(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void aWrongCodeIsRefusedAndTheAuthenticatorIsKept() {
        passwordIs(PasswordCheck.VERIFIED);
        when(keycloakAdminClient.findUser(REALM, EMAIL)).thenReturn(Optional.of(owner));
        when(recovery.consume(REALM, "kc-owner", "AAAAA-BBBBB")).thenReturn(false);

        Throwable thrown = catchThrowable(() ->
                authService.loginWithRecoveryCode(request, EMAIL, "pw", "AAAAA-BBBBB", "127.0.0.1"));

        assertThat(((AuthChallengeException) thrown).getCode()).isEqualTo("INVALID_RECOVERY_CODE");
        verify(keycloakAdminClient, never()).removeOtpCredentials(anyString(), anyString());
        verify(keycloakAdminClient, never()).logoutUser(anyString(), anyString());
    }

    @Test
    void theCodeIsNotEvenCheckedUntilThePasswordIsProven() {
        passwordIs(PasswordCheck.INVALID);
        when(keycloakAdminClient.findUser(REALM, EMAIL)).thenReturn(Optional.of(owner));

        Throwable thrown = catchThrowable(() ->
                authService.loginWithRecoveryCode(request, EMAIL, "pw", "K7QD4-9MXRT", "127.0.0.1"));

        assertThat(thrown).isInstanceOf(AuthChallengeException.class);
        assertThat(((AuthChallengeException) thrown).getStatus()).isEqualTo(401);
        verify(recovery, never()).consume(anyString(), anyString(), anyString());
        verify(keycloakAdminClient, never()).removeOtpCredentials(anyString(), anyString());
    }
}

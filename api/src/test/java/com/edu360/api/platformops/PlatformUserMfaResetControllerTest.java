package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.edu360.api.auth.MfaRecoveryService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.exception.ApiException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platformops.dto.PlatformMfaResetRequest;
import com.edu360.api.platformops.role.PlatformAuditWriter;

/** E8-S4 / MFA-01: the Product Owner resets a platform staff member's two-step login. */
@ExtendWith(MockitoExtension.class)
class PlatformUserMfaResetControllerTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();
    private static final String REALM = "edu360-platform";

    @Mock private PlatformPermissionService permissions;
    @Mock private PlatformUserRepository users;
    @Mock private KeycloakAdminClient keycloak;
    @Mock private KeycloakProperties properties;
    @Mock private MfaRecoveryService recovery;
    @Mock private PlatformAuditWriter auditWriter;
    @Mock private PlatformNotificationService notifications;
    @Mock private Authentication authentication;

    private PlatformUserMfaResetController controller;
    private final PlatformPrincipal owner = new PlatformPrincipal(OWNER_ID, PlatformRole.PRODUCT_OWNER, "owner@edu360-platform.local", false);

    @BeforeEach
    void setUp() {
        controller = new PlatformUserMfaResetController(permissions, users, keycloak, properties, recovery,
                auditWriter, notifications);
        when(authentication.getPrincipal()).thenReturn(owner);
    }

    @Test
    void allow_productOwnerResetsStaff_removesAuthenticatorAndCodes_endsSessions_audits_notifies() {
        PlatformUser staff = mock(PlatformUser.class);
        when(staff.getIdentitySubject()).thenReturn("kc-staff");
        when(users.findById(STAFF_ID)).thenReturn(Optional.of(staff));
        when(properties.getPlatformRealm()).thenReturn(REALM);

        var response = controller.resetMfa(STAFF_ID, new PlatformMfaResetRequest("New phone, verified by video call"), authentication);

        assertThat(response.getBody()).containsEntry("status", "RESET");
        verify(permissions).authorize(owner, "PLATFORM_USER_MANAGE");
        verify(keycloak).removeOtpCredentials(REALM, "kc-staff");
        verify(recovery).clear(REALM, "kc-staff");
        verify(keycloak).logoutUser(REALM, "kc-staff");
        verify(auditWriter).record(eq(owner), any(), eq("PLATFORM_USER_MFA_RESET"), eq("RESET_MFA"),
                eq("PLATFORM_USER_MANAGE"), eq("PLATFORM_USER"), eq(STAFF_ID), eq(PlatformAuditWriter.SUCCESS), any(Map.class));
        verify(notifications).notify(eq(STAFF_ID), eq("MFA_RESET"), anyString(), anyString(), eq("PLATFORM_USER"),
                eq(STAFF_ID), eq("SECURITY"), eq(OWNER_ID));
    }

    @Test
    void deny_aRoleWithoutPlatformUserManageChangesNothing() {
        doThrow(new UnauthorizedException("denied")).when(permissions).authorize(any(), eq("PLATFORM_USER_MANAGE"));

        assertThatThrownBy(() -> controller.resetMfa(STAFF_ID, new PlatformMfaResetRequest("reason text"), authentication))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(keycloak, recovery, notifications);
    }

    @Test
    void guard_theProductOwnerCannotResetTheirOwnTwoStepLogin() {
        assertThatThrownBy(() -> controller.resetMfa(OWNER_ID, new PlatformMfaResetRequest("reason text"), authentication))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot reset your own");
        verifyNoInteractions(keycloak, recovery, notifications);
    }
}

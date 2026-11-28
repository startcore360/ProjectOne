package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.auth.MfaRecoveryService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.exception.ApiException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.UserIdentityLink;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.dto.MfaResetRequest;
import com.edu360.api.selfservice.InAppNotifier;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

/** USER_MFA_RESET (MFA-01): ALLOW / DENY / SCOPE / GUARD for an admin resetting someone's two-step login. */
@ExtendWith(MockitoExtension.class)
class UserMfaResetActionTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();
    private static final String REALM = "abc-realm";
    private static final String TARGET_KC_ID = "kc-target";

    @Mock private PermissionEvaluatorService permissions;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserIdentityLinkRepository linkRepository;
    @Mock private KeycloakAdminClient keycloak;
    @Mock private AuditLogService audit;
    @Mock private MfaRecoveryService recovery;
    @Mock private UserGovernanceGuard governanceGuard;
    @Mock private InAppNotifier notifier;
    @Mock private Authentication authentication;

    private UserMfaResetController controller;

    @BeforeEach
    void setUp() {
        controller = new UserMfaResetController(permissions, tenantRepository, linkRepository, keycloak, audit,
                recovery, governanceGuard, notifier);
        when(authentication.getPrincipal())
                .thenReturn(new TenantPrincipal(TENANT_ID, ADMIN_ID, UserType.STAFF, "College Admin", false));
    }

    private void targetExists() {
        Tenant tenant = mock(Tenant.class);
        when(tenant.getKeycloakRealm()).thenReturn(REALM);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        UserIdentityLink link = mock(UserIdentityLink.class);
        when(link.getIdentitySubject()).thenReturn(TARGET_KC_ID);
        when(linkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(TENANT_ID, TARGET_ID, IdentityLinkStatus.ACTIVE))
                .thenReturn(Optional.of(link));
    }

    private MfaResetRequest request() {
        return new MfaResetRequest(INSTITUTION_ID, "Lost phone, confirmed in person");
    }

    @Test
    void allow_removesAuthenticatorAndCodes_endsSessions_audits_andNotifiesTheUser() {
        targetExists();
        when(keycloak.removeOtpCredentials(REALM, TARGET_KC_ID)).thenReturn(1);

        ResponseEntity<Map<String, String>> response = controller.resetMfa(TENANT_ID, TARGET_ID, request(), authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("status", "RESET");
        verify(permissions).authorize(new RbacPrincipal(TENANT_ID, ADMIN_ID), "USER_MFA_RESET",
                RbacScope.institution(INSTITUTION_ID), RbacTarget.of(TARGET_ID));
        verify(governanceGuard).checkTargetUserAuthority(TENANT_ID, ADMIN_ID, TARGET_ID, INSTITUTION_ID);
        verify(keycloak).removeOtpCredentials(REALM, TARGET_KC_ID);
        verify(recovery).clear(REALM, TARGET_KC_ID);
        verify(keycloak).logoutUser(REALM, TARGET_KC_ID);
        verify(audit).record(eq(AuditEventType.USER_MFA_RESET), isNull(), eq(TARGET_KC_ID), anyString(),
                contains("Lost phone, confirmed in person"));
        verify(notifier).notify(eq(TENANT_ID), eq(TARGET_ID), eq(INSTITUTION_ID), eq("MFA_RESET"), anyString(),
                anyString(), eq("USER_ACCOUNT"), eq(TARGET_ID), eq(ADMIN_ID), eq("SECURITY"), anyString());
        // The password is never touched by a two-step reset.
        verify(keycloak, never()).resetPassword(anyString(), anyString(), anyString());
    }

    @Test
    void deny_withoutTheActionNothingIsChanged() {
        doThrow(new UnauthorizedException("USER_MFA_RESET not held")).when(permissions)
                .authorize(any(), eq("USER_MFA_RESET"), any(), any());

        assertThatThrownBy(() -> controller.resetMfa(TENANT_ID, TARGET_ID, request(), authentication))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(keycloak, recovery, notifier);
    }

    @Test
    void scope_aTargetAboveTheAdminsAuthorityIsRefusedByTheGovernanceGuard() {
        doThrow(new UnauthorizedException("target outranks actor")).when(governanceGuard)
                .checkTargetUserAuthority(TENANT_ID, ADMIN_ID, TARGET_ID, INSTITUTION_ID);

        assertThatThrownBy(() -> controller.resetMfa(TENANT_ID, TARGET_ID, request(), authentication))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(keycloak, recovery, notifier);
    }

    @Test
    void guard_nobodyResetsTheirOwnTwoStepLogin() {
        assertThatThrownBy(() -> controller.resetMfa(TENANT_ID, ADMIN_ID, request(), authentication))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot reset your own");
        verifyNoInteractions(keycloak, recovery, notifier);
    }

    @Test
    void guard_aUserWithoutAnActiveSignInIdentityIs404AndKeycloakIsNotCalled() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(linkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(TENANT_ID, TARGET_ID, IdentityLinkStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.resetMfa(TENANT_ID, TARGET_ID, request(), authentication))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(keycloak, recovery, notifier);
    }
}

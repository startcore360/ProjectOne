package com.edu360.api.rbac;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.UserIdentityLink;
import com.edu360.api.identity.UserIdentityLinkId;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.identity.UsernameKind;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.dto.InstitutionScopeRequest;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

/**
 * New test, added alongside the gap fix that wires KeycloakAdminClient.clearBruteForceLockout(...)
 * into UserPasswordResetUnlockController.unlockUser(...) (previously that endpoint only called
 * resetPassword, per that class's own GAP note - now superseded). Mirrors the ALLOW-path /
 * Mockito-mocked style of UserPasswordResetUnlockActionTest, which this file deliberately does
 * not edit (built by another agent this session) - a fresh test class instead, covering only the
 * one new behavior: /unlock calls BOTH resetPassword and clearBruteForceLockout.
 */
@ExtendWith(MockitoExtension.class)
class UserPasswordResetUnlockUnlockActionTest {

    @Mock private ActionCatalogRepository actionCatalogRepository;
    @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock private RoleActionRepository roleActionRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserIdentityLinkRepository userIdentityLinkRepository;
    @Mock private KeycloakAdminClient keycloakAdminClient;
    @Mock private Authentication authentication;
    @Mock private Tenant tenant;

    private static final String ACTION_CODE = "USER_PASSWORD_RESET_UNLOCK";
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID TARGET_USER_ID = UUID.randomUUID();
    private static final UUID ROLE_DEFINITION_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();
    private static final String REALM = "abc-university";

    private PermissionEvaluatorService evaluator() {
        return new PermissionEvaluatorService(actionCatalogRepository, userRoleAssignmentRepository, roleActionRepository);
    }

    private ActionCatalog action() {
        ActionCatalog a = new ActionCatalog();
        setField(a, "actionCode", ACTION_CODE);
        setField(a, "approvalAction", false);
        return a;
    }

    private UserRoleAssignment activeAssignment() {
        return UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, UUID.randomUUID()))
                .userAccountId(USER_ACCOUNT_ID)
                .roleDefinitionId(ROLE_DEFINITION_ID)
                .scopeType(ScopeType.INSTITUTION)
                .institutionId(INSTITUTION_ID)
                .effectiveFrom(Instant.now().minusSeconds(3600))
                .status(AssignmentStatus.ACTIVE)
                .build();
    }

    private UserIdentityLink activeLink() {
        return UserIdentityLink.builder()
                .id(new UserIdentityLinkId(TENANT_ID, UUID.randomUUID()))
                .userAccountId(TARGET_USER_ID)
                .identityRealm(REALM)
                .identitySubject("kc-target-subject")
                .usernameKind(UsernameKind.EMAIL)
                .status(IdentityLinkStatus.ACTIVE)
                .build();
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void unlockUser_resetsPasswordAndClearsBruteForceLockout() {
        PermissionEvaluatorService realEvaluator = evaluator();
        when(actionCatalogRepository.findById(ACTION_CODE)).thenReturn(Optional.of(action()));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment()));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, ACTION_CODE))
                .thenReturn(true);
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, TARGET_USER_ID, IdentityLinkStatus.ACTIVE))
                .thenReturn(Optional.of(activeLink()));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenant.getKeycloakRealm()).thenReturn(REALM);

        UserPasswordResetUnlockController controller = new UserPasswordResetUnlockController(
                realEvaluator, tenantRepository, userIdentityLinkRepository, keycloakAdminClient);
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF, "Admin", false);
        when(authentication.getPrincipal()).thenReturn(principal);

        InstitutionScopeRequest request = new InstitutionScopeRequest(INSTITUTION_ID);

        controller.unlockUser(TENANT_ID, TARGET_USER_ID, request, authentication);

        verify(keycloakAdminClient).resetPassword(eq(REALM), eq("kc-target-subject"), any());
        verify(keycloakAdminClient).clearBruteForceLockout(REALM, "kc-target-subject");
    }
}

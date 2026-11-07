package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.UserIdentityLink;
import com.edu360.api.identity.UserIdentityLinkId;
import com.edu360.api.identity.UsernameKind;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.dto.InstitutionScopeRequest;
import com.edu360.api.rbac.dto.PasswordResetResponse;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

/**
 * Covers the USER_PASSWORD_RESET_UNLOCK story ("Reset a password or unlock an account",
 * POST .../{id}/password-reset and POST .../{id}/unlock). ALLOW/DENY/SCOPE exercise
 * PermissionEvaluatorService.can(...) directly with USER_PASSWORD_RESET_UNLOCK, exactly like
 * the USER_CREATE tests in PermissionEvaluatorServiceTest - always an INSTITUTION-scoped check
 * per the spec (sql/05_ref_data.sql rows 455-459). GUARD exercises
 * UserPasswordResetUnlockController's own precondition: it never calls Keycloak for a target
 * that has no ACTIVE UserIdentityLink, rather than passing a null/garbage subject through to
 * KeycloakAdminClient.
 */
@ExtendWith(MockitoExtension.class)
class UserPasswordResetUnlockActionTest {

    @Mock private ActionCatalogRepository actionCatalogRepository;
    @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock private RoleActionRepository roleActionRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserIdentityLinkRepository userIdentityLinkRepository;
    @Mock private KeycloakAdminClient keycloakAdminClient;
    @Mock private AuditLogService auditLogService;
    @Mock private Authentication authentication;
    @Mock private Tenant tenant;

    private static final String ACTION_CODE = "USER_PASSWORD_RESET_UNLOCK";
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID TARGET_USER_ID = UUID.randomUUID();
    private static final UUID ROLE_DEFINITION_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();
    private static final UUID OTHER_INSTITUTION_ID = UUID.randomUUID();

    private PermissionEvaluatorService evaluator() {
        return new PermissionEvaluatorService(actionCatalogRepository, userRoleAssignmentRepository, roleActionRepository);
    }

    private ActionCatalog action() {
        ActionCatalog a = new ActionCatalog();
        setField(a, "actionCode", ACTION_CODE);
        setField(a, "approvalAction", false);
        return a;
    }

    private UserRoleAssignment activeAssignment(UUID institutionId) {
        return UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, UUID.randomUUID()))
                .userAccountId(USER_ACCOUNT_ID)
                .roleDefinitionId(ROLE_DEFINITION_ID)
                .scopeType(ScopeType.INSTITUTION)
                .institutionId(institutionId)
                .effectiveFrom(Instant.now().minusSeconds(3600))
                .status(AssignmentStatus.ACTIVE)
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

    // ------------------------------------------------------------------------------- ALLOW

    @Test
    void passwordResetUnlock_allowsWhenTheActiveRoleInThatExactInstitutionScopeHoldsTheAction() {
        PermissionEvaluatorService evaluator = evaluator();
        when(actionCatalogRepository.findById(ACTION_CODE)).thenReturn(Optional.of(action()));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment(INSTITUTION_ID)));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, ACTION_CODE))
                .thenReturn(true);

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), ACTION_CODE, RbacScope.institution(INSTITUTION_ID), RbacTarget.of(TARGET_USER_ID));

        assertThat(decision.allowed()).isTrue();
    }

    // -------------------------------------------------------------------------------- DENY

    @Test
    void passwordResetUnlock_deniesWhenTheActiveRoleInThatExactInstitutionScopeDoesNotHoldTheAction() {
        PermissionEvaluatorService evaluator = evaluator();
        when(actionCatalogRepository.findById(ACTION_CODE)).thenReturn(Optional.of(action()));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment(INSTITUTION_ID)));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, ACTION_CODE))
                .thenReturn(false);

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), ACTION_CODE, RbacScope.institution(INSTITUTION_ID), RbacTarget.of(TARGET_USER_ID));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("ACTION_NOT_GRANTED");
    }

    // ------------------------------------------------------------------------------- SCOPE

    @Test
    void passwordResetUnlock_deniesWhenPrincipalsActiveRoleIsInADifferentInstitutionThanRequested() {
        PermissionEvaluatorService evaluator = evaluator();
        when(actionCatalogRepository.findById(ACTION_CODE)).thenReturn(Optional.of(action()));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, OTHER_INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), ACTION_CODE, RbacScope.institution(OTHER_INSTITUTION_ID), RbacTarget.of(TARGET_USER_ID));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("NO_ACTIVE_ROLE_IN_SCOPE");
    }

    // ------------------------------------------------------------------------------- GUARD

    @Test
    void passwordResetUnlock_controllerNeverCallsKeycloakWhenTargetHasNoActiveIdentityLink() {
        PermissionEvaluatorService realEvaluator = evaluator();
        when(actionCatalogRepository.findById(ACTION_CODE)).thenReturn(Optional.of(action()));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment(INSTITUTION_ID)));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, ACTION_CODE))
                .thenReturn(true);
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, TARGET_USER_ID, IdentityLinkStatus.ACTIVE))
                .thenReturn(Optional.empty());

        UserPasswordResetUnlockController controller = new UserPasswordResetUnlockController(
                realEvaluator, tenantRepository, userIdentityLinkRepository, keycloakAdminClient, auditLogService);
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF, "Admin", false);
        when(authentication.getPrincipal()).thenReturn(principal);

        InstitutionScopeRequest request = new InstitutionScopeRequest(INSTITUTION_ID);

        assertThrows(ResourceNotFoundException.class, () ->
                controller.resetPassword(TENANT_ID, TARGET_USER_ID, request, authentication));

        verifyNoInteractions(keycloakAdminClient);
        verify(tenantRepository, never()).findById(any());
    }

    @Test
    void passwordReset_allowsAndRevokesSessionsAndAudits() {
        PermissionEvaluatorService realEvaluator = evaluator();
        when(actionCatalogRepository.findById(ACTION_CODE)).thenReturn(Optional.of(action()));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment(INSTITUTION_ID)));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, ACTION_CODE))
                .thenReturn(true);

        UserIdentityLink activeLink = UserIdentityLink.builder()
                .id(new UserIdentityLinkId(TENANT_ID, UUID.randomUUID()))
                .userAccountId(TARGET_USER_ID)
                .identityRealm("test-realm")
                .identitySubject("kc-target-subject")
                .usernameKind(UsernameKind.EMAIL)
                .status(IdentityLinkStatus.ACTIVE)
                .build();
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, TARGET_USER_ID, IdentityLinkStatus.ACTIVE))
                .thenReturn(Optional.of(activeLink));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenant.getKeycloakRealm()).thenReturn("test-realm");

        UserPasswordResetUnlockController controller = new UserPasswordResetUnlockController(
                realEvaluator, tenantRepository, userIdentityLinkRepository, keycloakAdminClient, auditLogService);
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF, "Admin", false);
        when(authentication.getPrincipal()).thenReturn(principal);

        InstitutionScopeRequest request = new InstitutionScopeRequest(INSTITUTION_ID);

        var response = controller.resetPassword(TENANT_ID, TARGET_USER_ID, request, authentication);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().reasonCode()).isEqualTo("ALLOWED");
        assertThat(response.getBody().temporaryPassword()).isNotBlank();

        verify(keycloakAdminClient).resetPassword(eq("test-realm"), eq("kc-target-subject"), any());
        verify(keycloakAdminClient).logoutUser("test-realm", "kc-target-subject");
        verify(auditLogService).record(eq(AuditEventType.USER_PASSWORD_RESET), any(), eq("kc-target-subject"), any(), any());
    }
}

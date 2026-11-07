package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserIdentityLink;
import com.edu360.api.identity.UserIdentityLinkId;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.identity.UsernameKind;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.dto.CreateUserCheckResponse;
import com.edu360.api.rbac.dto.InstitutionScopeRequest;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

/**
 * Covers the USER_DEACTIVATE story ("Deactivate a user", POST .../{id}/deactivation).
 * ALLOW/DENY/SCOPE exercise PermissionEvaluatorService.can(...) directly with USER_DEACTIVATE,
 * exactly like the USER_CREATE tests in PermissionEvaluatorServiceTest - always an
 * INSTITUTION-scoped check per the spec (sql/05_ref_data.sql rows 450-454). GUARD exercises
 * the Addendum rule 17 "no hard delete" requirement at the controller level: deactivation is
 * always a status flip via save(), never a UserAccountRepository.delete(...) call.
 */
@ExtendWith(MockitoExtension.class)
class UserDeactivationActionTest {

    @Mock private ActionCatalogRepository actionCatalogRepository;
    @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock private RoleActionRepository roleActionRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private UserIdentityLinkRepository userIdentityLinkRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private KeycloakAdminClient keycloakAdminClient;
    @Mock private AuditLogService auditLogService;
    @Mock private Authentication authentication;
    @Mock private Tenant tenant;

    private static final String USER_DEACTIVATE = "USER_DEACTIVATE";
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
        setField(a, "actionCode", USER_DEACTIVATE);
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
    void userDeactivate_allowsWhenTheActiveRoleInThatExactInstitutionScopeHoldsTheAction() {
        PermissionEvaluatorService evaluator = evaluator();
        when(actionCatalogRepository.findById(USER_DEACTIVATE)).thenReturn(Optional.of(action()));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment(INSTITUTION_ID)));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, USER_DEACTIVATE))
                .thenReturn(true);

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), USER_DEACTIVATE, RbacScope.institution(INSTITUTION_ID), RbacTarget.of(TARGET_USER_ID));

        assertThat(decision.allowed()).isTrue();
    }

    // -------------------------------------------------------------------------------- DENY

    @Test
    void userDeactivate_deniesWhenTheActiveRoleInThatExactInstitutionScopeDoesNotHoldTheAction() {
        PermissionEvaluatorService evaluator = evaluator();
        when(actionCatalogRepository.findById(USER_DEACTIVATE)).thenReturn(Optional.of(action()));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment(INSTITUTION_ID)));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, USER_DEACTIVATE))
                .thenReturn(false);

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), USER_DEACTIVATE, RbacScope.institution(INSTITUTION_ID), RbacTarget.of(TARGET_USER_ID));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("ACTION_NOT_GRANTED");
    }

    // ------------------------------------------------------------------------------- SCOPE

    @Test
    void userDeactivate_deniesWhenPrincipalsActiveRoleIsInADifferentInstitutionThanRequested() {
        PermissionEvaluatorService evaluator = evaluator();
        when(actionCatalogRepository.findById(USER_DEACTIVATE)).thenReturn(Optional.of(action()));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, OTHER_INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), USER_DEACTIVATE, RbacScope.institution(OTHER_INSTITUTION_ID), RbacTarget.of(TARGET_USER_ID));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("NO_ACTIVE_ROLE_IN_SCOPE");
    }

    // ------------------------------------------------------------------------------- GUARD

    @Test
    void userDeactivate_controllerNeverDeletesTheRowAndOnlyFlipsStatusViaSave() {
        PermissionEvaluatorService realEvaluator = evaluator();
        when(actionCatalogRepository.findById(USER_DEACTIVATE)).thenReturn(Optional.of(action()));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment(INSTITUTION_ID)));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, USER_DEACTIVATE))
                .thenReturn(true);

        UserAccount active = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, TARGET_USER_ID))
                .userType(UserType.STAFF)
                .displayName("Active Person")
                .email("active.person@example.edu")
                .status(UserAccountStatus.ACTIVE)
                .createdBy(USER_ACCOUNT_ID)
                .updatedBy(USER_ACCOUNT_ID)
                .build();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, TARGET_USER_ID)))
                .thenReturn(Optional.of(active));
        when(userAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Active role assignment to terminate
        UserRoleAssignment targetAssignment = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, UUID.randomUUID()))
                .userAccountId(TARGET_USER_ID)
                .roleDefinitionId(ROLE_DEFINITION_ID)
                .scopeType(ScopeType.INSTITUTION)
                .institutionId(INSTITUTION_ID)
                .effectiveFrom(Instant.now().minusSeconds(7200))
                .status(AssignmentStatus.ACTIVE)
                .build();
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, TARGET_USER_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(targetAssignment));

        // Keycloak identity link
        UserIdentityLink activeLink = UserIdentityLink.builder()
                .id(new UserIdentityLinkId(TENANT_ID, UUID.randomUUID()))
                .userAccountId(TARGET_USER_ID)
                .identityRealm("test-realm")
                .identitySubject("kc-target-deact-subject")
                .usernameKind(UsernameKind.EMAIL)
                .status(IdentityLinkStatus.ACTIVE)
                .build();
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, TARGET_USER_ID, IdentityLinkStatus.ACTIVE))
                .thenReturn(Optional.of(activeLink));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenant.getKeycloakRealm()).thenReturn("test-realm");

        UserDeactivationController controller = new UserDeactivationController(
                realEvaluator, userAccountRepository, userRoleAssignmentRepository,
                userIdentityLinkRepository, tenantRepository, keycloakAdminClient, auditLogService);
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF, "Admin", false);
        when(authentication.getPrincipal()).thenReturn(principal);

        ResponseEntity<CreateUserCheckResponse> response = controller.deactivateUser(
                TENANT_ID, TARGET_USER_ID, new InstitutionScopeRequest(INSTITUTION_ID), authentication);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().reasonCode()).isEqualTo("ALLOWED");

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(UserAccountStatus.DEACTIVATED);
        assertThat(captor.getValue().getDeactivatedAt()).isNotNull();
        assertThat(captor.getValue().getDeactivatedBy()).isEqualTo(USER_ACCOUNT_ID);

        // Active assignments terminated
        ArgumentCaptor<UserRoleAssignment> assignCaptor = ArgumentCaptor.forClass(UserRoleAssignment.class);
        verify(userRoleAssignmentRepository).save(assignCaptor.capture());
        assertThat(assignCaptor.getValue().getStatus()).isEqualTo(AssignmentStatus.ENDED);
        assertThat(assignCaptor.getValue().getEffectiveTo()).isNotNull();
        assertThat(assignCaptor.getValue().getUpdatedBy()).isEqualTo(USER_ACCOUNT_ID);

        // Keycloak user disabled and sessions revoked (WF-27)
        verify(keycloakAdminClient).setUserEnabled("test-realm", "kc-target-deact-subject", false);
        verify(keycloakAdminClient).logoutUser("test-realm", "kc-target-deact-subject");

        // Immutable audit log recorded
        verify(auditLogService).record(eq(AuditEventType.USER_DEACTIVATED), any(), eq("active.person@example.edu"), any(), any());

        // Addendum rule 17 - no hard delete, ever, even though UserAccountRepository (a plain
        // JpaRepository) still technically exposes a delete method.
        verify(userAccountRepository, never()).delete(any());
        verify(userAccountRepository, never()).deleteById(any());
    }

    @Test
    void userDeactivate_rejectsSelfDeactivationWithBusinessRuleViolationException() {
        PermissionEvaluatorService realEvaluator = evaluator();
        when(actionCatalogRepository.findById(USER_DEACTIVATE)).thenReturn(Optional.of(action()));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment(INSTITUTION_ID)));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, USER_DEACTIVATE))
                .thenReturn(true);

        UserDeactivationController controller = new UserDeactivationController(
                realEvaluator, userAccountRepository, userRoleAssignmentRepository,
                userIdentityLinkRepository, tenantRepository, keycloakAdminClient, auditLogService);
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF, "Admin", false);
        when(authentication.getPrincipal()).thenReturn(principal);

        InstitutionScopeRequest request = new InstitutionScopeRequest(INSTITUTION_ID);

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                controller.deactivateUser(TENANT_ID, USER_ACCOUNT_ID, request, authentication));

        assertThat(ex.getErrorCode()).isEqualTo("CANNOT_DEACTIVATE_SELF");
        verify(userAccountRepository, never()).save(any());
        verify(userAccountRepository, never()).delete(any());
    }
}

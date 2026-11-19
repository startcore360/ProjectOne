package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.dto.UserListResponse;

/**
 * Covers the USER_VIEW story ("See user accounts", GET /api/tenants/{tenantId}/users and
 * GET .../{id}) - same ALLOW/DENY/SCOPE/GUARD breakdown as PermissionEvaluatorServiceTest,
 * applied to this new action code. ALLOW/DENY/SCOPE exercise PermissionEvaluatorService.can(...)
 * directly, exactly like the USER_CREATE tests in that file (no parallel authorization
 * mechanism is introduced here - see UserViewController, which calls the exact same
 * evaluator.authorize(...)). GUARD exercises UserViewController's own tenant-isolation re-check
 * (Addendum rule 13), which runs BEFORE the evaluator is ever consulted.
 */
@ExtendWith(MockitoExtension.class)
class UserViewActionTest {

    @Mock private ActionCatalogRepository actionCatalogRepository;
    @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock private RoleActionRepository roleActionRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private Authentication authentication;

    private static final String USER_VIEW = "USER_VIEW";
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID ROLE_DEFINITION_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();
    private static final UUID OTHER_INSTITUTION_ID = UUID.randomUUID();

    private PermissionEvaluatorService evaluator() {
        return new PermissionEvaluatorService(actionCatalogRepository, userRoleAssignmentRepository, roleActionRepository);
    }

    private ActionCatalog action(boolean isApproval) {
        ActionCatalog a = new ActionCatalog();
        setField(a, "actionCode", USER_VIEW);
        setField(a, "approvalAction", isApproval);
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
    void userView_allowsWhenTheActiveRoleInThatExactScopeHoldsTheAction() {
        PermissionEvaluatorService evaluator = evaluator();
        when(actionCatalogRepository.findById(USER_VIEW)).thenReturn(Optional.of(action(false)));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment(INSTITUTION_ID)));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, USER_VIEW))
                .thenReturn(true);

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), USER_VIEW, RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);

        assertThat(decision.allowed()).isTrue();
    }

    // -------------------------------------------------------------------------------- DENY

    @Test
    void userView_deniesWhenTheActiveRoleInThatExactScopeDoesNotHoldTheAction() {
        PermissionEvaluatorService evaluator = evaluator();
        when(actionCatalogRepository.findById(USER_VIEW)).thenReturn(Optional.of(action(false)));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment(INSTITUTION_ID)));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, USER_VIEW))
                .thenReturn(false);

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), USER_VIEW, RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("ACTION_NOT_GRANTED");
    }

    // ------------------------------------------------------------------------------- SCOPE

    @Test
    void userView_deniesWhenPrincipalsActiveRoleIsInADifferentScopeThanRequested() {
        PermissionEvaluatorService evaluator = evaluator();
        when(actionCatalogRepository.findById(USER_VIEW)).thenReturn(Optional.of(action(false)));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, OTHER_INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), USER_VIEW, RbacScope.institution(OTHER_INSTITUTION_ID), RbacTarget.NONE);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("NO_ACTIVE_ROLE_IN_SCOPE");
    }

    // ------------------------------------------------------------------------------- GUARD

    @Test
    void userView_controllerDeniesBeforeCallingTheEvaluatorWhenPathTenantIdDoesNotMatchThePrincipal() {
        PermissionEvaluatorService mockEvaluator = new PermissionEvaluatorService(
                actionCatalogRepository, userRoleAssignmentRepository, roleActionRepository);
        // Standing in for @Mock PermissionEvaluatorService would also be valid; using the real
        // type with no repository stubs makes the "never consulted" assertion below fail loudly
        // (UnfinishedStubbing) if the GUARD did not short-circuit, same discipline the existing
        // self-approval GUARD test in PermissionEvaluatorServiceTest uses.
        UserViewController controller = new UserViewController(mockEvaluator, userAccountRepository);

        TenantPrincipal principal = new TenantPrincipal(OTHER_TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF, "Someone", false);
        when(authentication.getPrincipal()).thenReturn(principal);

        assertThrows(UnauthorizedException.class, () ->
                controller.listUsers(TENANT_ID, ScopeType.TENANT, null, null, authentication));

        verifyNoInteractions(userAccountRepository);
    }

    // ------------------------------------------------------------------------------- PERF-02a (DATABASE QUERIES)

    private UserAccount sampleUser(UUID tenantId, UUID userAccountId, String name) {
        return UserAccount.builder()
                .id(new UserAccountId(tenantId, userAccountId))
                .userType(UserType.STAFF)
                .displayName(name)
                .email(name.toLowerCase() + "@example.com")
                .status(UserAccountStatus.ACTIVE)
                .build();
    }

    @Test
    void userView_tenantScope_queriesByTenantIdAndNeverCallsFindAll() {
        PermissionEvaluatorService mockEvaluator = org.mockito.Mockito.mock(PermissionEvaluatorService.class);
        UserViewController controller = new UserViewController(mockEvaluator, userAccountRepository);

        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF, "Principal", false);
        when(authentication.getPrincipal()).thenReturn(principal);

        UserAccount user1 = sampleUser(TENANT_ID, UUID.randomUUID(), "Alice");
        UserAccount user2 = sampleUser(TENANT_ID, UUID.randomUUID(), "Bob");
        when(userAccountRepository.findByIdTenantId(TENANT_ID)).thenReturn(List.of(user1, user2));

        ResponseEntity<UserListResponse> response = controller.listUsers(TENANT_ID, ScopeType.TENANT, null, null, authentication);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().users()).hasSize(2);
        assertThat(response.getBody().users().get(0).displayName()).isEqualTo("Alice");
        assertThat(response.getBody().users().get(1).displayName()).isEqualTo("Bob");

        verify(userAccountRepository).findByIdTenantId(TENANT_ID);
        verify(userAccountRepository, never()).findAll();
    }

    @Test
    void userView_institutionScope_queriesByTenantAndInstitutionWithActiveStatusAndNeverCallsFindAll() {
        PermissionEvaluatorService mockEvaluator = org.mockito.Mockito.mock(PermissionEvaluatorService.class);
        UserViewController controller = new UserViewController(mockEvaluator, userAccountRepository);

        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF, "Principal", false);
        when(authentication.getPrincipal()).thenReturn(principal);

        UserAccount user = sampleUser(TENANT_ID, UUID.randomUUID(), "College Faculty");
        when(userAccountRepository.findByTenantIdAndInstitutionIdAndAssignmentStatus(TENANT_ID, INSTITUTION_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(user));

        ResponseEntity<UserListResponse> response = controller.listUsers(TENANT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, authentication);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().users()).hasSize(1);
        assertThat(response.getBody().users().get(0).displayName()).isEqualTo("College Faculty");

        verify(userAccountRepository).findByTenantIdAndInstitutionIdAndAssignmentStatus(TENANT_ID, INSTITUTION_ID, AssignmentStatus.ACTIVE);
        verify(userAccountRepository, never()).findAll();
    }

    @Test
    void userView_institutionScope_emptyWhenNoActiveAssignmentsInInstitution() {
        PermissionEvaluatorService mockEvaluator = org.mockito.Mockito.mock(PermissionEvaluatorService.class);
        UserViewController controller = new UserViewController(mockEvaluator, userAccountRepository);

        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF, "Principal", false);
        when(authentication.getPrincipal()).thenReturn(principal);

        when(userAccountRepository.findByTenantIdAndInstitutionIdAndAssignmentStatus(TENANT_ID, INSTITUTION_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        ResponseEntity<UserListResponse> response = controller.listUsers(TENANT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, authentication);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().users()).isEmpty();

        verify(userAccountRepository).findByTenantIdAndInstitutionIdAndAssignmentStatus(TENANT_ID, INSTITUTION_ID, AssignmentStatus.ACTIVE);
        verify(userAccountRepository, never()).findAll();
    }
}

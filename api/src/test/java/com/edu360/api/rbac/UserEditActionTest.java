package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.dto.UpdateUserRequest;

/**
 * Covers the USER_EDIT story ("Edit a user account", PATCH /api/tenants/{tenantId}/users/{id}).
 * ALLOW/DENY/SCOPE exercise PermissionEvaluatorService.can(...) directly with USER_EDIT, exactly
 * like the USER_CREATE tests in PermissionEvaluatorServiceTest. GUARD exercises
 * UserEditController's own business rule that a DEACTIVATED account can never be edited (no
 * reactivate - Addendum rule 17 / UserAccountStatus's own javadoc).
 */
@ExtendWith(MockitoExtension.class)
class UserEditActionTest {

    @Mock private ActionCatalogRepository actionCatalogRepository;
    @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock private RoleActionRepository roleActionRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private Authentication authentication;

    private static final String USER_EDIT = "USER_EDIT";
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
        setField(a, "actionCode", USER_EDIT);
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
    void userEdit_allowsWhenTheActiveRoleInThatExactScopeHoldsTheAction() {
        PermissionEvaluatorService evaluator = evaluator();
        when(actionCatalogRepository.findById(USER_EDIT)).thenReturn(Optional.of(action()));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment(INSTITUTION_ID)));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, USER_EDIT))
                .thenReturn(true);

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), USER_EDIT, RbacScope.institution(INSTITUTION_ID), RbacTarget.of(TARGET_USER_ID));

        assertThat(decision.allowed()).isTrue();
    }

    // -------------------------------------------------------------------------------- DENY

    @Test
    void userEdit_deniesWhenTheActiveRoleInThatExactScopeDoesNotHoldTheAction() {
        PermissionEvaluatorService evaluator = evaluator();
        when(actionCatalogRepository.findById(USER_EDIT)).thenReturn(Optional.of(action()));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment(INSTITUTION_ID)));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, USER_EDIT))
                .thenReturn(false);

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), USER_EDIT, RbacScope.institution(INSTITUTION_ID), RbacTarget.of(TARGET_USER_ID));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("ACTION_NOT_GRANTED");
    }

    // ------------------------------------------------------------------------------- SCOPE

    @Test
    void userEdit_deniesWhenPrincipalsActiveRoleIsInADifferentScopeThanRequested() {
        PermissionEvaluatorService evaluator = evaluator();
        when(actionCatalogRepository.findById(USER_EDIT)).thenReturn(Optional.of(action()));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, OTHER_INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), USER_EDIT, RbacScope.institution(OTHER_INSTITUTION_ID), RbacTarget.of(TARGET_USER_ID));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("NO_ACTIVE_ROLE_IN_SCOPE");
    }

    // ------------------------------------------------------------------------------- GUARD

    @Test
    void userEdit_controllerRefusesToEditADeactivatedAccountEvenWhenTheRoleGrantsUserEdit() {
        PermissionEvaluatorService realEvaluator = evaluator();
        when(actionCatalogRepository.findById(USER_EDIT)).thenReturn(Optional.of(action()));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment(INSTITUTION_ID)));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, USER_EDIT))
                .thenReturn(true);

        UserAccount deactivated = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, TARGET_USER_ID))
                .userType(UserType.STAFF)
                .displayName("Deactivated Person")
                .status(UserAccountStatus.DEACTIVATED)
                .createdBy(USER_ACCOUNT_ID)
                .updatedBy(USER_ACCOUNT_ID)
                .build();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, TARGET_USER_ID)))
                .thenReturn(Optional.of(deactivated));

        UserEditController controller = new UserEditController(realEvaluator, userAccountRepository);
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF, "Admin", false);
        when(authentication.getPrincipal()).thenReturn(principal);

        UpdateUserRequest request = new UpdateUserRequest(ScopeType.INSTITUTION, INSTITUTION_ID, null, "New Name", null, null);

        assertThrows(UnauthorizedException.class, () ->
                controller.updateUser(TENANT_ID, TARGET_USER_ID, request, authentication));

        verify(userAccountRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}

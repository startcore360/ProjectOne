package com.edu360.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleAction;
import com.edu360.api.rbac.RoleActionRepository;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionId;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.RoleDefinitionStatus;
import com.edu360.api.rbac.RoleLevel;
import com.edu360.api.rbac.RoleOrigin;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.StandardRole;
import com.edu360.api.rbac.StandardRoleAction;
import com.edu360.api.rbac.StandardRoleActionRepository;
import com.edu360.api.rbac.StandardRoleRepository;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

/**
 * Unit tests for DevRoleAssignmentSeeder, mirroring AuthServiceTest's style: every collaborator
 * mocked out, no Spring context. DevAdminSeeder itself has no test file to match (it uses raw
 * JDBC it would be awkward to mock), so this follows the general Mockito-mocked-repositories
 * convention this codebase already uses everywhere else a seeder-adjacent class is unit tested
 * (AuthServiceTest, UserPasswordResetUnlockActionTest).
 *
 * Covers: (1) a tenant-less dev DB is a true no-op, (2) a tenant with no ACTIVE user_account is a
 * no-op for that tenant, (3) a tenant with exactly one ACTIVE account gets a COLLEGE_ADMIN
 * RoleDefinition + its template RoleActions + one TENANT-scope ACTIVE UserRoleAssignment, and
 * (4) re-running against an already-seeded tenant is idempotent (no duplicate inserts).
 */
@ExtendWith(MockitoExtension.class)
class DevRoleAssignmentSeederTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private RoleDefinitionRepository roleDefinitionRepository;
    @Mock private RoleActionRepository roleActionRepository;
    @Mock private StandardRoleRepository standardRoleRepository;
    @Mock private StandardRoleActionRepository standardRoleActionRepository;
    @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ACCOUNT_ID = UUID.randomUUID();

    private DevRoleAssignmentSeeder seeder() {
        return new DevRoleAssignmentSeeder(tenantRepository, userAccountRepository, roleDefinitionRepository,
                roleActionRepository, standardRoleRepository, standardRoleActionRepository, userRoleAssignmentRepository);
    }

    private Tenant tenant() {
        Tenant tenant = org.mockito.Mockito.mock(Tenant.class);
        org.mockito.Mockito.lenient().when(tenant.getTenantId()).thenReturn(TENANT_ID);
        org.mockito.Mockito.lenient().when(tenant.getIsolationMode()).thenReturn(IsolationMode.SHARED);
        org.mockito.Mockito.lenient().when(tenant.getState()).thenReturn(TenantState.ACTIVE);
        return tenant;
    }

    private UserAccount activeAccount() {
        return UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ACCOUNT_ID))
                .userType(UserType.STAFF)
                .displayName("Test User")
                .status(UserAccountStatus.ACTIVE)
                .build();
    }

    @Test
    void run_noTenants_isANoOp() {
        when(tenantRepository.findAll()).thenReturn(List.of());

        seeder().run();

        verify(userAccountRepository, never()).findByIdTenantIdAndStatus(any(), any());
        verify(roleDefinitionRepository, never()).save(any());
        verify(userRoleAssignmentRepository, never()).save(any());
    }

    @Test
    void run_tenantWithNoActiveAccounts_isANoOpForThatTenant() {
        Tenant tenant = tenant();
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(userAccountRepository.findByIdTenantIdAndStatus(TENANT_ID, UserAccountStatus.ACTIVE))
                .thenReturn(List.of());

        seeder().run();

        verify(roleDefinitionRepository, never()).save(any());
        verify(userRoleAssignmentRepository, never()).save(any());
    }

    @Test
    void run_tenantWithOneActiveAccount_seedsRoleDefinitionRoleActionsAndAssignment() {
        Tenant tenant = tenant();
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(userAccountRepository.findByIdTenantIdAndStatus(TENANT_ID, UserAccountStatus.ACTIVE))
                .thenReturn(List.of(activeAccount()));
        when(roleDefinitionRepository.findByIdTenantId(TENANT_ID)).thenReturn(List.of());
        when(roleDefinitionRepository.save(any(RoleDefinition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(standardRoleRepository.findById("COLLEGE_ADMIN")).thenReturn(Optional.empty());

        StandardRoleAction templateAction = standardRoleAction("USER_PASSWORD_RESET_UNLOCK");
        when(standardRoleActionRepository.findByRoleCode("COLLEGE_ADMIN")).thenReturn(List.of(templateAction));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(eq(TENANT_ID), any(), eq("USER_PASSWORD_RESET_UNLOCK")))
                .thenReturn(false);
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.TENANT, null, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        seeder().run();

        ArgumentCaptor<RoleDefinition> roleDefinitionCaptor = ArgumentCaptor.forClass(RoleDefinition.class);
        verify(roleDefinitionRepository).save(roleDefinitionCaptor.capture());
        RoleDefinition savedRoleDefinition = roleDefinitionCaptor.getValue();
        assertThat(savedRoleDefinition.getRoleOrigin()).isEqualTo(RoleOrigin.STANDARD);
        assertThat(savedRoleDefinition.getBaseStandardRole()).isEqualTo("COLLEGE_ADMIN");
        assertThat(savedRoleDefinition.getRoleLevel()).isEqualTo(RoleLevel.COLLEGE);
        assertThat(savedRoleDefinition.getStatus()).isEqualTo(RoleDefinitionStatus.ACTIVE);
        assertThat(savedRoleDefinition.getId().getTenantId()).isEqualTo(TENANT_ID);

        ArgumentCaptor<RoleAction> roleActionCaptor = ArgumentCaptor.forClass(RoleAction.class);
        verify(roleActionRepository).save(roleActionCaptor.capture());
        assertThat(roleActionCaptor.getValue().getActionCode()).isEqualTo("USER_PASSWORD_RESET_UNLOCK");

        ArgumentCaptor<UserRoleAssignment> assignmentCaptor = ArgumentCaptor.forClass(UserRoleAssignment.class);
        verify(userRoleAssignmentRepository).save(assignmentCaptor.capture());
        UserRoleAssignment savedAssignment = assignmentCaptor.getValue();
        assertThat(savedAssignment.getUserAccountId()).isEqualTo(USER_ACCOUNT_ID);
        assertThat(savedAssignment.getScopeType()).isEqualTo(ScopeType.TENANT);
        assertThat(savedAssignment.getInstitutionId()).isNull();
        assertThat(savedAssignment.getDepartmentId()).isNull();
        assertThat(savedAssignment.getStatus()).isEqualTo(AssignmentStatus.ACTIVE);
    }

    @Test
    void run_namedTestAccount_isSeededWithItsMappedRoleCodeNotTheDefault() {
        UUID namedUserAccountId = UUID.randomUUID();
        UserAccount namedAccount = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, namedUserAccountId))
                .userType(UserType.STAFF)
                .displayName("ABC Teacher One")
                .status(UserAccountStatus.ACTIVE)
                .build();

        Tenant tenant = tenant();
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(userAccountRepository.findByIdTenantIdAndStatus(TENANT_ID, UserAccountStatus.ACTIVE))
                .thenReturn(List.of(namedAccount));
        when(roleDefinitionRepository.findByIdTenantId(TENANT_ID)).thenReturn(List.of());
        when(roleDefinitionRepository.save(any(RoleDefinition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(standardRoleRepository.findById("CLASS_TEACHER")).thenReturn(Optional.empty());
        when(standardRoleActionRepository.findByRoleCode("CLASS_TEACHER")).thenReturn(List.of());
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, namedUserAccountId, ScopeType.TENANT, null, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        seeder().run();

        ArgumentCaptor<RoleDefinition> roleDefinitionCaptor = ArgumentCaptor.forClass(RoleDefinition.class);
        verify(roleDefinitionRepository).save(roleDefinitionCaptor.capture());
        assertThat(roleDefinitionCaptor.getValue().getBaseStandardRole()).isEqualTo("CLASS_TEACHER");

        ArgumentCaptor<UserRoleAssignment> assignmentCaptor = ArgumentCaptor.forClass(UserRoleAssignment.class);
        verify(userRoleAssignmentRepository).save(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue().getUserAccountId()).isEqualTo(namedUserAccountId);
    }

    @Test
    void run_tenantWithMultipleActiveAccounts_seedsEveryAccountNotJustTheFirst() {
        UUID secondUserAccountId = UUID.randomUUID();
        UserAccount second = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, secondUserAccountId))
                .userType(UserType.STAFF)
                .displayName("Second Test User")
                .status(UserAccountStatus.ACTIVE)
                .build();

        Tenant tenant = tenant();
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(userAccountRepository.findByIdTenantIdAndStatus(TENANT_ID, UserAccountStatus.ACTIVE))
                .thenReturn(List.of(activeAccount(), second));
        when(roleDefinitionRepository.findByIdTenantId(TENANT_ID)).thenReturn(List.of());
        when(roleDefinitionRepository.save(any(RoleDefinition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(standardRoleRepository.findById("COLLEGE_ADMIN")).thenReturn(Optional.empty());
        when(standardRoleActionRepository.findByRoleCode("COLLEGE_ADMIN")).thenReturn(List.of());
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                eq(TENANT_ID), any(), eq(ScopeType.TENANT), eq(null), eq(null), eq(AssignmentStatus.ACTIVE)))
                .thenReturn(List.of());

        seeder().run();

        verify(userRoleAssignmentRepository, org.mockito.Mockito.times(2)).save(any(UserRoleAssignment.class));
    }

    @Test
    void run_tenantAlreadySeeded_isIdempotent() {
        UUID existingRoleDefinitionId = UUID.randomUUID();
        RoleDefinition existingRoleDefinition = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, existingRoleDefinitionId))
                .roleCode("COLLEGE_ADMIN")
                .roleName("College Admin")
                .roleOrigin(RoleOrigin.STANDARD)
                .roleLevel(RoleLevel.COLLEGE)
                .baseStandardRole("COLLEGE_ADMIN")
                .sensitiveRole(false)
                .status(RoleDefinitionStatus.ACTIVE)
                .createdBy(USER_ACCOUNT_ID)
                .updatedBy(USER_ACCOUNT_ID)
                .build();

        Tenant tenant = tenant();
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(userAccountRepository.findByIdTenantIdAndStatus(TENANT_ID, UserAccountStatus.ACTIVE))
                .thenReturn(List.of(activeAccount()));
        when(roleDefinitionRepository.findByIdTenantId(TENANT_ID)).thenReturn(List.of(existingRoleDefinition));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.TENANT, null, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(org.mockito.Mockito.mock(UserRoleAssignment.class)));

        seeder().run();

        verify(roleDefinitionRepository, never()).save(any());
        verify(userRoleAssignmentRepository, never()).save(any());
    }

    private StandardRoleAction standardRoleAction(String actionCode) {
        StandardRoleAction action = new StandardRoleAction();
        setField(action, "standardRoleActionId", 1);
        setField(action, "roleCode", "COLLEGE_ADMIN");
        setField(action, "actionCode", actionCode);
        return action;
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
}

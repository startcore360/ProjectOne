package com.edu360.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditFailureException;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionId;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentId;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.selfservice.UserActiveRole;
import com.edu360.api.selfservice.UserActiveRoleRepository;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class TenantUserLookupTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID LINK_ID = UUID.randomUUID();
    private static final UUID ROLE_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_A = UUID.randomUUID();
    private static final UUID INSTITUTION_B = UUID.randomUUID();
    private static final UUID ASSIGNMENT_A = UUID.randomUUID();
    private static final UUID ASSIGNMENT_B = UUID.randomUUID();

    @Mock private UserIdentityLinkRepository identityLinks;
    @Mock private UserAccountRepository accounts;
    @Mock private UserRoleAssignmentRepository assignments;
    @Mock private RoleDefinitionRepository roles;
    @Mock private UserActiveRoleRepository activeRoles;
    @Mock private AuditLogService auditLogService;

    private final TransactionTemplate transactionTemplate = new TransactionTemplate(new AbstractPlatformTransactionManager() {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition def) {}
        @Override protected void doCommit(DefaultTransactionStatus st) {}
        @Override protected void doRollback(DefaultTransactionStatus st) {}
    });

    private Tenant tenant;
    private UserAccount account;
    private UserIdentityLink link;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().tenantId(TENANT_ID).isolationMode(IsolationMode.SHARED).build();
        account = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ID))
                .userType(UserType.STAFF).displayName("Test user").email("test@example.com").status(UserAccountStatus.ACTIVE)
                .build();
        link = UserIdentityLink.builder()
                .id(new UserIdentityLinkId(TENANT_ID, LINK_ID))
                .userAccountId(USER_ID).identityRealm("tenant-realm").identitySubject("subject")
                .usernameKind(UsernameKind.EMAIL).status(IdentityLinkStatus.ACTIVE)
                .build();
        when(identityLinks.findByIdTenantIdAndIdentityRealmAndIdentitySubjectAndStatus(
                TENANT_ID, "tenant-realm", "subject", IdentityLinkStatus.ACTIVE)).thenReturn(Optional.of(link));
        when(accounts.findById(new UserAccountId(TENANT_ID, USER_ID))).thenReturn(Optional.of(account));
        RoleDefinition role = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_ID)).roleCode("SCHOOL_ADMIN").build();
        lenient().when(roles.findById(new RoleDefinitionId(TENANT_ID, ROLE_ID))).thenReturn(Optional.of(role));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private TenantUserLookup lookup() {
        return new TenantUserLookup(identityLinks, accounts, assignments, roles, activeRoles, auditLogService, transactionTemplate);
    }

    private UserRoleAssignment assignment(UUID assignmentId, ScopeType scope, UUID institutionId) {
        return UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, assignmentId))
                .userAccountId(USER_ID).roleDefinitionId(ROLE_ID).scopeType(scope)
                .institutionId(institutionId).effectiveFrom(Instant.now().minusSeconds(60))
                .status(AssignmentStatus.ACTIVE)
                .build();
    }

    private void select(UUID assignmentId) {
        when(activeRoles.findByIdTenantIdAndIdUserAccountId(TENANT_ID, USER_ID))
                .thenReturn(Optional.of(UserActiveRole.builder()
                        .userRoleAssignmentId(assignmentId).build()));
    }

    @Test
    void activeRoleScopeContainsOnlyTheSelectedAssignmentInstitution() {
        when(assignments.findByIdTenantIdAndUserAccountIdAndStatus(TENANT_ID, USER_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(
                        assignment(ASSIGNMENT_A, ScopeType.INSTITUTION, INSTITUTION_A),
                        assignment(ASSIGNMENT_B, ScopeType.INSTITUTION, INSTITUTION_B)));
        select(ASSIGNMENT_A);

        lookup().resolveActiveUser(tenant, "tenant-realm", "subject");

        assertThat(TenantContext.currentInstitutionScope()).isEqualTo("LIST");
        assertThat(TenantContext.currentInstitutionIds()).containsExactly(INSTITUTION_A);
        assertThat(TenantContext.currentActiveAssignmentId()).isEqualTo(ASSIGNMENT_A);
        assertThat(TenantContext.isActiveRoleRequired()).isFalse();
    }

    @Test
    void switchingActiveRoleChangesTheInstitutionScope() {
        when(assignments.findByIdTenantIdAndUserAccountIdAndStatus(TENANT_ID, USER_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(
                        assignment(ASSIGNMENT_A, ScopeType.INSTITUTION, INSTITUTION_A),
                        assignment(ASSIGNMENT_B, ScopeType.INSTITUTION, INSTITUTION_B)));
        select(ASSIGNMENT_B);

        lookup().resolveActiveUser(tenant, "tenant-realm", "subject");

        assertThat(TenantContext.currentInstitutionIds()).containsExactly(INSTITUTION_B);
        assertThat(TenantContext.currentActiveAssignmentId()).isEqualTo(ASSIGNMENT_B);
    }

    @Test
    void tenantWideActiveRoleGetsAllInstitutionScope() {
        when(assignments.findByIdTenantIdAndUserAccountIdAndStatus(TENANT_ID, USER_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(assignment(UUID.randomUUID(), ScopeType.TENANT, null)));

        lookup().resolveActiveUser(tenant, "tenant-realm", "subject");

        assertThat(TenantContext.currentInstitutionScope()).isEqualTo("ALL");
        assertThat(TenantContext.currentInstitutionIds()).isEmpty();
    }

    @Test
    void userWithoutAnActiveAssignmentGetsNoInstitutionScope() {
        when(assignments.findByIdTenantIdAndUserAccountIdAndStatus(TENANT_ID, USER_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        lookup().resolveActiveUser(tenant, "tenant-realm", "subject");

        assertThat(TenantContext.currentInstitutionScope()).isEqualTo("NONE");
        assertThat(TenantContext.currentInstitutionIds()).isEmpty();
        assertThat(TenantContext.currentActiveAssignmentId()).isNull();
        assertThat(TenantContext.isActiveRoleRequired()).isFalse();
    }

    @Test
    void activationWritesOneAuditRow() {
        account.setStatus(UserAccountStatus.PENDING_ACTIVATION);
        when(accounts.updateStatusIfMatches(TENANT_ID, USER_ID, UserAccountStatus.ACTIVE, UserAccountStatus.PENDING_ACTIVATION))
                .thenReturn(1);

        TenantUserLookup.ResolvedUser resolved = lookup().resolveActiveUser(tenant, "tenant-realm", "subject");

        assertThat(resolved.account().getStatus()).isEqualTo(UserAccountStatus.ACTIVE);
        verify(accounts).updateStatusIfMatches(TENANT_ID, USER_ID, UserAccountStatus.ACTIVE, UserAccountStatus.PENDING_ACTIVATION);
        verify(auditLogService).record(
                eq(AuditEventType.USER_ACTIVATED),
                isNull(),
                eq("test@example.com"),
                isNull(),
                contains("activated on first login")
        );
    }

    @Test
    void auditFailureLeavesStatusPendingActivationAndFailsRequest() {
        account.setStatus(UserAccountStatus.PENDING_ACTIVATION);
        when(accounts.updateStatusIfMatches(TENANT_ID, USER_ID, UserAccountStatus.ACTIVE, UserAccountStatus.PENDING_ACTIVATION))
                .thenReturn(1);
        doThrow(new AuditFailureException("Audit sink down", new RuntimeException()))
                .when(auditLogService).record(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> lookup().resolveActiveUser(tenant, "tenant-realm", "subject"))
                .isInstanceOf(AuditFailureException.class)
                .hasMessageContaining("Audit sink down");

        assertThat(account.getStatus()).isEqualTo(UserAccountStatus.PENDING_ACTIVATION);
    }

    @Test
    void alreadyActiveUserWritesNoRow() {
        assertThat(account.getStatus()).isEqualTo(UserAccountStatus.ACTIVE);

        TenantUserLookup.ResolvedUser resolved = lookup().resolveActiveUser(tenant, "tenant-realm", "subject");

        assertThat(resolved.account().getStatus()).isEqualTo(UserAccountStatus.ACTIVE);
        verify(accounts, never()).updateStatusIfMatches(any(), any(), any(), any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }
}

package com.edu360.api.platformops.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionId;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentId;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.selfservice.UserNotification;
import com.edu360.api.selfservice.UserNotificationRepository;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

class PlatformReleaseNotificationServiceTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final RoleDefinitionRepository roles = mock(RoleDefinitionRepository.class);
    private final UserRoleAssignmentRepository assignments = mock(UserRoleAssignmentRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final UserNotificationRepository notifications = mock(UserNotificationRepository.class);

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void notifiesActiveTenantAdminsWithAllScopeAndVersionUserDedupe() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, IsolationMode.SHARED);
        RoleDefinition role = RoleDefinition.builder()
                .id(new RoleDefinitionId(tenantId, roleId)).roleCode("SCHOOL_ADMIN").build();
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(tenantId, UUID.randomUUID()))
                .userAccountId(userId).roleDefinitionId(roleId).institutionId(institutionId)
                .status(AssignmentStatus.ACTIVE).build();
        UserAccount account = UserAccount.builder()
                .id(new UserAccountId(tenantId, userId)).status(UserAccountStatus.ACTIVE).build();
        PlatformRelease release = release("v4.2.0", "Important release notes");

        when(tenants.findAll()).thenReturn(List.of(tenant));
        when(roles.findByIdTenantIdAndRoleCode(tenantId, "SCHOOL_ADMIN")).thenAnswer(inv -> {
            assertThat(TenantContext.currentTenantId()).isEqualTo(tenantId);
            assertThat(TenantContext.currentInstitutionScope()).isEqualTo("ALL");
            return Optional.of(role);
        });
        when(assignments.findByIdTenantIdAndRoleDefinitionIdAndStatus(
                tenantId, roleId, AssignmentStatus.ACTIVE)).thenReturn(List.of(assignment));
        when(accounts.findById(new UserAccountId(tenantId, userId))).thenReturn(Optional.of(account));
        when(notifications.findFirstByIdTenantIdAndDedupeKey(tenantId, "v4.2.0_" + userId))
                .thenReturn(Optional.empty());

        service().notifyTenantAdmins(release);

        ArgumentCaptor<UserNotification> saved = ArgumentCaptor.forClass(UserNotification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getTriggerCode()).isEqualTo("RELEASE_DEPLOYED");
        assertThat(saved.getValue().getTitle()).isEqualTo("EDU 360 v4.2.0 deployed");
        assertThat(saved.getValue().getBody()).isEqualTo("Important release notes");
        assertThat(saved.getValue().getDedupeKey()).isEqualTo("v4.2.0_" + userId);
        assertThat(saved.getValue().getCreatedByUserId()).isNull();
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void skipsIsolatedTenantsAndContinuesAfterOneTenantFails() {
        UUID isolatedId = UUID.randomUUID();
        UUID failingId = UUID.randomUUID();
        UUID healthyId = UUID.randomUUID();
        Tenant isolated = tenant(isolatedId, IsolationMode.ISOLATED);
        Tenant failing = tenant(failingId, IsolationMode.SHARED);
        Tenant healthy = tenant(healthyId, IsolationMode.SHARED);
        when(tenants.findAll()).thenReturn(List.of(isolated, failing, healthy));

        when(roles.findByIdTenantIdAndRoleCode(failingId, "SCHOOL_ADMIN"))
                .thenThrow(new IllegalStateException("tenant database unavailable"));
        when(roles.findByIdTenantIdAndRoleCode(healthyId, "SCHOOL_ADMIN"))
                .thenReturn(Optional.empty());

        service().notifyTenantAdmins(release("v4.2.1", "Notes"));

        verify(roles, never()).findByIdTenantIdAndRoleCode(isolatedId, "SCHOOL_ADMIN");
        verify(roles, times(2)).findByIdTenantIdAndRoleCode(failingId, "SCHOOL_ADMIN");
        verify(roles).findByIdTenantIdAndRoleCode(healthyId, "SCHOOL_ADMIN");
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void existingVersionAndUserNotificationIsNotInsertedAgain() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, IsolationMode.SHARED);
        RoleDefinition role = RoleDefinition.builder()
                .id(new RoleDefinitionId(tenantId, roleId)).roleCode("SCHOOL_ADMIN").build();
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(tenantId, UUID.randomUUID()))
                .userAccountId(userId).roleDefinitionId(roleId).status(AssignmentStatus.ACTIVE).build();
        UserAccount account = UserAccount.builder()
                .id(new UserAccountId(tenantId, userId)).status(UserAccountStatus.ACTIVE).build();
        when(tenants.findAll()).thenReturn(List.of(tenant));
        when(roles.findByIdTenantIdAndRoleCode(tenantId, "SCHOOL_ADMIN")).thenReturn(Optional.of(role));
        when(assignments.findByIdTenantIdAndRoleDefinitionIdAndStatus(
                tenantId, roleId, AssignmentStatus.ACTIVE)).thenReturn(List.of(assignment));
        when(accounts.findById(new UserAccountId(tenantId, userId))).thenReturn(Optional.of(account));
        when(notifications.findFirstByIdTenantIdAndDedupeKey(tenantId, "v4.2.2_" + userId))
                .thenReturn(Optional.of(mock(UserNotification.class)));

        service().notifyTenantAdmins(release("v4.2.2", "Notes"));

        verify(notifications, never()).save(any(UserNotification.class));
        assertThat(TenantContext.isSet()).isFalse();
    }

    private PlatformReleaseNotificationService service() {
        return new PlatformReleaseNotificationService(tenants, roles, assignments, accounts, notifications);
    }

    private static Tenant tenant(UUID tenantId, IsolationMode isolationMode) {
        return Tenant.builder().tenantId(tenantId).tenantName("Test tenant")
                .tenantType("STANDALONE_SCHOOL").state(TenantState.ACTIVE)
                .isolationMode(isolationMode).dbConnectionRef(null).build();
    }

    private static PlatformRelease release(String version, String notes) {
        return PlatformRelease.builder().releaseId(UUID.randomUUID()).versionLabel(version)
                .releaseNotes(notes).status("DEPLOYED").build();
    }
}

package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platform.PlatformUserStatus;
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

class PlatformRenewalReminderServiceTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final PlatformUserRepository platformUsers = mock(PlatformUserRepository.class);
    private final PlatformNotificationRepository platformNotifications = mock(PlatformNotificationRepository.class);
    private final PlatformNotificationService platformNotificationService = mock(PlatformNotificationService.class);
    private final RoleDefinitionRepository roles = mock(RoleDefinitionRepository.class);
    private final UserRoleAssignmentRepository assignments = mock(UserRoleAssignmentRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final UserNotificationRepository userNotifications = mock(UserNotificationRepository.class);

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void ACT_RENEWAL_REMINDER_ALLOW_notifiesBillingManagerAndActiveTenantAdminOnce() {
        LocalDate today = LocalDate.of(2026, 11, 16);
        UUID tenantId = UUID.randomUUID();
        UUID platformManagerId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().tenantId(tenantId).tenantName("North School")
                .tenantType("STANDALONE_SCHOOL").state(TenantState.ACTIVE)
                .nextRenewalAt(today.plusDays(30)).isolationMode(IsolationMode.SHARED).build();
        PlatformUser manager = PlatformUser.builder().platformUserId(platformManagerId)
                .platformRole(PlatformRole.BILLING_MANAGER).status(PlatformUserStatus.ACTIVE).build();
        RoleDefinition role = RoleDefinition.builder().id(new RoleDefinitionId(tenantId, roleId))
                .roleCode("SCHOOL_ADMIN").build();
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(tenantId, assignmentId)).userAccountId(adminUserId)
                .roleDefinitionId(roleId).status(AssignmentStatus.ACTIVE).build();
        UserAccount admin = UserAccount.builder().id(new UserAccountId(tenantId, adminUserId))
                .status(UserAccountStatus.ACTIVE).build();

        when(tenants.findByStateAndNextRenewalAtBetween(TenantState.ACTIVE, today.plusDays(30), today.plusDays(60)))
                .thenReturn(List.of(tenant));
        when(platformUsers.findAllByPlatformRoleAndStatus(PlatformRole.BILLING_MANAGER, PlatformUserStatus.ACTIVE))
                .thenReturn(List.of(manager));
        when(platformNotifications.existsByRecipientPlatformUserIdAndTriggerCodeAndEntityId(
                platformManagerId, "TENANT_RENEWAL_30D_20261216", tenantId)).thenReturn(false);
        when(roles.findByIdTenantIdAndRoleCode(tenantId, "SCHOOL_ADMIN")).thenReturn(Optional.of(role));
        when(assignments.findByIdTenantIdAndRoleDefinitionIdAndStatus(tenantId, roleId, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(assignment));
        when(accounts.findById(new UserAccountId(tenantId, adminUserId))).thenReturn(Optional.of(admin));
        when(userNotifications.findFirstByIdTenantIdAndDedupeKey(tenantId,
                "TENANT_RENEWAL_30D_20261216_" + adminUserId)).thenReturn(Optional.empty());

        PlatformRenewalReminderService service = new PlatformRenewalReminderService(tenants, platformUsers,
                platformNotifications, platformNotificationService, roles, assignments, accounts, userNotifications);

        int sent = service.sendDueRenewalReminders(today);

        assertThat(sent).isEqualTo(2);
        verify(platformNotificationService).notify(platformManagerId, "TENANT_RENEWAL_30D_20261216",
                "Tenant renewal due in 30 days", "North School is due to renew on 2026-12-16.",
                "TENANT", tenantId, "NORMAL", new UUID(0L, 0L));
        verify(userNotifications).save(any(UserNotification.class));
        assertThat(TenantContext.isSet()).isFalse();
    }
}

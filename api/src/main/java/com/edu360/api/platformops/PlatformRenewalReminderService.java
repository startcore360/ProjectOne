package com.edu360.api.platformops;

import java.time.LocalDate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

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
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.selfservice.UserNotification;
import com.edu360.api.selfservice.UserNotificationRepository;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

import lombok.RequiredArgsConstructor;

/** Sends the E3-S7 60-day and 30-day renewal reminders to platform billing managers and tenant admins. */
@Service
@RequiredArgsConstructor
public class PlatformRenewalReminderService {
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);
    private static final List<String> TENANT_ADMIN_ROLES = List.of("SCHOOL_ADMIN", "COLLEGE_ADMIN", "UNIVERSITY_ADMIN");

    private final TenantRepository tenantRepository;
    private final PlatformUserRepository platformUserRepository;
    private final PlatformNotificationRepository platformNotificationRepository;
    private final PlatformNotificationService platformNotificationService;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserNotificationRepository userNotificationRepository;

    public int sendDueRenewalReminders(LocalDate today) {
        List<Tenant> dueTenants = tenantRepository.findByStateAndNextRenewalAtBetween(
                TenantState.ACTIVE, today.plusDays(30), today.plusDays(60));
        List<PlatformUser> billingManagers = platformUserRepository.findAllByPlatformRoleAndStatus(
                PlatformRole.BILLING_MANAGER, PlatformUserStatus.ACTIVE);
        int sent = 0;
        for (Tenant tenant : dueTenants) {
            long days = ChronoUnit.DAYS.between(today, tenant.getNextRenewalAt());
            if (days != 30 && days != 60) continue;
            String dateToken = tenant.getNextRenewalAt().toString().replace("-", "");
            String trigger = "TENANT_RENEWAL_" + days + "D_" + dateToken;
            String title = "Tenant renewal due in " + days + " days";
            String body = tenant.getTenantName() + " is due to renew on " + tenant.getNextRenewalAt() + ".";

            for (PlatformUser manager : billingManagers) {
                if (!platformNotificationRepository.existsByRecipientPlatformUserIdAndTriggerCodeAndEntityId(
                        manager.getPlatformUserId(), trigger, tenant.getTenantId())) {
                    platformNotificationService.notify(manager.getPlatformUserId(), trigger, title, body,
                            "TENANT", tenant.getTenantId(), "NORMAL", SYSTEM_ACTOR);
                    sent++;
                }
            }
            sent += notifyTenantAdmins(tenant, days, dateToken, title, body);
        }
        return sent;
    }

    private int notifyTenantAdmins(Tenant tenant, long days, String dateToken, String title, String body) {
        if (tenant.getIsolationMode() == null) return 0;
        int sent = 0;
        TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef(),
                "ALL", Set.of(), null, null);
        try {
            for (String roleCode : TENANT_ADMIN_ROLES) {
                RoleDefinition role = roleDefinitionRepository
                        .findByIdTenantIdAndRoleCode(tenant.getTenantId(), roleCode).orElse(null);
                if (role == null) continue;
                List<UserRoleAssignment> assignments = userRoleAssignmentRepository
                        .findByIdTenantIdAndRoleDefinitionIdAndStatus(tenant.getTenantId(),
                                role.getId().getRoleDefinitionId(), AssignmentStatus.ACTIVE);
                Instant now = Instant.now();
                for (UserRoleAssignment assignment : assignments) {
                    if (assignment.getEffectiveFrom() != null && assignment.getEffectiveFrom().isAfter(now)) continue;
                    if (assignment.getEffectiveTo() != null && !assignment.getEffectiveTo().isAfter(now)) continue;
                    UserAccount account = userAccountRepository.findById(
                            new UserAccountId(tenant.getTenantId(), assignment.getUserAccountId())).orElse(null);
                    if (account == null || account.getStatus() != UserAccountStatus.ACTIVE) continue;
                    String dedupeKey = "TENANT_RENEWAL_" + days + "D_" + dateToken + "_" + account.getId().getUserAccountId();
                    if (userNotificationRepository.findFirstByIdTenantIdAndDedupeKey(tenant.getTenantId(), dedupeKey).isPresent()) continue;
                    UserNotification notification = new UserNotification(tenant.getTenantId(), UUID.randomUUID(),
                            account.getId().getUserAccountId(), assignment.getInstitutionId(),
                            "TENANT_RENEWAL_" + days + "D", title, body, "TENANT_RENEWAL",
                            tenant.getTenantId(), SYSTEM_ACTOR, "NORMAL", dedupeKey);
                    notification.setCreatedByUserId(null);
                    userNotificationRepository.save(notification);
                    sent++;
                }
            }
        } finally {
            TenantContext.clear();
        }
        return sent;
    }
}

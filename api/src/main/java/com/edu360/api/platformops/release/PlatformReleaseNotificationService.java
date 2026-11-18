package com.edu360.api.platformops.release;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.selfservice.UserNotification;
import com.edu360.api.selfservice.UserNotificationRepository;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Publishes a deployed release's notes to active tenant administrators. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformReleaseNotificationService {
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);
    private static final List<String> TENANT_ADMIN_ROLES =
            List.of("SCHOOL_ADMIN", "COLLEGE_ADMIN", "UNIVERSITY_ADMIN");
    private static final int MAX_NOTIFICATION_BODY_LENGTH = 2000;
    private static final int MAX_TENANT_ATTEMPTS = 2;

    private final TenantRepository tenantRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserNotificationRepository userNotificationRepository;

    /**
     * Suspends the platform release transaction. Each repository write then uses its own
     * edu_app transaction while the correct shared-tenant context is installed. A notification
     * failure must never roll back a successful platform deployment.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void notifyTenantAdmins(PlatformRelease release) {
        List<Tenant> tenants;
        try {
            tenants = tenantRepository.findAll();
        } catch (RuntimeException exception) {
            log.error("Unable to enumerate tenants for deployed release {}", release.getVersionLabel(), exception);
            return;
        }

        for (Tenant tenant : tenants) {
            if (tenant.getState() != TenantState.ACTIVE && tenant.getState() != TenantState.TRIAL) {
                continue;
            }
            if (tenant.getIsolationMode() != IsolationMode.SHARED) {
                continue;
            }
            notifyTenantWithRetry(tenant, release);
        }
    }

    private void notifyTenantWithRetry(Tenant tenant, PlatformRelease release) {
        for (int attempt = 1; attempt <= MAX_TENANT_ATTEMPTS; attempt++) {
            try {
                notifyTenantAdmins(tenant, release);
                return;
            } catch (RuntimeException exception) {
                log.warn("Release notification attempt {} failed for tenant {} and release {}",
                        attempt, tenant.getTenantId(), release.getVersionLabel(), exception);
            }
        }
        log.error("Release notifications exhausted retries for tenant {} and release {}",
                tenant.getTenantId(), release.getVersionLabel());
    }

    private void notifyTenantAdmins(Tenant tenant, PlatformRelease release) {
        TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef(),
                "ALL", Set.of(), null, null);
        try {
            Instant now = Instant.now();
            String title = "EDU 360 " + release.getVersionLabel() + " deployed";
            String body = notificationBody(release.getReleaseNotes());

            for (String roleCode : TENANT_ADMIN_ROLES) {
                RoleDefinition role = roleDefinitionRepository
                        .findByIdTenantIdAndRoleCode(tenant.getTenantId(), roleCode).orElse(null);
                if (role == null) {
                    continue;
                }

                List<UserRoleAssignment> assignments = userRoleAssignmentRepository
                        .findByIdTenantIdAndRoleDefinitionIdAndStatus(tenant.getTenantId(),
                                role.getId().getRoleDefinitionId(), AssignmentStatus.ACTIVE);
                for (UserRoleAssignment assignment : assignments) {
                    if (assignment.getEffectiveFrom() != null && assignment.getEffectiveFrom().isAfter(now)) {
                        continue;
                    }
                    if (assignment.getEffectiveTo() != null && !assignment.getEffectiveTo().isAfter(now)) {
                        continue;
                    }

                    UserAccount account = userAccountRepository.findById(
                            new UserAccountId(tenant.getTenantId(), assignment.getUserAccountId())).orElse(null);
                    if (account == null || account.getStatus() != UserAccountStatus.ACTIVE) {
                        continue;
                    }

                    UUID userId = account.getId().getUserAccountId();
                    String dedupeKey = release.getVersionLabel() + "_" + userId;
                    if (userNotificationRepository
                            .findFirstByIdTenantIdAndDedupeKey(tenant.getTenantId(), dedupeKey).isPresent()) {
                        continue;
                    }

                    UserNotification notification = new UserNotification(tenant.getTenantId(), UUID.randomUUID(),
                            userId, assignment.getInstitutionId(), "RELEASE_DEPLOYED", title, body,
                            "PLATFORM_RELEASE", release.getReleaseId(), SYSTEM_ACTOR, "NORMAL", dedupeKey);
                    notification.setCreatedByUserId(null);
                    userNotificationRepository.save(notification);
                }
            }
        } finally {
            TenantContext.clear();
        }
    }

    private String notificationBody(String releaseNotes) {
        if (releaseNotes == null || releaseNotes.isBlank()) {
            return "Release notes are available from your platform administrator.";
        }
        if (releaseNotes.length() <= MAX_NOTIFICATION_BODY_LENGTH) {
            return releaseNotes;
        }
        return releaseNotes.substring(0, MAX_NOTIFICATION_BODY_LENGTH - 3) + "...";
    }
}

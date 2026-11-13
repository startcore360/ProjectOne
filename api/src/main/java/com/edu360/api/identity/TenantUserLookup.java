package com.edu360.api.identity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.edu360.api.exception.InvalidCredentialsException;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionId;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.selfservice.UserActiveRoleRepository;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;

/**
 * WHAT: the one place that turns "this subject, in this realm, belongs to this tenant" into a
 * real, active UserAccount - shared by both the bearer-token converter
 * (TenantIssuerAuthenticationManagerResolver, after JWT verification succeeds) and
 * AuthService.login (after Keycloak's own password check succeeds), so the two call sites can
 * never drift into checking different things.
 *
 * Scopes: Reads user's active role assignment(s) and computes real institution scope:
 * - TENANT scope: institutionScope = "ALL" (per D4 §2.2 reserved for tenant-wide roles)
 * - INSTITUTION / DEPARTMENT scope: institutionScope = "LIST", institutionIds = the selected assignment's institution
 * - No active assignment: institutionScope = "NONE", institutionIds = {}
 */
@Component
public class TenantUserLookup {

    private final UserIdentityLinkRepository userIdentityLinkRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final UserActiveRoleRepository userActiveRoleRepository;

    public TenantUserLookup(
            UserIdentityLinkRepository userIdentityLinkRepository,
            UserAccountRepository userAccountRepository,
            @Autowired(required = false) UserRoleAssignmentRepository userRoleAssignmentRepository,
            @Autowired(required = false) RoleDefinitionRepository roleDefinitionRepository) {
        this(userIdentityLinkRepository, userAccountRepository, userRoleAssignmentRepository, roleDefinitionRepository, null);
    }

    @Autowired
    public TenantUserLookup(
            UserIdentityLinkRepository userIdentityLinkRepository,
            UserAccountRepository userAccountRepository,
            @Autowired(required = false) UserRoleAssignmentRepository userRoleAssignmentRepository,
            @Autowired(required = false) RoleDefinitionRepository roleDefinitionRepository,
            @Autowired(required = false) UserActiveRoleRepository userActiveRoleRepository) {
        this.userIdentityLinkRepository = userIdentityLinkRepository;
        this.userAccountRepository = userAccountRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.roleDefinitionRepository = roleDefinitionRepository;
        this.userActiveRoleRepository = userActiveRoleRepository;
    }

    public record ResolvedUser(UserAccount account, UserIdentityLink link) {
    }

    public ResolvedUser resolveActiveUser(Tenant tenant, String realm, String subject) {
        // Initial fail-closed tenant context required before querying tenant data
        TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef());

        UserIdentityLink link = userIdentityLinkRepository
                .findByIdTenantIdAndIdentityRealmAndIdentitySubjectAndStatus(
                        tenant.getTenantId(), realm, subject, IdentityLinkStatus.ACTIVE)
                .orElseThrow(() -> new InvalidCredentialsException("No active account for this identity"));

        UserAccount account = userAccountRepository
                .findById(new UserAccountId(tenant.getTenantId(), link.getUserAccountId()))
                .filter(a -> a.getStatus() == UserAccountStatus.ACTIVE)
                .orElseThrow(() -> new InvalidCredentialsException("Account is not active"));

        String institutionScope = "NONE";
        Set<UUID> institutionIds = Set.of();
        String activeRoleCode = null;
        ScopeType activeScopeType = null;
        UUID activeAssignmentId = null;
        boolean activeRoleRequired = false;

        if (userRoleAssignmentRepository != null) {
            List<UserRoleAssignment> assignments = userRoleAssignmentRepository
                    .findByIdTenantIdAndUserAccountIdAndStatus(
                            tenant.getTenantId(), account.getId().getUserAccountId(), AssignmentStatus.ACTIVE)
                    .stream()
                    .filter(TenantUserLookup::isCurrentlyEffective)
                    .toList();

            if (!assignments.isEmpty()) {
                UserRoleAssignment chosen = null;
                if (userActiveRoleRepository != null) {
                    chosen = userActiveRoleRepository
                            .findByIdTenantIdAndIdUserAccountId(tenant.getTenantId(), account.getId().getUserAccountId())
                            .flatMap(active -> assignments.stream()
                                    .filter(a -> a.getId().getUserRoleAssignmentId().equals(active.getUserRoleAssignmentId()))
                                    .findFirst())
                            .orElse(null);
                }

                // If no chosen active role in DB, default only if there is exactly one active assignment
                if (chosen == null && assignments.size() == 1) {
                    chosen = assignments.get(0);
                }

                // A-02: several active roles and no saved choice: no role is active, and every
                // request except role switching and self-service reads gets 409 ACTIVE_ROLE_REQUIRED.
                activeRoleRequired = chosen == null;

                if (chosen != null) {
                    activeAssignmentId = chosen.getId().getUserRoleAssignmentId();
                    activeScopeType = chosen.getScopeType();
                    if (activeScopeType == ScopeType.TENANT) {
                        institutionScope = "ALL";
                    } else {
                        institutionScope = "LIST";
                        // Keep the selected active role, but include its active institution assignments.
                        institutionIds = chosen.getInstitutionId() == null
                                ? Set.of()
                                : Set.of(chosen.getInstitutionId());
                    }

                    if (roleDefinitionRepository != null) {
                        activeRoleCode = roleDefinitionRepository
                                .findById(new RoleDefinitionId(tenant.getTenantId(), chosen.getRoleDefinitionId()))
                                .map(RoleDefinition::getRoleCode)
                                .orElse(null);
                    }
                }
            }
        }

        // Set the full authenticated tenant context with real institution scope and role
        TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef(),
                institutionScope, institutionIds, activeRoleCode, activeScopeType,
                activeAssignmentId, activeRoleRequired);

        return new ResolvedUser(account, link);
    }

    private static boolean isCurrentlyEffective(UserRoleAssignment assignment) {
        java.time.Instant now = java.time.Instant.now();
        if (assignment.getEffectiveFrom() != null && assignment.getEffectiveFrom().isAfter(now)) {
            return false;
        }
        return assignment.getEffectiveTo() == null || assignment.getEffectiveTo().isAfter(now);
    }
}

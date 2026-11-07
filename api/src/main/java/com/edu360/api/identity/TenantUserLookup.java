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
 * - INSTITUTION / DEPARTMENT scope: institutionScope = "LIST", institutionIds = {institutionId}
 * - No active assignment: institutionScope = "NONE", institutionIds = {}
 */
@Component
public class TenantUserLookup {

    private final UserIdentityLinkRepository userIdentityLinkRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;

    public TenantUserLookup(
            UserIdentityLinkRepository userIdentityLinkRepository,
            UserAccountRepository userAccountRepository,
            @Autowired(required = false) UserRoleAssignmentRepository userRoleAssignmentRepository,
            @Autowired(required = false) RoleDefinitionRepository roleDefinitionRepository) {
        this.userIdentityLinkRepository = userIdentityLinkRepository;
        this.userAccountRepository = userAccountRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.roleDefinitionRepository = roleDefinitionRepository;
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

        if (userRoleAssignmentRepository != null) {
            List<UserRoleAssignment> assignments = userRoleAssignmentRepository
                    .findByIdTenantIdAndUserAccountIdAndStatus(
                            tenant.getTenantId(), account.getId().getUserAccountId(), AssignmentStatus.ACTIVE);

            if (!assignments.isEmpty()) {
                // Prioritize TENANT scope if user has multiple active assignments
                UserRoleAssignment primary = assignments.stream()
                        .filter(a -> a.getScopeType() == ScopeType.TENANT)
                        .findFirst()
                        .orElse(assignments.get(0));

                activeScopeType = primary.getScopeType();
                if (activeScopeType == ScopeType.TENANT) {
                    institutionScope = "ALL";
                } else {
                    institutionScope = "LIST";
                    if (primary.getInstitutionId() != null) {
                        institutionIds = Set.of(primary.getInstitutionId());
                    }
                }

                if (roleDefinitionRepository != null) {
                    activeRoleCode = roleDefinitionRepository
                            .findById(new RoleDefinitionId(tenant.getTenantId(), primary.getRoleDefinitionId()))
                            .map(RoleDefinition::getRoleCode)
                            .orElse(null);
                }
            }
        }

        // Set the full authenticated tenant context with real institution scope and role
        TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef(),
                institutionScope, institutionIds, activeRoleCode, activeScopeType);

        return new ResolvedUser(account, link);
    }
}

package com.edu360.api.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleAction;
import com.edu360.api.rbac.RoleActionId;
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
import com.edu360.api.rbac.UserRoleAssignmentId;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;

import lombok.RequiredArgsConstructor;

/**
 * DEV-ONLY: the real-world gap this closes is that sql/05_ref_data.sql seeds 71
 * ref.standard_role rows and 1,851 ref.standard_role_action pairs, but nothing in api/ ever
 * turns one of those templates into an actual app.role_definition/app.role_action/
 * app.user_role_assignment row for a real logged-in user - PermissionEvaluatorService.can(...)
 * always finds zero active assignments, so every dev login is permission-less no matter what
 * ref.standard_role_action says it should be allowed to do.
 *
 * SCOPE, flagged rather than silently widened: TENANT_CREATE (platform.tenant + app.tenant_local)
 * and real USER_CREATE (app.user_account + app.user_identity_link) do not exist in this codebase
 * yet - see DevAdminSeeder's own class javadoc ("real additional scope belonging to the Platform
 * epic's TENANT_CREATE story, not this migration") and UserCreationController's javadoc
 * ("deliberately NOT the full USER_CREATE story: there is no user actually created"). This class
 * does NOT fabricate a tenant or a user account to work around that - on a freshly provisioned
 * dev database with no tenant/user rows yet, run() iterates zero tenants (platform.tenant is
 * empty) or finds zero ACTIVE accounts per tenant, and is a complete no-op, logged at debug. The
 * moment a real tenant + ACTIVE user_account exists (manually inserted for manual testing today;
 * automatically once TENANT_CREATE/USER_CREATE are built), this seeder gives that account exactly
 * one standard role so the RBAC evaluator has something real to check.
 *
 * WHICH role, per account: looked up by the account's display name in {@link #TEST_ACCOUNT_ROLES}
 * (the named ABC/XYZ dev test personas from earlier manual testing, e.g. "ABC Teacher One" ->
 * CLASS_TEACHER) - every other ACTIVE account in the tenant falls back to 'COLLEGE_ADMIN' ("College
 * Admin", COLLEGE level, is_staff=true), the broadest generic staff role available, same default
 * this class used before per-account roles existed (picked because it is the role
 * USER_PASSWORD_RESET_UNLOCK/USER_DEACTIVATE/ROLE_CUSTOM_MANAGE already use as their canonical
 * "institution admin" example in sql/05_ref_data.sql rows 456/451/873). This was previously only
 * the FIRST active account per tenant; now every active account gets a role, since a frontend
 * dashboard now reads this to pick a role-appropriate view (see AuthService.resolveRole) and a
 * test account with no assignment at all would just fall back to the generic dashboard, same as
 * a real not-yet-assigned user - which defeats the point of a named test persona.
 *
 * WHICH scope: no app.institution rows are seeded anywhere in sql/05_ref_data.sql (confirmed by
 * reading the whole file - only ref.* template rows exist, no app.institution INSERTs), so this
 * seeder scopes every assignment at TENANT level (scopeType=TENANT, institutionId=null,
 * departmentId=null) rather than inventing a fake institution id Addendum rule 8/9 would then
 * have to reject as make-believe.
 *
 * IDEMPOTENT, same pattern as DevAdminSeeder: re-running this on an already-seeded tenant is a
 * no-op per account (checks for an existing STANDARD RoleDefinition for that account's resolved
 * role code and an existing ACTIVE TENANT-scope UserRoleAssignment for that account before
 * inserting either).
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevRoleAssignmentSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevRoleAssignmentSeeder.class);

    /** Fallback for any ACTIVE account not named in {@link #TEST_ACCOUNT_ROLES} - see class javadoc. */
    static final String DEFAULT_STANDARD_ROLE_CODE = "COLLEGE_ADMIN";

    /**
     * Known dev test personas by exact display name -> ref.standard_role code. Add a row here for
     * any other named ABC/XYZ test account that should land on its own role-shaped dashboard
     * instead of the generic COLLEGE_ADMIN default.
     */
    static final Map<String, String> TEST_ACCOUNT_ROLES = Map.of(
            "ABC Teacher One", "CLASS_TEACHER"
    );

    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final RoleActionRepository roleActionRepository;
    private final StandardRoleRepository standardRoleRepository;
    private final StandardRoleActionRepository standardRoleActionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Override
    public void run(String... args) {
        List<Tenant> tenants = tenantRepository.findAll();
        if (tenants.isEmpty()) {
            log.debug("DevRoleAssignmentSeeder: no tenants provisioned yet (platform.tenant is "
                    + "empty) - nothing to seed a role assignment for.");
            return;
        }
        for (Tenant tenant : tenants) {
            seedTenant(tenant);
        }
    }

    private void seedTenant(Tenant tenant) {
        TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef());
        try {
            List<UserAccount> activeAccounts =
                    userAccountRepository.findByIdTenantIdAndStatus(tenant.getTenantId(), UserAccountStatus.ACTIVE);
            if (activeAccounts.isEmpty()) {
                log.debug("DevRoleAssignmentSeeder: tenant {} has no ACTIVE user_account rows yet - "
                        + "nothing to seed a role assignment for.", tenant.getTenantId());
                return;
            }
            for (UserAccount account : activeAccounts) {
                String roleCode = TEST_ACCOUNT_ROLES.getOrDefault(account.getDisplayName(), DEFAULT_STANDARD_ROLE_CODE);
                RoleDefinition roleDefinition = ensureRoleDefinition(tenant.getTenantId(), account, roleCode);
                ensureUserRoleAssignment(tenant.getTenantId(), account, roleDefinition);
            }
        } finally {
            TenantContext.clear();
        }
    }

    private RoleDefinition ensureRoleDefinition(UUID tenantId, UserAccount account, String roleCode) {
        List<RoleDefinition> existing = roleDefinitionRepository.findByIdTenantId(tenantId);
        for (RoleDefinition candidate : existing) {
            if (candidate.getRoleOrigin() == RoleOrigin.STANDARD
                    && roleCode.equals(candidate.getBaseStandardRole())
                    && candidate.getStatus() == RoleDefinitionStatus.ACTIVE) {
                return candidate;
            }
        }
        return createRoleDefinition(tenantId, account, roleCode);
    }

    private RoleDefinition createRoleDefinition(UUID tenantId, UserAccount account, String roleCode) {
        UUID actor = account.getId().getUserAccountId();
        StandardRole standardRole = standardRoleRepository.findById(roleCode).orElse(null);
        RoleDefinition roleDefinition = RoleDefinition.builder()
                .id(new RoleDefinitionId(tenantId, UUID.randomUUID()))
                .roleCode(roleCode)
                .roleName(standardRole != null ? standardRole.getRoleName() : roleCode)
                .roleOrigin(RoleOrigin.STANDARD)
                .roleLevel(standardRole != null ? standardRole.getRoleLevel() : RoleLevel.COLLEGE)
                .baseStandardRole(roleCode)
                .customScope(null)
                .sensitiveRole(false)
                .status(RoleDefinitionStatus.ACTIVE)
                .createdBy(actor)
                .updatedBy(actor)
                .build();
        RoleDefinition saved = roleDefinitionRepository.save(roleDefinition);
        copyStandardRoleActions(tenantId, saved, roleCode, actor);
        log.warn("DevRoleAssignmentSeeder: seeded a dev RoleDefinition ({}) for tenant {} from "
                + "ref.standard_role '{}'. Dev only.", saved.getId().getRoleDefinitionId(), tenantId, roleCode);
        return saved;
    }

    private void copyStandardRoleActions(UUID tenantId, RoleDefinition roleDefinition, String roleCode, UUID actor) {
        List<StandardRoleAction> templateActions = standardRoleActionRepository.findByRoleCode(roleCode);
        UUID roleDefinitionId = roleDefinition.getId().getRoleDefinitionId();
        for (StandardRoleAction templateAction : templateActions) {
            boolean alreadyGranted = roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(
                    tenantId, roleDefinitionId, templateAction.getActionCode());
            if (alreadyGranted) {
                continue;
            }
            RoleAction roleAction = RoleAction.builder()
                    .id(new RoleActionId(tenantId, UUID.randomUUID()))
                    .roleDefinitionId(roleDefinitionId)
                    .actionCode(templateAction.getActionCode())
                    .createdBy(actor)
                    .updatedBy(actor)
                    .build();
            roleActionRepository.save(roleAction);
        }
    }

    private void ensureUserRoleAssignment(UUID tenantId, UserAccount account, RoleDefinition roleDefinition) {
        UUID userAccountId = account.getId().getUserAccountId();
        List<UserRoleAssignment> existing = userRoleAssignmentRepository
                .findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                        tenantId, userAccountId, ScopeType.TENANT, null, null, AssignmentStatus.ACTIVE);
        if (!existing.isEmpty()) {
            return;
        }
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(tenantId, UUID.randomUUID()))
                .userAccountId(userAccountId)
                .roleDefinitionId(roleDefinition.getId().getRoleDefinitionId())
                .scopeType(ScopeType.TENANT)
                .institutionId(null)
                .departmentId(null)
                .effectiveFrom(Instant.now())
                .effectiveTo(null)
                .status(AssignmentStatus.ACTIVE)
                .createdBy(userAccountId)
                .updatedBy(userAccountId)
                .build();
        userRoleAssignmentRepository.save(assignment);
        log.warn("DevRoleAssignmentSeeder: seeded a dev TENANT-scope ACTIVE UserRoleAssignment for "
                + "user {} in tenant {} -> role '{}'. Dev only.", userAccountId, tenantId,
                roleDefinition.getRoleCode());
    }
}

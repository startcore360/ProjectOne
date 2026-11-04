package com.edu360.api.config;

import java.time.Instant;
import java.util.List;
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
import com.edu360.api.rbac.StandardRoleAction;
import com.edu360.api.rbac.StandardRoleActionRepository;
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
 * WHICH role: ref.standard_role 'COLLEGE_ADMIN' ("College Admin", COLLEGE level, is_staff=true) -
 * picked as the broadest generic staff role available for a generic test account (University/
 * School Admin would be an equally defensible choice; COLLEGE_ADMIN was picked because it is the
 * role USER_PASSWORD_RESET_UNLOCK/USER_DEACTIVATE/ROLE_CUSTOM_MANAGE already use as their
 * canonical "institution admin" example in sql/05_ref_data.sql rows 456/451/873).
 *
 * WHICH scope: no app.institution rows are seeded anywhere in sql/05_ref_data.sql (confirmed by
 * reading the whole file - only ref.* template rows exist, no app.institution INSERTs), so this
 * seeder scopes the assignment at TENANT level (scopeType=TENANT, institutionId=null,
 * departmentId=null) rather than inventing a fake institution id Addendum rule 8/9 would then
 * have to reject as make-believe.
 *
 * IDEMPOTENT, same pattern as DevAdminSeeder: re-running this on an already-seeded tenant is a
 * no-op (checks for an existing STANDARD/COLLEGE_ADMIN RoleDefinition and an existing ACTIVE
 * TENANT-scope UserRoleAssignment for the chosen account before inserting either).
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevRoleAssignmentSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevRoleAssignmentSeeder.class);

    /** Broadest generic staff standard role available - see class javadoc. */
    static final String SEEDED_STANDARD_ROLE_CODE = "COLLEGE_ADMIN";

    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final RoleActionRepository roleActionRepository;
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
            UserAccount testAccount = activeAccounts.get(0);
            RoleDefinition roleDefinition = ensureRoleDefinition(tenant.getTenantId(), testAccount);
            ensureUserRoleAssignment(tenant.getTenantId(), testAccount, roleDefinition);
        } finally {
            TenantContext.clear();
        }
    }

    private RoleDefinition ensureRoleDefinition(UUID tenantId, UserAccount testAccount) {
        List<RoleDefinition> existing = roleDefinitionRepository.findByIdTenantId(tenantId);
        for (RoleDefinition candidate : existing) {
            if (candidate.getRoleOrigin() == RoleOrigin.STANDARD
                    && SEEDED_STANDARD_ROLE_CODE.equals(candidate.getBaseStandardRole())
                    && candidate.getStatus() == RoleDefinitionStatus.ACTIVE) {
                return candidate;
            }
        }
        return createRoleDefinition(tenantId, testAccount);
    }

    private RoleDefinition createRoleDefinition(UUID tenantId, UserAccount testAccount) {
        UUID actor = testAccount.getId().getUserAccountId();
        RoleDefinition roleDefinition = RoleDefinition.builder()
                .id(new RoleDefinitionId(tenantId, UUID.randomUUID()))
                .roleCode(SEEDED_STANDARD_ROLE_CODE)
                .roleName("College Admin")
                .roleOrigin(RoleOrigin.STANDARD)
                .roleLevel(RoleLevel.COLLEGE)
                .baseStandardRole(SEEDED_STANDARD_ROLE_CODE)
                .customScope(null)
                .sensitiveRole(false)
                .status(RoleDefinitionStatus.ACTIVE)
                .createdBy(actor)
                .updatedBy(actor)
                .build();
        RoleDefinition saved = roleDefinitionRepository.save(roleDefinition);
        copyStandardRoleActions(tenantId, saved, actor);
        log.warn("DevRoleAssignmentSeeder: seeded a dev RoleDefinition ({}) for tenant {} from "
                + "ref.standard_role '{}'. Dev only.", saved.getId().getRoleDefinitionId(), tenantId,
                SEEDED_STANDARD_ROLE_CODE);
        return saved;
    }

    private void copyStandardRoleActions(UUID tenantId, RoleDefinition roleDefinition, UUID actor) {
        List<StandardRoleAction> templateActions =
                standardRoleActionRepository.findByRoleCode(SEEDED_STANDARD_ROLE_CODE);
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

    private void ensureUserRoleAssignment(UUID tenantId, UserAccount testAccount, RoleDefinition roleDefinition) {
        UUID userAccountId = testAccount.getId().getUserAccountId();
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
                SEEDED_STANDARD_ROLE_CODE);
    }
}

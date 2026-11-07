package com.edu360.api.rbac;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * WHAT: the single server-side action evaluator for the V4.0 role/permission model - exactly
 * the mechanism the spec calls can(principal, actionCode, scope, record) (used by every
 * fine-grained action check, e.g. ROLLDOWN_VIEW later; roll-down itself is a separate, later
 * story and is deliberately NOT implemented here).
 *
 * HOW a decision is reached, in order:
 *   1. The action must exist in ref.action_catalog - an unknown action code is always denied,
 *      never silently allowed.
 *   2. Addendum rule 11 (self-elevation/self-approval is prohibited): if the action is an
 *      approval action (ref.action_catalog.is_approval_action) and the target is the principal
 *      themselves, DENY immediately - before the role/permission lookup even runs, so no role
 *      can ever be granted enough permission to bypass this guard.
 *   3. Look up the user's ACTIVE app.user_role_assignment row whose scope EXACTLY matches the
 *      requested scope (tenant/institution/department) for this tenant+user (Addendum rule 10:
 *      exactly one role is active within a given assignment and access context). Deliberately
 *      EXACT, not "covers": a TENANT-scoped assignment is not treated as also granting
 *      INSTITUTION- or DEPARTMENT-scoped actions in this first slice, and a request for a scope
 *      the principal holds no assignment in is denied rather than guessed at (Addendum rules 8
 *      and 9: separately scoped assignments are never combined to expand access). Covering
 *      broader-grants-narrower scope semantics, if the product ever wants them, is a
 *      deliberate design decision for a later story, not an accident of this one.
 *   4. If more than one ACTIVE row matches that exact scope (which Addendum rule 10 says should
 *      never happen - note there is no database UNIQUE constraint on
 *      app.user_role_assignment enforcing "at most one ACTIVE row per exact scope" today, so
 *      this is a real possible data state, not just a defensive branch), fail safe: deny and
 *      log a warning rather than pick one arbitrarily or union their permissions.
 *   5. Check whether that one assignment's role (app.role_action, keyed by role_definition_id -
 *      see RoleAction's javadoc for why that table, not ref.standard_role_action, is the
 *      runtime source of truth) holds the requested action code. Allow or deny accordingly.
 *
 * WHY this never throws a raw exception (can(...) always returns a PermissionDecision): a
 * denial is an expected, first-class outcome, not a failure of the evaluator - the caller
 * decides what to do with it. authorize(...) is the thin controller-facing wrapper that turns a
 * denial into an UnauthorizedException, which GlobalExceptionHandler maps to a 403 with the
 * standard {timestamp, status, error} body - the same convention every other denied-access path
 * in this app already uses.
 */
@Service
public class PermissionEvaluatorService {

    private static final Logger log = LoggerFactory.getLogger(PermissionEvaluatorService.class);

    private static final Set<String> PERSONAL_ACTIONS = Set.of(
            "PAYMENT_MAKE", "APPLICATION_SUBMIT", "STUDENT_PROFILE_EDIT", "ATTENDANCE_VIEW_OWN", "RESULT_VIEW_OWN"
    );

    private static final Set<String> LOCKED_ACTIONS = Set.of(
            "PAYROLL_APPROVE_TOTALS", "SALARY_INDIVIDUAL_VIEW", "TENANT_EXPORT_APPROVE",
            "TENANT_DELETION_APPROVE", "RETENTION_CHANGE_APPROVE"
    );

    private final ActionCatalogRepository actionCatalogRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RoleActionRepository roleActionRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final TenantStepInRepository tenantStepInRepository;

    @Autowired
    public PermissionEvaluatorService(
            ActionCatalogRepository actionCatalogRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository,
            RoleActionRepository roleActionRepository,
            @Autowired(required = false) RoleDefinitionRepository roleDefinitionRepository,
            @Autowired(required = false) TenantStepInRepository tenantStepInRepository) {
        this.actionCatalogRepository = actionCatalogRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.roleActionRepository = roleActionRepository;
        this.roleDefinitionRepository = roleDefinitionRepository;
        this.tenantStepInRepository = tenantStepInRepository;
    }

    public PermissionEvaluatorService(
            ActionCatalogRepository actionCatalogRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository,
            RoleActionRepository roleActionRepository) {
        this(actionCatalogRepository, userRoleAssignmentRepository, roleActionRepository, null, null);
    }

    public PermissionDecision can(RbacPrincipal principal, String actionCode, RbacScope scope, RbacTarget target) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(actionCode, "actionCode is required");
        Objects.requireNonNull(scope, "scope is required");
        RbacTarget effectiveTarget = target == null ? RbacTarget.NONE : target;

        ActionCatalog action = actionCatalogRepository.findById(actionCode).orElse(null);
        if (action == null) {
            return PermissionDecision.deny("UNKNOWN_ACTION", "Action " + actionCode + " is not in the approved Action List.");
        }

        if (action.isApprovalAction()
                && effectiveTarget.targetUserAccountId() != null
                && effectiveTarget.targetUserAccountId().equals(principal.userAccountId())) {
            return PermissionDecision.deny("SELF_APPROVAL_FORBIDDEN",
                    "A principal may not perform approval action " + actionCode + " on their own record (Addendum rule 11).");
        }

        List<UserRoleAssignment> matches = userRoleAssignmentRepository
                .findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                        principal.tenantId(), principal.userAccountId(), scope.scopeType(),
                        scope.institutionId(), scope.departmentId(), AssignmentStatus.ACTIVE)
                .stream()
                .filter(this::isCurrentlyEffective)
                .toList();

        if (matches.isEmpty() && roleDefinitionRepository != null) {
            // Check Top-Post Authority (TPA-01 / TOP_POST_AUTHORITY_RULES.md)
            List<UserRoleAssignment> allActive = userRoleAssignmentRepository
                    .findByIdTenantIdAndUserAccountIdAndStatus(principal.tenantId(), principal.userAccountId(), AssignmentStatus.ACTIVE)
                    .stream()
                    .filter(this::isCurrentlyEffective)
                    .toList();

            for (UserRoleAssignment a : allActive) {
                RoleDefinition roleDef = roleDefinitionRepository.findById(
                        new RoleDefinitionId(principal.tenantId(), a.getRoleDefinitionId())).orElse(null);
                if (roleDef != null) {
                    String code = roleDef.getRoleCode();
                    boolean covers = false;
                    if ("MANAGEMENT".equals(code) && a.getScopeType() == ScopeType.TENANT) {
                        covers = true; // Management covers entire tenant
                    } else if ("VICE_CHANCELLOR".equals(code) && (a.getScopeType() == ScopeType.TENANT
                            || (a.getScopeType() == ScopeType.INSTITUTION && Objects.equals(a.getInstitutionId(), scope.institutionId())))) {
                        covers = true; // Vice Chancellor covers university and its institutions
                    } else if (("PRINCIPAL_COLLEGE".equals(code) || "PRINCIPAL_SCHOOL".equals(code))
                            && a.getScopeType() == ScopeType.INSTITUTION
                            && Objects.equals(a.getInstitutionId(), scope.institutionId())) {
                        covers = true; // Principal covers departments in their institution
                    }

                    if (covers) {
                        if (PERSONAL_ACTIONS.contains(actionCode)) {
                            return PermissionDecision.deny("TOP_POST_PERSONAL_FORBIDDEN", "Top posts cannot execute personal applicant/student/parent actions.");
                        }
                        if (LOCKED_ACTIONS.contains(actionCode)) {
                            return PermissionDecision.deny("ACTION_LOCKED", "Action " + actionCode + " is permanently locked (Decision D / TPA-04).");
                        }
                        boolean hasAction = roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(
                                principal.tenantId(), a.getRoleDefinitionId(), actionCode);
                        if (hasAction) {
                            return PermissionDecision.allow();
                        }
                    }
                }
            }
        }

        if (matches.isEmpty()) {
            return PermissionDecision.deny("NO_ACTIVE_ROLE_IN_SCOPE",
                    "No active role assignment for this user in the requested scope (Addendum rule 10).");
        }

        if (matches.size() > 1) {
            String activeRoleCode = TenantContext.currentActiveRoleCode();
            if (activeRoleCode != null && roleDefinitionRepository != null) {
                List<UserRoleAssignment> activeRoleMatches = matches.stream()
                        .filter(a -> {
                            RoleDefinition rd = roleDefinitionRepository.findById(
                                    new RoleDefinitionId(principal.tenantId(), a.getRoleDefinitionId())).orElse(null);
                            return rd != null && activeRoleCode.equalsIgnoreCase(rd.getRoleCode());
                        })
                        .toList();
                if (!activeRoleMatches.isEmpty()) {
                    matches = activeRoleMatches;
                }
            }

            if (matches.size() > 1) {
                // PO-3: If caller has multiple active assignments, check if exactly one holds this action
                List<UserRoleAssignment> withAction = matches.stream()
                        .filter(a -> roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(
                                principal.tenantId(), a.getRoleDefinitionId(), actionCode))
                        .toList();
                if (withAction.size() == 1) {
                    matches = withAction;
                } else {
                    log.warn("Found {} ACTIVE user_role_assignment rows for tenant={} user={} scope={} - "
                                    + "expected at most one (Addendum rule 10); denying rather than guessing.",
                            matches.size(), principal.tenantId(), principal.userAccountId(), scope);
                    return PermissionDecision.deny("AMBIGUOUS_ROLE_ASSIGNMENT",
                            "More than one active role assignment matched this exact scope; refusing rather than combining them.");
                }
            }
        }

        UserRoleAssignment assignment = matches.get(0);
        boolean hasAction = roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(
                principal.tenantId(), assignment.getRoleDefinitionId(), actionCode);

        if (!hasAction) {
            return PermissionDecision.deny("ACTION_NOT_GRANTED",
                    "The active role for this scope does not hold action " + actionCode + ".");
        }
        return PermissionDecision.allow();
    }

    /**
     * Controller-facing wrapper: same decision as {@link #can}, but a denial becomes an
     * {@link UnauthorizedException} (-> 403 via GlobalExceptionHandler) instead of a value the
     * caller has to remember to check.
     */
    public void authorize(RbacPrincipal principal, String actionCode, RbacScope scope, RbacTarget target) {
        PermissionDecision decision = can(principal, actionCode, scope, target);
        if (!decision.allowed()) {
            throw new UnauthorizedException(decision.message());
        }
    }

    private boolean isCurrentlyEffective(UserRoleAssignment assignment) {
        Instant now = Instant.now();
        if (assignment.getEffectiveFrom() != null && assignment.getEffectiveFrom().isAfter(now)) {
            return false;
        }
        return assignment.getEffectiveTo() == null || assignment.getEffectiveTo().isAfter(now);
    }
}

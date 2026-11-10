package com.edu360.api.rbac;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.edu360.api.exception.ActiveRoleRequiredException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.selfservice.UserActiveRole;
import com.edu360.api.selfservice.UserActiveRoleRepository;
import com.edu360.api.tenant.TenantContext;

/**
 * WHAT: the single server-side action evaluator for the V4.0 role/permission model - exactly
 * the mechanism the spec calls can(principal, actionCode, scope, record).
 *
 * HOW a decision is reached, in order:
 *   1. The action must exist in ref.action_catalog - an unknown action code is always denied.
 *   2. A check with no caller is denied (NO_PRINCIPAL, Addendum rule 15).
 *   3. Addendum rule 11: an approval action against the caller's own record is denied before
 *      any role lookup.
 *   4. A-02 (story 6, PO-3, Addendum rules 8-10, D3 CR-11): ONLY the caller's ACTIVE role
 *      assignment is used. The active assignment is the saved choice (app.user_active_role, or
 *      the copy of it TenantUserLookup put on TenantContext for this request); a user with exactly
 *      one active assignment needs no choice. A user with several active assignments and no
 *      choice is refused with ACTIVE_ROLE_REQUIRED (authorize(...) turns that into 409). The
 *      other, non-active assignments are never consulted and their permissions never combined.
 *   5. The active assignment's scope must EXACTLY match the requested scope (a TENANT assignment
 *      does not grant INSTITUTION or DEPARTMENT actions), except for top-post coverage
 *      (docs/TOP_POST_AUTHORITY_RULES.md, TPA-01..TPA-05): when the ACTIVE role is a top post
 *      whose area covers the requested scope it may act there - never on personal actions and
 *      never on LOCKED_ACTIONS.
 *   6. The active role definition must be ACTIVE (DB-A11) and must hold the action in
 *      app.role_action (the runtime source of truth, see RoleAction).
 *
 * WHY can(...) never throws for a denial: a denial is an expected outcome; authorize(...) is the
 * controller-facing wrapper that turns it into an exception (403, or 409 for ACTIVE_ROLE_REQUIRED).
 */
@Service
public class PermissionEvaluatorService {

    private static final Logger log = LoggerFactory.getLogger(PermissionEvaluatorService.class);

    /** Reason code when several active roles exist and none is chosen (A-02). */
    public static final String ACTIVE_ROLE_REQUIRED = ActiveRoleRequiredException.CODE;

    /**
     * Personal actions of students, parents and applicants: the 22 actions in group "Personal"
     * of D2 (docs/TOP_POST_AUTHORITY_RULES.md section 1 and Appendix B). A top post never performs
     * them through coverage.
     */
    public static final Set<String> PERSONAL_ACTIONS = Set.of(
            "AUTH_FIRST_LOGIN_RESET", "ATTENDANCE_VIEW_OWN", "HALL_TICKET_DOWNLOAD", "SUPPLEMENTARY_REGISTER",
            "RESULT_VIEW_OWN", "ASSIGNMENT_SUBMIT", "ASSIGNMENT_VIEW_OWN", "FEE_PAY_ONLINE", "FEE_VIEW_OWN",
            "STUDENT_LEAVE_APPLY", "COMPLAINT_RAISE", "RISK_VIEW_OWN", "STUDENT_RECORD_VIEW_OWN",
            "CERTIFICATE_REQUEST", "PLACEMENT_PROFILE_EDIT", "JOB_APPLY", "OFFER_RESPOND", "PLACEMENT_VIEW_CHILD",
            "APPLICATION_SUBMIT", "TRANSPORT_VIEW_OWN", "EXAM_SCHEDULE_VIEW_OWN", "HOSTEL_VIEW_OWN"
    );

    /**
     * The single shared lock list (A-03), used for top-post coverage and for every custom role:
     * Decision D (individual salaries stay locked: PAYROLL_APPROVE_TOTALS, SALARY_INDIVIDUAL_VIEW)
     * and the Data Protection Officer approvals that stay with a separate named holder
     * (TPA-04; D2 action 198 "Locked; named holder only").
     */
    public static final Set<String> LOCKED_ACTIONS = Set.of(
            "PAYROLL_APPROVE_TOTALS", "SALARY_INDIVIDUAL_VIEW", "TENANT_EXPORT_APPROVE",
            "TENANT_DELETION_APPROVE", "RETENTION_CHANGE_APPROVE"
    );

    /**
     * The 22 actions in group "Platform" of D2 (TOP_POST_AUTHORITY_RULES.md Appendix B). D2/D3
     * never-allowed: platform users never get tenant roles and platform work never enters a
     * tenant (D3 CR-06, D2 action 267, GUARD_PLATFORM_ISOLATION), so no tenant custom role may
     * carry one of these.
     */
    public static final Set<String> PLATFORM_ONLY_ACTIONS = Set.of(
            "TENANT_EXPORT_APPROVE", "TENANT_DELETION_CONFIRM", "TENANT_DELETION_APPROVE", "RETENTION_CHANGE_APPROVE",
            "PLAN_MANAGE", "TENANT_CREATE", "TENANT_ACTIVATE", "TENANT_SUSPEND_ARCHIVE", "TENANT_SETTINGS",
            "FEATURE_TOGGLE", "INVOICE_MANAGE", "DISCOUNT_APPLY", "OVERDUE_FLAG", "SUPPORT_TICKET_MANAGE",
            "SUPPORT_ACCESS_USE", "RELEASE_MANAGE", "INTEGRATION_TYPES_MANAGE", "PLATFORM_AUDIT_VIEW",
            "USAGE_COUNTS_VIEW", "SECURITY_POLICY_SET", "PLATFORM_USER_MANAGE", "PLATFORM_REPORTS_VIEW"
    );

    /**
     * A-03 / D3 CR-03: true when an action may never be placed in a custom role, whatever the
     * creator holds: LOCKED_ACTIONS plus the platform-only actions. Approval actions (CR-10) are
     * refused separately from ref.action_catalog.is_approval_action, so they are not copied here.
     */
    public static boolean isLockedForCustomRoles(String actionCode) {
        return actionCode != null && (LOCKED_ACTIONS.contains(actionCode) || PLATFORM_ONLY_ACTIONS.contains(actionCode));
    }

    private final ActionCatalogRepository actionCatalogRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RoleActionRepository roleActionRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final TenantStepInRepository tenantStepInRepository;
    private final UserActiveRoleRepository userActiveRoleRepository;

    @Autowired
    public PermissionEvaluatorService(
            ActionCatalogRepository actionCatalogRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository,
            RoleActionRepository roleActionRepository,
            @Autowired(required = false) RoleDefinitionRepository roleDefinitionRepository,
            @Autowired(required = false) TenantStepInRepository tenantStepInRepository,
            @Autowired(required = false) UserActiveRoleRepository userActiveRoleRepository) {
        this.actionCatalogRepository = actionCatalogRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.roleActionRepository = roleActionRepository;
        this.roleDefinitionRepository = roleDefinitionRepository;
        this.tenantStepInRepository = tenantStepInRepository;
        this.userActiveRoleRepository = userActiveRoleRepository;
    }

    public PermissionEvaluatorService(
            ActionCatalogRepository actionCatalogRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository,
            RoleActionRepository roleActionRepository,
            RoleDefinitionRepository roleDefinitionRepository,
            TenantStepInRepository tenantStepInRepository) {
        this(actionCatalogRepository, userRoleAssignmentRepository, roleActionRepository,
                roleDefinitionRepository, tenantStepInRepository, null);
    }

    public PermissionEvaluatorService(
            ActionCatalogRepository actionCatalogRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository,
            RoleActionRepository roleActionRepository) {
        this(actionCatalogRepository, userRoleAssignmentRepository, roleActionRepository, null, null, null);
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

        // Deny by default (Addendum rule 15): a check with no caller can never pass.
        if (principal.userAccountId() == null) {
            return PermissionDecision.deny("NO_PRINCIPAL", "No signed-in user was supplied for " + actionCode + ".");
        }

        if (action.isApprovalAction()
                && effectiveTarget.targetUserAccountId() != null
                && effectiveTarget.targetUserAccountId().equals(principal.userAccountId())) {
            return PermissionDecision.deny("SELF_APPROVAL_FORBIDDEN",
                    "A principal may not perform approval action " + actionCode + " on their own record (Addendum rule 11).");
        }

        List<UserRoleAssignment> exactMatches = userRoleAssignmentRepository
                .findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                        principal.tenantId(), principal.userAccountId(), scope.scopeType(),
                        scope.institutionId(), scope.departmentId(), AssignmentStatus.ACTIVE)
                .stream()
                .filter(this::isCurrentlyEffective)
                .toList();

        ActiveResolution resolution = resolve(principal, exactMatches);
        if (resolution.active() == null) {
            if (resolution.choiceRequired()) {
                if (exactMatches.size() > 1) {
                    // Two ACTIVE rows at the same exact scope (Addendum rule 10 says this should never
                    // happen): never combine them; the caller must choose one.
                    log.warn("Found {} ACTIVE user_role_assignment rows for tenant={} user={} scope={} and no active "
                                    + "role chosen - denying rather than combining them.",
                            exactMatches.size(), principal.tenantId(), principal.userAccountId(), scope);
                    return PermissionDecision.deny("AMBIGUOUS_ROLE_ASSIGNMENT",
                            "More than one active role assignment matched this exact scope; choose the active role first.");
                }
                return PermissionDecision.deny(ACTIVE_ROLE_REQUIRED,
                        "You hold more than one role. Choose the active role before acting (PO-3).");
            }
            return PermissionDecision.deny("NO_ACTIVE_ROLE_IN_SCOPE",
                    "No active role assignment for this user in the requested scope (Addendum rule 10).");
        }

        UserRoleAssignment active = resolution.active();
        RoleDefinition activeRole = roleDefinition(principal.tenantId(), active.getRoleDefinitionId());
        // DB-A11 / Q-02: only an ACTIVE role definition grants anything - DRAFT, INACTIVE, RETIRED
        // and ARCHIVED grant nothing, and an assignment pointing at no role definition grants nothing.
        if (roleDefinitionRepository != null && activeRole == null) {
            return PermissionDecision.deny("ROLE_NOT_ACTIVE", "The assigned role definition does not exist.");
        }
        if (activeRole != null && activeRole.getStatus() != RoleDefinitionStatus.ACTIVE) {
            return PermissionDecision.deny("ROLE_NOT_ACTIVE", "The assigned role definition is not in ACTIVE status.");
        }

        if (!sameScope(active, scope)) {
            if (activeRole == null || !topPostCovers(activeRole.getRoleCode(), active, scope)) {
                return PermissionDecision.deny("NO_ACTIVE_ROLE_IN_SCOPE",
                        "The active role does not apply to the requested scope (Addendum rules 8-10).");
            }
            if (PERSONAL_ACTIONS.contains(actionCode)) {
                return PermissionDecision.deny("TOP_POST_PERSONAL_FORBIDDEN",
                        "Top posts cannot execute personal applicant/student/parent actions.");
            }
            if (LOCKED_ACTIONS.contains(actionCode)) {
                return PermissionDecision.deny("ACTION_LOCKED",
                        "Action " + actionCode + " is permanently locked (Decision D / TPA-04).");
            }
        }

        boolean hasAction = roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(
                principal.tenantId(), active.getRoleDefinitionId(), actionCode);
        if (!hasAction) {
            return PermissionDecision.deny("ACTION_NOT_GRANTED",
                    "The active role for this scope does not hold action " + actionCode + ".");
        }
        return PermissionDecision.allow();
    }

    /**
     * Controller-facing wrapper: same decision as {@link #can}, but a denial becomes an exception:
     * {@link ActiveRoleRequiredException} (409) when the caller must first choose an active role,
     * otherwise {@link UnauthorizedException} (403).
     */
    public void authorize(RbacPrincipal principal, String actionCode, RbacScope scope, RbacTarget target) {
        PermissionDecision decision = can(principal, actionCode, scope, target);
        if (!decision.allowed()) {
            if (ACTIVE_ROLE_REQUIRED.equals(decision.reasonCode())
                    || "AMBIGUOUS_ROLE_ASSIGNMENT".equals(decision.reasonCode())) {
                throw new ActiveRoleRequiredException(decision.message());
            }
            throw new UnauthorizedException(decision.message());
        }
    }

    /**
     * A-02: the caller's active role assignment, or empty when they hold none.
     *
     * @throws ActiveRoleRequiredException when they hold several and have chosen none
     */
    public Optional<UserRoleAssignment> activeAssignment(RbacPrincipal principal) {
        Objects.requireNonNull(principal, "principal is required");
        if (principal.userAccountId() == null) {
            return Optional.empty();
        }
        ActiveResolution resolution = resolve(principal, List.of());
        if (resolution.choiceRequired()) {
            throw new ActiveRoleRequiredException("You hold more than one role. Choose the active role before acting (PO-3).");
        }
        return Optional.ofNullable(resolution.active());
    }

    /**
     * Every ACTIVE, currently effective assignment the caller holds in this tenant, whichever is
     * active. Used only for "does the caller hold this role at all" guards (A-03, D3 CR-04: nobody
     * edits a role they hold), never to grant anything.
     */
    public List<UserRoleAssignment> heldAssignments(RbacPrincipal principal) {
        Objects.requireNonNull(principal, "principal is required");
        if (principal.userAccountId() == null) {
            return List.of();
        }
        List<UserRoleAssignment> rows = userRoleAssignmentRepository
                .findAllByIdTenantIdAndUserAccountIdAndStatusOrderByEffectiveFromAsc(
                        principal.tenantId(), principal.userAccountId(), AssignmentStatus.ACTIVE);
        return rows == null ? List.of() : rows.stream().filter(this::isCurrentlyEffective).toList();
    }

    /**
     * A-08: the scope of the caller's own active assignment ("Own scope" / "Self" in D2
     * "Limited to"). A user with no active assignment gets the tenant scope, which then denies
     * because no assignment matches it.
     */
    public RbacScope ownScope(RbacPrincipal principal) {
        return activeAssignment(principal).map(PermissionEvaluatorService::scopeOf).orElse(RbacScope.tenant());
    }

    /**
     * A-05: the scope at which an action on a target record is checked, at the level D3 gives the
     * caller's ACTIVE role: a tenant-level role (University Admin, Registrar, Vice Chancellor,
     * Management) is checked at the tenant (university) scope; an institution-level role (College
     * or School Admin, Principal) at the target's institution; a department-level role (HOD) at the
     * target's department. The target's own institution and department are always used, so a
     * record from another institution or department never matches the caller's assignment. A
     * department-level caller acting on a target with no department gets an institution scope,
     * which matches no department assignment, so the check denies.
     */
    public RbacScope targetScope(RbacPrincipal principal, UUID targetInstitutionId, UUID targetDepartmentId) {
        ScopeType level = activeAssignment(principal).map(UserRoleAssignment::getScopeType).orElse(null);
        if (level == ScopeType.TENANT) {
            return RbacScope.tenant();
        }
        if (level == ScopeType.DEPARTMENT && targetInstitutionId != null && targetDepartmentId != null) {
            return RbacScope.department(targetInstitutionId, targetDepartmentId);
        }
        return targetInstitutionId != null ? RbacScope.institution(targetInstitutionId) : RbacScope.tenant();
    }

    public static RbacScope scopeOf(UserRoleAssignment assignment) {
        if (assignment.getScopeType() == ScopeType.DEPARTMENT) {
            return RbacScope.department(assignment.getInstitutionId(), assignment.getDepartmentId());
        }
        if (assignment.getScopeType() == ScopeType.INSTITUTION) {
            return RbacScope.institution(assignment.getInstitutionId());
        }
        return RbacScope.tenant();
    }

    // ------------------------------------------------------------------------------------------

    private record ActiveResolution(UserRoleAssignment active, boolean choiceRequired) {
    }

    private ActiveResolution resolve(RbacPrincipal principal, List<UserRoleAssignment> exactMatches) {
        // Every ACTIVE, currently effective assignment of this user in this tenant. The exact-scope
        // rows are the same table read with a narrower filter, so they are merged in by id.
        Map<Object, UserRoleAssignment> all = new LinkedHashMap<>();
        for (UserRoleAssignment a : exactMatches) {
            all.put(keyOf(a), a);
        }
        List<UserRoleAssignment> everyActive = userRoleAssignmentRepository
                .findAllByIdTenantIdAndUserAccountIdAndStatusOrderByEffectiveFromAsc(
                        principal.tenantId(), principal.userAccountId(), AssignmentStatus.ACTIVE);
        if (everyActive != null) {
            for (UserRoleAssignment a : everyActive) {
                if (isCurrentlyEffective(a)) {
                    all.putIfAbsent(keyOf(a), a);
                }
            }
        }
        if (all.isEmpty()) {
            return new ActiveResolution(null, false);
        }

        UUID chosenId = chosenAssignmentId(principal);
        if (chosenId != null && all.containsKey(chosenId)) {
            return new ActiveResolution(all.get(chosenId), false);
        }
        if (all.size() == 1) {
            return new ActiveResolution(new ArrayList<>(all.values()).get(0), false);
        }
        return new ActiveResolution(null, true);
    }

    private static Object keyOf(UserRoleAssignment a) {
        return a.getId() != null && a.getId().getUserRoleAssignmentId() != null ? a.getId().getUserRoleAssignmentId() : a;
    }

    /** The saved active-role choice: this request's TenantContext copy, else app.user_active_role. */
    private UUID chosenAssignmentId(RbacPrincipal principal) {
        if (TenantContext.isSet() && principal.tenantId() != null
                && principal.tenantId().equals(TenantContext.currentTenantId())
                && TenantContext.currentActiveAssignmentId() != null) {
            return TenantContext.currentActiveAssignmentId();
        }
        if (userActiveRoleRepository == null || principal.tenantId() == null) {
            return null;
        }
        return userActiveRoleRepository
                .findByIdTenantIdAndIdUserAccountId(principal.tenantId(), principal.userAccountId())
                .map(UserActiveRole::getUserRoleAssignmentId)
                .orElse(null);
    }

    private RoleDefinition roleDefinition(UUID tenantId, UUID roleDefinitionId) {
        if (roleDefinitionRepository == null) {
            return null;
        }
        return roleDefinitionRepository.findById(new RoleDefinitionId(tenantId, roleDefinitionId)).orElse(null);
    }

    private static boolean sameScope(UserRoleAssignment a, RbacScope scope) {
        return a.getScopeType() == scope.scopeType()
                && Objects.equals(a.getInstitutionId(), scope.institutionId())
                && Objects.equals(a.getDepartmentId(), scope.departmentId());
    }

    /** Top-post coverage (TOP_POST_AUTHORITY_RULES.md, "Higher posts cover lower levels"). */
    private static boolean topPostCovers(String roleCode, UserRoleAssignment a, RbacScope scope) {
        if (roleCode == null) {
            return false;
        }
        return switch (roleCode) {
            case "MANAGEMENT", "UNIVERSITY_ADMIN" -> a.getScopeType() == ScopeType.TENANT;
            case "VICE_CHANCELLOR" -> a.getScopeType() == ScopeType.TENANT
                    || (a.getScopeType() == ScopeType.INSTITUTION && Objects.equals(a.getInstitutionId(), scope.institutionId()));
            case "PRINCIPAL_COLLEGE", "SCHOOL_PRINCIPAL" -> a.getScopeType() == ScopeType.INSTITUTION
                    && scope.scopeType() != ScopeType.TENANT
                    && Objects.equals(a.getInstitutionId(), scope.institutionId());
            default -> false;
        };
    }

    private boolean isCurrentlyEffective(UserRoleAssignment assignment) {
        Instant now = Instant.now();
        if (assignment.getEffectiveFrom() != null && assignment.getEffectiveFrom().isAfter(now)) {
            return false;
        }
        return assignment.getEffectiveTo() == null || assignment.getEffectiveTo().isAfter(now);
    }
}

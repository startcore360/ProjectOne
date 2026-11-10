package com.edu360.api.approval;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.edu360.api.institution.Institution;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.rolldown.RoleRolldownDefault;
import com.edu360.api.rolldown.RoleRolldownDefaultRepository;
import com.edu360.api.rolldown.RoleRolldownOverride;
import com.edu360.api.rolldown.RoleRolldownOverrideRepository;

/**
 * WHAT: decides WHO may act on one approval step (P-01, P-07, P-09).
 *
 * HOW: a caller may decide a step only when one of these holds:
 *   ROLE        the caller's ACTIVE role equals the step's approverRoleCode;
 *   USER        the caller is the step's named approverUserId;
 *   DELEGATION  the caller holds a valid delegation (ACTIVE, inside its dates, for this process)
 *               from a person who holds the step's role (or is the named user) - no re-delegation,
 *               because a delegate never "holds" the role by delegation;
 *   ROLLDOWN    nobody holds the step's role in the request's scope and the caller's active role
 *               is the stand-in (tenant override first, else the V4-19 default), D1 V4-19.
 * The action and scope check (PermissionEvaluatorService) is applied by the caller of this class,
 * to the caller, or to the delegator when acting by delegation.
 *
 * WHY separate: the same rule drives the inbox list, the request read, the decision and the
 * bulk decision, so they can never disagree about who is the approver.
 */
@Component
public class ApprovalRouting {

    public enum Via { ROLE, USER, DELEGATION, ROLLDOWN }

    /** How the caller qualifies. actingForRole/delegatorUserId are set for DELEGATION and ROLLDOWN. */
    public record Route(Via via, String actingForRole, UUID delegatorUserId, UUID delegationId) {
        public boolean actingFor() {
            return via == Via.DELEGATION || via == Via.ROLLDOWN;
        }
    }

    private final DelegationRepository delegationRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RoleRolldownOverrideRepository overrideRepository;
    private final RoleRolldownDefaultRepository defaultRepository;
    private final InstitutionRepository institutionRepository;

    public ApprovalRouting(
            DelegationRepository delegationRepository,
            PermissionEvaluatorService permissionEvaluatorService,
            RoleDefinitionRepository roleDefinitionRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository,
            RoleRolldownOverrideRepository overrideRepository,
            RoleRolldownDefaultRepository defaultRepository,
            InstitutionRepository institutionRepository) {
        this.delegationRepository = delegationRepository;
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.roleDefinitionRepository = roleDefinitionRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.overrideRepository = overrideRepository;
        this.defaultRepository = defaultRepository;
        this.institutionRepository = institutionRepository;
    }

    /** The caller's route to this step, or empty when the caller is not an approver of it. */
    public Optional<Route> route(UUID tenantId, UUID userId, String activeRole, ApprovalRequest request, ApprovalStep step) {
        String stepRole = step.getApproverRoleCode();
        if (activeRole != null && stepRole != null && activeRole.equalsIgnoreCase(stepRole)) {
            return Optional.of(new Route(Via.ROLE, null, null, null));
        }
        if (userId != null && userId.equals(step.getApproverUserId())) {
            return Optional.of(new Route(Via.USER, null, null, null));
        }
        Optional<Route> delegated = delegationRoute(tenantId, userId, request, step);
        if (delegated.isPresent()) {
            return delegated;
        }
        if (activeRole != null && stepRole != null && rolldownApplies(tenantId, activeRole, request, step)) {
            return Optional.of(new Route(Via.ROLLDOWN, stepRole, null, null));
        }
        return Optional.empty();
    }

    /** True while the user has handed this step's approvals to a delegate (items sit with the delegate). */
    public boolean handedOver(UUID tenantId, UUID userId, ApprovalRequest request) {
        return delegationRepository.findByIdTenantIdAndDelegatorUserId(tenantId, userId).stream()
                .anyMatch(d -> inWindow(d) && covers(d, request));
    }

    private Optional<Route> delegationRoute(UUID tenantId, UUID userId, ApprovalRequest request, ApprovalStep step) {
        for (Delegation d : delegationRepository.findByIdTenantIdAndDelegateUserId(tenantId, userId)) {
            if (!inWindow(d) || !covers(d, request)) {
                continue;
            }
            UUID delegator = d.getDelegatorUserId();
            if (delegator.equals(step.getApproverUserId()) || holdsRole(tenantId, delegator, step.getApproverRoleCode())) {
                return Optional.of(new Route(Via.DELEGATION, step.getApproverRoleCode(), delegator, d.getId().getDelegationId()));
            }
        }
        return Optional.empty();
    }

    private static boolean inWindow(Delegation d) {
        Instant now = Instant.now();
        return "ACTIVE".equals(d.getStatus())
                && d.getStartsAt() != null && !d.getStartsAt().isAfter(now)
                && d.getEndsAt() != null && d.getEndsAt().isAfter(now);
    }

    private static boolean covers(Delegation d, ApprovalRequest request) {
        String p = d.getProcessCode();
        return p == null || p.isBlank() || "ALL".equalsIgnoreCase(p) || p.equalsIgnoreCase(request.getProcessCode());
    }

    /** Does this user hold the role through any ACTIVE, current assignment? */
    private boolean holdsRole(UUID tenantId, UUID userId, String roleCode) {
        if (roleCode == null) {
            return false;
        }
        for (UserRoleAssignment a : permissionEvaluatorService.heldAssignments(new RbacPrincipal(tenantId, userId))) {
            RoleDefinition rd = roleDefinitionRepository
                    .findById(new com.edu360.api.rbac.RoleDefinitionId(tenantId, a.getRoleDefinitionId())).orElse(null);
            if (rd != null && roleCode.equalsIgnoreCase(rd.getRoleCode())) {
                return true;
            }
        }
        return false;
    }

    // --------------------------------------------------------------------- roll-down (P-09)

    private boolean rolldownApplies(UUID tenantId, String activeRole, ApprovalRequest request, ApprovalStep step) {
        if (anyHolder(tenantId, step.getApproverRoleCode(), request.getInstitutionId())) {
            return false;
        }
        return standIn(tenantId, request, step.getApproverRoleCode())
                .map(activeRole::equalsIgnoreCase).orElse(false);
    }

    /** Is there any ACTIVE, current holder of the role whose scope covers the request's institution? */
    public boolean anyHolder(UUID tenantId, String roleCode, UUID institutionId) {
        RoleDefinition rd = roleDefinitionRepository.findByIdTenantIdAndRoleCode(tenantId, roleCode).orElse(null);
        if (rd == null) {
            return false;
        }
        Instant now = Instant.now();
        List<UserRoleAssignment> rows = userRoleAssignmentRepository.findByIdTenantIdAndRoleDefinitionIdAndStatus(
                tenantId, rd.getId().getRoleDefinitionId(), AssignmentStatus.ACTIVE);
        return rows != null && rows.stream().anyMatch(a ->
                (a.getEffectiveFrom() == null || !a.getEffectiveFrom().isAfter(now))
                        && (a.getEffectiveTo() == null || a.getEffectiveTo().isAfter(now))
                        && (a.getScopeType() == ScopeType.TENANT
                                || institutionId == null
                                || Objects.equals(a.getInstitutionId(), institutionId)));
    }

    /**
     * The role that acts for a missing role on approvals: this tenant's ACTIVE override (mode
     * ASSIGN, kind APPROVE; the institution's own before a tenant-wide one), else the V4-19
     * default for the institution's context (ref.role_rolldown_default, lowest preference).
     */
    public Optional<String> standIn(UUID tenantId, ApprovalRequest request, String missingRole) {
        UUID institutionId = request.getInstitutionId();
        Optional<RoleRolldownOverride> override = overrideRepository.findByIdTenantIdAndStatus(tenantId, "ACTIVE").stream()
                .filter(o -> missingRole.equalsIgnoreCase(o.getMissingRoleCode())
                        && "APPROVE".equalsIgnoreCase(o.getPermissionKind())
                        && "ASSIGN".equalsIgnoreCase(o.getMode())
                        && o.getTargetRoleCode() != null
                        && (o.getInstitutionId() == null || o.getInstitutionId().equals(institutionId)))
                .max(Comparator.comparing((RoleRolldownOverride o) -> o.getInstitutionId() != null)
                        .thenComparing(o -> o.getEffectiveFrom() == null ? Instant.MIN : o.getEffectiveFrom()));
        if (override.isPresent()) {
            return Optional.of(override.get().getTargetRoleCode());
        }
        String context = "UNIVERSITY_WIDE";
        if (institutionId != null) {
            Institution inst = institutionRepository.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId).orElse(null);
            if (inst != null && inst.getInstitutionType() != null) {
                context = inst.getInstitutionType().toUpperCase();
            }
        }
        return defaultRepository.findByContextOrderByMissingRoleCodeAscPreferenceAsc(context).stream()
                .filter(d -> missingRole.equalsIgnoreCase(d.getMissingRoleCode()) && "APPROVE".equalsIgnoreCase(d.getPermissionKind()))
                .min(Comparator.comparingInt(RoleRolldownDefault::getPreference))
                .map(RoleRolldownDefault::getStandInRoleCode);
    }
}

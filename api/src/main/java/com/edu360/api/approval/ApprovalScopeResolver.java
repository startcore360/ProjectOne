package com.edu360.api.approval;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.edu360.api.institution.StaffAssignment;
import com.edu360.api.institution.StaffAssignmentRepository;
import com.edu360.api.institution.StaffProfileRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.ScopeType;

/**
 * WHAT: the scope at which an approver's action on one approval request is checked.
 *
 * HOW: the request's institution, exactly as before, for every caller whose ACTIVE assignment is
 * TENANT or INSTITUTION scoped (PermissionEvaluatorService.targetScope). Only when the checked
 * caller's ACTIVE assignment is DEPARTMENT scoped (an HOD) is the request's department looked up
 * and passed on, so the evaluator's exact-scope match succeeds for the HOD of THAT department and
 * nobody else. The department of a request is not stored on app.approval_request /
 * app.approval_step, so it is derived from the subject: for staff-originated requests (staff
 * leave, staff self-attendance check-in) the requester IS the staff member, and the department is
 * the one on their ACTIVE, current staff_assignment at the request's institution - the same
 * assignment StaffLeaveService / StaffCheckinApproverResolver used to pick the HOD.
 *
 * WHY fail closed: any other subject type, a requester with no staff profile, or active
 * assignments at that institution that disagree about the department (or have none) give no
 * department; targetScope then falls back to the institution scope, which no DEPARTMENT
 * assignment matches, so the HOD is denied exactly as before. The evaluator's matching rules are
 * not touched.
 */
@Component
public class ApprovalScopeResolver {

    /** Subject types raised by a staff member about themselves: the requester is the subject. */
    private static final Set<String> STAFF_OWN_SUBJECT_TYPES =
            Set.of("LEAVE_REQUEST", "STAFF_LEAVE_REQUEST", "STAFF_ATTENDANCE_CHECKIN");

    private final PermissionEvaluatorService permissions;
    private final StaffProfileRepository staffProfiles;
    private final StaffAssignmentRepository staffAssignments;

    public ApprovalScopeResolver(PermissionEvaluatorService permissions, StaffProfileRepository staffProfiles,
            StaffAssignmentRepository staffAssignments) {
        this.permissions = Objects.requireNonNull(permissions);
        this.staffProfiles = Objects.requireNonNull(staffProfiles);
        this.staffAssignments = Objects.requireNonNull(staffAssignments);
    }

    /** The scope to check the approval action at, for this (already routed) caller. */
    public RbacScope scopeFor(RbacPrincipal checked, ApprovalRequest request) {
        UUID institutionId = request.getInstitutionId();
        boolean departmentLevel = institutionId != null && permissions.activeAssignment(checked)
                .map(a -> a.getScopeType() == ScopeType.DEPARTMENT).orElse(false);
        UUID departmentId = departmentLevel ?departmentOf(checked.tenantId(), request) : null;
        return permissions.targetScope(checked, institutionId, departmentId);
    }

    /** The department the request belongs to, or null when it cannot be said for certain. */
    public UUID departmentOf(UUID tenantId, ApprovalRequest request) {
        if (request == null || request.getInstitutionId() == null || request.getRequestedByUserId() == null
                || request.getSubjectType() == null
                || !STAFF_OWN_SUBJECT_TYPES.contains(request.getSubjectType().toUpperCase(java.util.Locale.ROOT))) {
            return null;
        }
        UUID staffProfileId = staffProfiles.findByIdTenantIdAndUserAccountId(tenantId, request.getRequestedByUserId())
                .map(p -> p.getStaffProfileId()).orElse(null);
        if (staffProfileId == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        List<StaffAssignment> rows = staffAssignments.findByTenantIdAndStaffProfileIdOrderByEffectiveFromDesc(
                tenantId, staffProfileId);
        Set<UUID> departments = new HashSet<>();
        boolean any = false;
        for (StaffAssignment a : rows == null ? List.<StaffAssignment>of() : rows) {
            if (!"ACTIVE".equalsIgnoreCase(a.getStatus())
                    || (a.getEffectiveTo() != null && a.getEffectiveTo().isBefore(today))
                    || !request.getInstitutionId().equals(a.getInstitutionId())) {
                continue;
            }
            any = true;
            departments.add(a.getDepartmentId());
        }
        if (!any || departments.size() != 1) {
            return null;
        }
        return departments.iterator().next();
    }
}

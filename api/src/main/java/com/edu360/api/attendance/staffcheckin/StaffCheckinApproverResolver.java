package com.edu360.api.attendance.staffcheckin;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.edu360.api.institution.Department;
import com.edu360.api.institution.DepartmentRepository;
import com.edu360.api.institution.Institution;
import com.edu360.api.institution.StaffAssignment;

/**
 * WHAT: decides who reviews a PROVISIONAL staff self-check-in, and who it escalates to when the
 * day ends unreviewed. The result is only ever an approval-step spec (role code + optional named
 * user) for the shared approval engine; this class never decides anything itself.
 *
 * HOW (first approver):
 *   - requester is the institution's Principal  -> the institution's configured higher authority
 *     (app.institution_attendance_location.approver_for_principal_role); none configured -> no
 *     route, and the caller raises the request BLOCKED (D-002: never skipped or auto-approved);
 *   - requester is the HOD of their department (by data: department.hod_staff_assignment_id, or
 *     an active HEAD_OF_DEPARTMENT role) -> the institution's Principal;
 *   - requester belongs to a department with an HOD -> that HOD, as a named user;
 *   - requester belongs to a department with no HOD -> HEAD_OF_DEPARTMENT with no named user, so
 *     the engine's roll-down (V4-PD-01 / tenant override) picks the stand-in;
 *   - anyone else (e.g. Accountant, office staff with no department) -> the institution's
 *     Principal, which the tenant can redirect with a roll-down override.
 * Routing reads structure the institution configures (department HOD assignment, the configured
 * higher authority, roll-down overrides); it never parses designation text.
 *
 * HOW (escalation at day end): HOD step -> Principal; Principal step -> configured higher
 * authority (or stays with the Principal when none is configured); any other step -> the
 * configured higher authority, else stays where it is (there is nobody higher).
 */
@Component
public class StaffCheckinApproverResolver {

    static final String HOD_ROLE = "HEAD_OF_DEPARTMENT";
    static final String COLLEGE_PRINCIPAL_ROLE = "PRINCIPAL_COLLEGE";
    static final String SCHOOL_PRINCIPAL_ROLE = "SCHOOL_PRINCIPAL";

    /** One approval step to create: the role expected at the step and, when known, the named person. */
    public record StepSpec(String roleCode, UUID userId) {
    }

    private final JdbcTemplate jdbc;
    private final DepartmentRepository departments;

    public StaffCheckinApproverResolver(JdbcTemplate jdbc, DepartmentRepository departments) {
        this.jdbc = jdbc;
        this.departments = departments;
    }

    /** The first reviewer for this requester, or empty when none can be resolved (raise BLOCKED). */
    public Optional<StepSpec> firstApprover(UUID tenantId, String activeRole, StaffAssignment assignment,
            Institution institution, InstitutionAttendanceLocation location) {
        String principalRole = principalRoleFor(institution);
        if (isPrincipalRole(activeRole)) {
            return configuredHigherAuthority(location).map(role -> new StepSpec(role, null));
        }
        Department department = assignment.getDepartmentId() == null ? null
                : departments.findByTenantIdAndDepartmentId(tenantId, assignment.getDepartmentId()).orElse(null);
        boolean structuralHod = department != null && department.getHodStaffAssignmentId() != null
                && department.getHodStaffAssignmentId().equals(assignment.getStaffAssignmentId());
        if (structuralHod || HOD_ROLE.equalsIgnoreCase(activeRole)) {
            return Optional.of(new StepSpec(principalRole, null));
        }
        if (department != null) {
            UUID hodUser = department.getHodStaffAssignmentId() == null ? null
                    : activeUserOfAssignment(tenantId, department.getHodStaffAssignmentId());
            return Optional.of(new StepSpec(HOD_ROLE, hodUser));
        }
        return Optional.of(new StepSpec(principalRole, null));
    }

    /** Who a day-end-expired check-in escalates to, from the role of the step that went unreviewed. */
    public StepSpec escalationFor(String unreviewedStepRole, Institution institution,
            InstitutionAttendanceLocation location) {
        String principalRole = principalRoleFor(institution);
        if (HOD_ROLE.equalsIgnoreCase(unreviewedStepRole)) {
            return new StepSpec(principalRole, null);
        }
        Optional<String> higher = configuredHigherAuthority(location)
                .filter(role -> !role.equalsIgnoreCase(unreviewedStepRole));
        return new StepSpec(higher.orElse(unreviewedStepRole), null);
    }

    static String principalRoleFor(Institution institution) {
        boolean school = institution != null && institution.getInstitutionType() != null
                && institution.getInstitutionType().toUpperCase(java.util.Locale.ROOT).contains("SCHOOL");
        return school ? SCHOOL_PRINCIPAL_ROLE : COLLEGE_PRINCIPAL_ROLE;
    }

    static boolean isPrincipalRole(String roleCode) {
        return COLLEGE_PRINCIPAL_ROLE.equalsIgnoreCase(roleCode) || SCHOOL_PRINCIPAL_ROLE.equalsIgnoreCase(roleCode);
    }

    private static Optional<String> configuredHigherAuthority(InstitutionAttendanceLocation location) {
        String role = location == null ? null : location.getApproverForPrincipalRole();
        return role == null || role.isBlank() ? Optional.empty() : Optional.of(role.trim());
    }

    private UUID activeUserOfAssignment(UUID tenantId, UUID staffAssignmentId) {
        return jdbc.query("""
                SELECT sp.user_account_id
                  FROM app.staff_assignment sa
                  JOIN app.staff_profile sp ON sp.tenant_id=sa.tenant_id AND sp.staff_profile_id=sa.staff_profile_id
                 WHERE sa.tenant_id=? AND sa.staff_assignment_id=? AND sa.status='ACTIVE'
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, tenantId, staffAssignmentId);
    }
}

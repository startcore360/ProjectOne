package com.edu360.api.institution;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.HodAssignRequest;
import com.edu360.api.institution.dto.HodAssignResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.time.TenantClock;

import lombok.RequiredArgsConstructor;

/**
 * WHAT: HOD_ASSIGN ("Make a faculty member HOD") - College Admin, own college.
 *
 * ORDER OF CHECKS (deny before touching any data, per the task brief):
 *   1. Load the department (read-only) purely to learn ITS institutionId - the scope the
 *      evaluator needs is "which college", which only the department row itself can say; the
 *      path only carries the department id.
 *   2. Authorize HOD_ASSIGN at RbacScope.institution(department.institutionId) - "Own college"
 *      in ref.standard_role_action maps to the INSTITUTION scope type the same way
 *      UserCreationController's scope factory does.
 *   3. Only once authorized: load the target staff_assignment and enforce "must already be a
 *      faculty member of that department" (department.hod_staff_assignment_id's own column
 *      comment) by checking staffAssignment.departmentId equals this department's id and the
 *      assignment is ACTIVE. See Department's class javadoc for why this check lives here and
 *      not in a database trigger - none is committed for this rule today.
 *   4. Persist department.hodStaffAssignmentId = the target staff_assignment id.
 */
@Service
@RequiredArgsConstructor
public class HodAssignmentService {

    private final DepartmentRepository departmentRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private TenantClock tenantClock;

    @Autowired(required = false)
    public void setTenantClock(TenantClock clock) { this.tenantClock = clock; }

    private LocalDate today(UUID tenantId) { return tenantClock != null ? tenantClock.today(tenantId) : LocalDate.now(TenantClock.DEFAULT_ZONE); }

    @Transactional
    public HodAssignResponse assignHod(RbacPrincipal principal, UUID departmentId, String ifMatch, HodAssignRequest request) {
        Department department = departmentRepository.findByTenantIdAndDepartmentId(principal.tenantId(), departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department " + departmentId + " was not found."));

        permissionEvaluatorService.authorize(principal, "HOD_ASSIGN",
                RbacScope.institution(department.getInstitutionId()), RbacTarget.NONE);

        int expectedVersion = parseIfMatchVersion(ifMatch);
        if (department.getRowVersion() == null || department.getRowVersion() != expectedVersion) {
            throw new BusinessRuleViolationException("VERSION_CONFLICT",
                    "Department was changed by another user. Reload and retry with the latest ETag.");
        }

        StaffAssignment target = staffAssignmentRepository
                .findByTenantIdAndStaffAssignmentId(principal.tenantId(), request.staffAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Staff assignment " + request.staffAssignmentId() + " was not found."));

        if (!"ACTIVE".equals(target.getStatus())) {
            throw new UnauthorizedException(
                    "Staff assignment " + target.getStaffAssignmentId() + " is not ACTIVE; cannot be made HOD.");
        }
        if (target.getDepartmentId() == null || !target.getDepartmentId().equals(departmentId)) {
            throw new UnauthorizedException(
                    "Staff assignment " + target.getStaffAssignmentId()
                            + " is not a faculty member of department " + departmentId + ".");
        }
        if (!department.getInstitutionId().equals(target.getInstitutionId())) {
            throw new UnauthorizedException("Staff assignment does not belong to the department institution.");
        }
        LocalDate today = today(principal.tenantId());
        if (target.getEffectiveFrom() == null || target.getEffectiveFrom().isAfter(today)
                || (target.getEffectiveTo() != null && target.getEffectiveTo().isBefore(today))) {
            throw new BusinessRuleViolationException("STAFF_ASSIGNMENT_NOT_EFFECTIVE", "Staff assignment is not effective on the allocation date.");
        }

        int updated = departmentRepository.updateHodAssignment(principal.tenantId(), departmentId,
                expectedVersion, target.getStaffAssignmentId(), principal.userAccountId());
        if (updated != 1) {
            throw new BusinessRuleViolationException("VERSION_CONFLICT",
                    "Department was changed by another user. Reload and retry with the latest ETag.");
        }
        return new HodAssignResponse(department.getDepartmentId(), target.getStaffAssignmentId(), expectedVersion + 1);
    }

    private int parseIfMatchVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new BusinessRuleViolationException("IF_MATCH_REQUIRED", "If-Match header with the department ETag is required.");
        }
        String value = ifMatch.trim();
        if (value.startsWith("W/")) value = value.substring(2).trim();
        value = value.replaceAll("\"", "");
        try {
            int version = Integer.parseInt(value);
            if (version < 1) throw new NumberFormatException();
            return version;
        } catch (NumberFormatException ex) {
            throw new BusinessRuleViolationException("IF_MATCH_REQUIRED", "If-Match must contain a positive department row version.");
        }
    }
}

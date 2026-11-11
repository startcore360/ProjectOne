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
    public HodAssignResponse assignHod(RbacPrincipal principal, UUID departmentId, HodAssignRequest request) {
        Department department = departmentRepository.findByTenantIdAndDepartmentId(principal.tenantId(), departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department " + departmentId + " was not found."));

        permissionEvaluatorService.authorize(principal, "HOD_ASSIGN",
                RbacScope.institution(department.getInstitutionId()), RbacTarget.NONE);

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

        department.setHodStaffAssignmentId(target.getStaffAssignmentId());
        department.setUpdatedBy(principal.userAccountId());
        departmentRepository.save(department);

        return new HodAssignResponse(department.getDepartmentId(), department.getHodStaffAssignmentId());
    }
}

package com.edu360.api.institution;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.MentorAssignRequest;
import com.edu360.api.institution.dto.MentorAssignResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.time.TenantClock;

import lombok.RequiredArgsConstructor;

/**
 * WHAT: MENTOR_ASSIGN ("Assign a mentor / class coordinator to a section") - Head of Department,
 * own department.
 *
 * WHY the section -> department lookup is a 3-hop join (section.semesterId -> semester.
 * programYearId -> program_year.programId -> program.departmentId): app.section has NO
 * department_id column of its own - a college section only carries semesterId, and the
 * department only shows up four tables away via app.program.department_id. This is the real
 * shape of the committed schema, not a shortcut invented here; see Semester/ProgramYear/
 * Program's javadocs. app.mentor_assignment itself is "college section" only (its own comment),
 * so a section with no semesterId (a school section) is rejected before any of that join runs.
 *
 * ORDER OF CHECKS (deny before touching any data):
 *   1. Load the section, then climb to its owning department - needed purely to know the scope
 *      (which department) the evaluator must check.
 *   2. Authorize MENTOR_ASSIGN at RbacScope.department(institutionId, departmentId) - "Own
 *      department" is a DEPARTMENT-scoped grant in ref.standard_role_action.
 *   3. Only once authorized: load the target staff_assignment and enforce "faculty of the
 *      section department" (app.mentor_assignment.staff_assignment_id's own column comment -
 *      enforced here, not by a database trigger; see Section's javadoc for why).
 *   4. End the current mentor assignment (if any) and insert a new one - a history table, the
 *      same append/supersede pattern app.staff_assignment itself uses.
 */
@Service
@RequiredArgsConstructor
public class MentorAssignmentService {

    private final SectionRepository sectionRepository;
    private final SemesterRepository semesterRepository;
    private final ProgramYearRepository programYearRepository;
    private final ProgramRepository programRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final MentorAssignmentRepository mentorAssignmentRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private TenantClock tenantClock;

    @Autowired(required = false)
    public void setTenantClock(TenantClock clock) { this.tenantClock = clock; }

    private LocalDate today(UUID tenantId) { return tenantClock != null ? tenantClock.today(tenantId) : LocalDate.now(TenantClock.DEFAULT_ZONE); }

    @Transactional
    public MentorAssignResponse assignMentor(RbacPrincipal principal, UUID sectionId, MentorAssignRequest request) {
        Section section = sectionRepository.findByTenantIdAndSectionId(principal.tenantId(), sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section " + sectionId + " was not found."));

        if (section.getSemesterId() == null) {
            throw new UnauthorizedException(
                    "Section " + sectionId + " is a school section; MENTOR_ASSIGN applies to college sections only.");
        }

        UUID departmentId = departmentOf(principal.tenantId(), section.getSemesterId());

        permissionEvaluatorService.authorize(principal, "MENTOR_ASSIGN",
                RbacScope.department(section.getInstitutionId(), departmentId), RbacTarget.NONE);

        StaffAssignment target = staffAssignmentRepository
                .findByTenantIdAndStaffAssignmentId(principal.tenantId(), request.staffAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Staff assignment " + request.staffAssignmentId() + " was not found."));

        if (!"ACTIVE".equals(target.getStatus())) {
            throw new UnauthorizedException(
                    "Staff assignment " + target.getStaffAssignmentId() + " is not ACTIVE; cannot be made mentor.");
        }
        if (target.getDepartmentId() == null || !target.getDepartmentId().equals(departmentId)) {
            throw new UnauthorizedException(
                    "Staff assignment " + target.getStaffAssignmentId()
                            + " is not a faculty member of department " + departmentId + ".");
        }
        if (!section.getInstitutionId().equals(target.getInstitutionId())) {
            throw new UnauthorizedException("Staff assignment does not belong to the section institution.");
        }
        LocalDate today = today(principal.tenantId());
        requireEffective(target, today);
        mentorAssignmentRepository.findByTenantIdAndSectionIdAndEffectiveToIsNull(principal.tenantId(), sectionId)
                .ifPresent(current -> {
                    current.setEffectiveTo(today);
                    current.setUpdatedBy(principal.userAccountId());
                    mentorAssignmentRepository.save(current);
                });

        MentorAssignment created = new MentorAssignment();
        created.setMentorAssignmentId(UUID.randomUUID());
        created.setTenantId(principal.tenantId());
        created.setInstitutionId(section.getInstitutionId());
        created.setSectionId(sectionId);
        created.setStaffAssignmentId(target.getStaffAssignmentId());
        created.setEffectiveFrom(today);
        created.setCreatedBy(principal.userAccountId());
        created.setUpdatedBy(principal.userAccountId());
        mentorAssignmentRepository.save(created);

        return new MentorAssignResponse(sectionId, created.getMentorAssignmentId(), target.getStaffAssignmentId());
    }

    private void requireEffective(StaffAssignment target, LocalDate date) {
        if (target.getEffectiveFrom() == null || target.getEffectiveFrom().isAfter(date)
                || (target.getEffectiveTo() != null && target.getEffectiveTo().isBefore(date))) {
            throw new BusinessRuleViolationException("STAFF_ASSIGNMENT_NOT_EFFECTIVE", "Staff assignment is not effective on the allocation date.");
        }
    }

    private UUID departmentOf(UUID tenantId, UUID semesterId) {
        Semester semester = semesterRepository.findByTenantIdAndSemesterId(tenantId, semesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Semester " + semesterId + " was not found."));
        ProgramYear programYear = programYearRepository
                .findByTenantIdAndProgramYearId(tenantId, semester.getProgramYearId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Program year " + semester.getProgramYearId() + " was not found."));
        Program program = programRepository.findByTenantIdAndProgramId(tenantId, programYear.getProgramId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Program " + programYear.getProgramId() + " was not found."));
        return program.getDepartmentId();
    }
}

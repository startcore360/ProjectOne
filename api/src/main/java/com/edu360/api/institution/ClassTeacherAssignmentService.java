package com.edu360.api.institution;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.ClassTeacherAssignRequest;
import com.edu360.api.institution.dto.ClassTeacherAssignResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.time.TenantClock;

import lombok.RequiredArgsConstructor;

/**
 * WHAT: CLASS_TEACHER_ASSIGN ("Assign a Class Teacher to a class") - School Admin, own school.
 *
 * WHY the path is /sections/{id}/class-teacher, not /classes/{id}/class-teacher, even though the
 * action's own name says "class": app.class_teacher_assignment's only FK is section_id, and its
 * own table comment says so explicitly - "Unit: section (OD-24 asks class vs section)". OD-24 is
 * a still-open governance-register decision; this slice follows what the committed table can
 * actually store (section) rather than guessing OD-24's outcome, and flags it rather than
 * resolving it silently. If OD-24 is later decided the other way, this endpoint's unit changes
 * with it.
 *
 * WHY there is no "faculty of department" check here unlike HOD_ASSIGN/MENTOR_ASSIGN: schools
 * have no departments at all (app.department's own comment: "colleges only; schools have none in
 * v1 [HI-12]"). The rule this enforces instead is the one the schema CAN express for a school
 * staff member: the target staff_assignment must be ACTIVE and belong to the same institution
 * (school) as the section.
 */
@Service
@RequiredArgsConstructor
public class ClassTeacherAssignmentService {

    private final SectionRepository sectionRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final ClassTeacherAssignmentRepository classTeacherAssignmentRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private TenantClock tenantClock;

    @Autowired(required = false)
    public void setTenantClock(TenantClock clock) { this.tenantClock = clock; }

    private LocalDate today(UUID tenantId) { return tenantClock != null ? tenantClock.today(tenantId) : LocalDate.now(TenantClock.DEFAULT_ZONE); }

    private void requireEffective(StaffAssignment target, LocalDate date) {
        if (target.getEffectiveFrom() == null || target.getEffectiveFrom().isAfter(date)
                || (target.getEffectiveTo() != null && target.getEffectiveTo().isBefore(date))) {
            throw new BusinessRuleViolationException("STAFF_ASSIGNMENT_NOT_EFFECTIVE", "Staff assignment is not effective on the allocation date.");
        }
    }

    @Transactional
    public ClassTeacherAssignResponse assignClassTeacher(
            RbacPrincipal principal, UUID sectionId, ClassTeacherAssignRequest request) {
        Section section = sectionRepository.findByTenantIdAndSectionId(principal.tenantId(), sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section " + sectionId + " was not found."));

        if (section.getClassId() == null) {
            throw new UnauthorizedException(
                    "Section " + sectionId + " is a college section; CLASS_TEACHER_ASSIGN applies to school sections only.");
        }

        permissionEvaluatorService.authorize(principal, "CLASS_TEACHER_ASSIGN",
                RbacScope.institution(section.getInstitutionId()), RbacTarget.NONE);

        StaffAssignment target = staffAssignmentRepository
                .findByTenantIdAndStaffAssignmentId(principal.tenantId(), request.staffAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Staff assignment " + request.staffAssignmentId() + " was not found."));

        if (!"ACTIVE".equals(target.getStatus())) {
            throw new UnauthorizedException(
                    "Staff assignment " + target.getStaffAssignmentId() + " is not ACTIVE; cannot be made Class Teacher.");
        }
        if (target.getInstitutionId() == null || !target.getInstitutionId().equals(section.getInstitutionId())) {
            throw new UnauthorizedException(
                    "Staff assignment " + target.getStaffAssignmentId()
                            + " does not belong to institution " + section.getInstitutionId() + ".");
        }
        requireEffective(target, today(principal.tenantId()));

        LocalDate today = today(principal.tenantId());
        classTeacherAssignmentRepository
                .findByTenantIdAndSectionIdAndEffectiveToIsNull(principal.tenantId(), sectionId)
                .ifPresent(current -> {
                    current.setEffectiveTo(today);
                    current.setUpdatedBy(principal.userAccountId());
                    classTeacherAssignmentRepository.save(current);
                });

        ClassTeacherAssignment created = new ClassTeacherAssignment();
        created.setClassTeacherAssignmentId(UUID.randomUUID());
        created.setTenantId(principal.tenantId());
        created.setInstitutionId(section.getInstitutionId());
        created.setSectionId(sectionId);
        created.setStaffAssignmentId(target.getStaffAssignmentId());
        created.setEffectiveFrom(today);
        created.setCreatedBy(principal.userAccountId());
        created.setUpdatedBy(principal.userAccountId());
        classTeacherAssignmentRepository.save(created);

        return new ClassTeacherAssignResponse(sectionId, created.getClassTeacherAssignmentId(), target.getStaffAssignmentId());
    }
}

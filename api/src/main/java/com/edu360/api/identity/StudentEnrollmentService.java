package com.edu360.api.identity;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.institution.AcademicSession;
import com.edu360.api.institution.AcademicSessionRepository;
import com.edu360.api.institution.Section;
import com.edu360.api.institution.SectionRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

/**
 * Enrols an existing student profile into a validated section. Account/profile creation remains
 * in the existing user-creation flow; callers can compose this method in that same transaction.
 */
@Service
public class StudentEnrollmentService {
    private final StudentEnrollmentRepository enrollments;
    private final StudentProfileRepository students;
    private final SectionRepository sections;
    private final AcademicSessionRepository sessions;
    private final PermissionEvaluatorService permissions;

    public StudentEnrollmentService(StudentEnrollmentRepository enrollments,
            StudentProfileRepository students, SectionRepository sections,
            AcademicSessionRepository sessions, PermissionEvaluatorService permissions) {
        this.enrollments = enrollments;
        this.students = students;
        this.sections = sections;
        this.sessions = sessions;
        this.permissions = permissions;
    }

    @Transactional
    public StudentEnrollment enrolExistingStudent(TenantPrincipal actor, UUID studentProfileId,
            UUID institutionId, UUID sectionId, UUID academicSessionId,
            String rollNumber, LocalDate startedOn) {
        if (startedOn == null) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "startedOn is required.");
        }
        RbacPrincipal principal = new RbacPrincipal(actor.tenantId(), actor.userAccountId());
        permissions.authorize(principal, "USER_CREATE", RbacScope.institution(institutionId), RbacTarget.NONE);

        StudentProfile student = students.findById(studentProfileId)
                .filter(row -> actor.tenantId().equals(row.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
        if (!institutionId.equals(student.getInstitutionId())) {
            throw new ResourceNotFoundException("Student not found in the requested institution.");
        }
        if (!"ACTIVE".equals(student.getStatus())) {
            throw new BusinessRuleViolationException("STUDENT_NOT_ACTIVE", "Only an active student can be enrolled.");
        }

        Section section = sections.findByTenantIdAndSectionId(actor.tenantId(), sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section not found."));
        if (!institutionId.equals(section.getInstitutionId())
                || !academicSessionId.equals(section.getAcademicSessionId())) {
            throw new ResourceNotFoundException("Section not found in the requested institution and academic session.");
        }
        if (!"ACTIVE".equals(section.getStatus())) {
            throw new BusinessRuleViolationException("SECTION_NOT_ACTIVE", "Students can only be enrolled in an active section.");
        }

        AcademicSession session = sessions.findByTenantIdAndAcademicSessionId(actor.tenantId(), academicSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic session not found."));
        if (session.getInstitutionId() != null && !institutionId.equals(session.getInstitutionId())) {
            throw new ResourceNotFoundException("Academic session not found in the requested institution.");
        }
        if (startedOn.isBefore(session.getStartDate()) || startedOn.isAfter(session.getEndDate())) {
            throw new BusinessRuleViolationException("ENROLLMENT_DATE_OUTSIDE_SESSION",
                    "startedOn must fall within the academic session.");
        }

        StudentEnrollment enrollment = new StudentEnrollment();
        enrollment.setId(new StudentEnrollmentId(actor.tenantId(), UUID.randomUUID()));
        enrollment.setStudentProfileId(studentProfileId);
        enrollment.setInstitutionId(institutionId);
        enrollment.setAcademicSessionId(academicSessionId);
        enrollment.setSectionId(sectionId);
        enrollment.setRollNumber(rollNumber == null || rollNumber.isBlank() ? null : rollNumber.trim());
        enrollment.setOutcome("IN_PROGRESS");
        enrollment.setStartedOn(startedOn);
        enrollment.setCreatedBy(actor.userAccountId());
        enrollment.setUpdatedBy(actor.userAccountId());
        return enrollments.save(enrollment);
    }
}

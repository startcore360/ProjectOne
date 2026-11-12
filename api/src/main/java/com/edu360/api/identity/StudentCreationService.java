package com.edu360.api.identity;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.institution.AcademicSession;
import com.edu360.api.institution.AcademicSessionRepository;
import com.edu360.api.institution.Institution;
import com.edu360.api.institution.InstitutionIdFormatService;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.institution.SchoolClass;
import com.edu360.api.institution.SchoolClassRepository;
import com.edu360.api.institution.Section;
import com.edu360.api.institution.SectionRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.dto.CreateUserResponse;

@Service
public class StudentCreationService {
    private final StudentProfileRepository students;
    private final InstitutionRepository institutions;
    private final AcademicSessionRepository sessions;
    private final SectionRepository sections;
    private final SchoolClassRepository schoolClasses;
    private final InstitutionIdFormatService idFormats;
    private final StudentEnrollmentService enrollments;
    private final UserAccountService accounts;
    private final AuditLogService audit;
    private final PermissionEvaluatorService permissions;

    @PersistenceContext
    private EntityManager entityManager;

    public StudentCreationService(StudentProfileRepository students, InstitutionRepository institutions,
            AcademicSessionRepository sessions, SectionRepository sections, SchoolClassRepository schoolClasses,
            InstitutionIdFormatService idFormats, StudentEnrollmentService enrollments,
            UserAccountService accounts, AuditLogService audit, PermissionEvaluatorService permissions) {
        this.students = students;
        this.institutions = institutions;
        this.sessions = sessions;
        this.sections = sections;
        this.schoolClasses = schoolClasses;
        this.idFormats = idFormats;
        this.enrollments = enrollments;
        this.accounts = accounts;
        this.audit = audit;
        this.permissions = permissions;
    }

    @Transactional
    public StudentProfileResponse create(TenantPrincipal actor, StudentCreateRequest request) {
        if (actor == null || request == null) throw new IllegalArgumentException("Actor and request are required.");
        if (request.fullName() == null || request.fullName().isBlank() || request.fullName().trim().length() > 150) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "fullName must contain 1 to 150 characters.");
        }
        UUID tenantId = actor.tenantId();
        UUID institutionId = request.institutionId();

        Institution institution = institutions.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found."));
        if (!"ACTIVE".equals(institution.getStatus())) {
            throw new BusinessRuleViolationException("INSTITUTION_NOT_ACTIVE",
                    "Students can only be created in an active institution.");
        }
        RbacPrincipal principal = new RbacPrincipal(tenantId, actor.userAccountId());
        permissions.authorize(principal, "USER_CREATE", RbacScope.institution(institutionId), RbacTarget.NONE);

        Section section = sections.findByTenantIdAndSectionId(tenantId, request.sectionId())
                .orElseThrow(() -> new ResourceNotFoundException("Section not found."));
        if (!institutionId.equals(section.getInstitutionId())
                || !request.academicSessionId().equals(section.getAcademicSessionId())) {
            throw new ResourceNotFoundException("Section not found in the requested institution and academic session.");
        }

        AcademicSession session = sessions.findByTenantIdAndAcademicSessionId(tenantId, request.academicSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Academic session not found."));
        if (session.getStartDate() == null) {
            throw new BusinessRuleViolationException("ACADEMIC_SESSION_INVALID",
                    "The academic session must have a start date to generate a student code.");
        }
        if (session.getInstitutionId() != null && !institutionId.equals(session.getInstitutionId())) {
            throw new ResourceNotFoundException("Academic session not found in the requested institution.");
        }
        if (!"ACTIVE".equals(session.getStatus())) {
            throw new BusinessRuleViolationException("ACADEMIC_SESSION_NOT_ACTIVE",
                    "Students can only be enrolled in an active academic session.");
        }

        if (!"ACTIVE".equals(section.getStatus())) {
            throw new BusinessRuleViolationException("SECTION_NOT_ACTIVE",
                    "Students can only be created in an active section.");
        }
        LocalDate admissionDate = request.admissionDate() == null ? LocalDate.now() : request.admissionDate();
        if (admissionDate.isBefore(session.getStartDate()) || admissionDate.isAfter(session.getEndDate())) {
            throw new BusinessRuleViolationException("ENROLLMENT_DATE_OUTSIDE_SESSION",
                    "admissionDate must fall within the academic session.");
        }

        boolean createLogin = resolveCreateLogin(institution, section, request.createLogin(), tenantId);
        String studentCode = idFormats.nextStudentCode(tenantId, institutionId, session.getStartDate().getYear());
        if (students.existsByTenantIdAndStudentCode(tenantId, studentCode)) {
            throw new DuplicateResourceException("Generated student code is already used in this tenant.");
        }

        UUID userAccountId = null;
        String temporaryPassword = null;
        if (createLogin) {
            CreateUserResponse account = accounts.createStudentAccount(principal,
                    request.fullName(), studentCode, institutionId);
            userAccountId = account.userAccountId();
            temporaryPassword = account.temporaryPassword();
        }

        UUID studentProfileId = UUID.randomUUID();
        StudentProfile profile = new StudentProfile(studentProfileId, tenantId, institutionId,
                userAccountId, studentCode, "ACTIVE");
        profile.setFullName(request.fullName().trim());
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setAdmissionDate(admissionDate);
        profile.setCreatedBy(actor.userAccountId());
        profile.setUpdatedBy(actor.userAccountId());
        StudentProfile saved = students.saveAndFlush(profile);
        entityManager.refresh(saved);

        enrollments.enrolExistingStudent(actor, studentProfileId, institutionId, request.sectionId(),
                request.academicSessionId(), null, admissionDate);

        audit.record(new AuditEntry(AuditEventType.STUDENT_PROFILE_CREATED, "USER_CREATE",
                "STUDENT_PROFILE", studentProfileId, institutionId, null,
                Map.of("student_code", studentCode,
                        "user_account_id", userAccountId == null ? "" : userAccountId.toString(),
                        "section_id", request.sectionId().toString()), 1, "P", "SUCCESS"));
        return StudentProfileResponse.from(saved, temporaryPassword);
    }

    private boolean resolveCreateLogin(Institution institution, Section section,
            Boolean requested, UUID tenantId) {
        String type = institution.getInstitutionType();
        if ("COLLEGE".equalsIgnoreCase(type)) {
            return requested == null || requested;
        }
        if ("SCHOOL".equalsIgnoreCase(type)) {
            if (section.getClassId() == null) {
                throw new BusinessRuleViolationException("SCHOOL_CLASS_REQUIRED",
                        "A school section must belong to a class.");
            }
            SchoolClass schoolClass = schoolClasses.findByTenantIdAndClassId(tenantId, section.getClassId())
                    .orElseThrow(() -> new ResourceNotFoundException("School class not found."));
            if (!institution.getInstitutionId().equals(schoolClass.getInstitutionId())
                    || !"ACTIVE".equals(schoolClass.getStatus())) {
                throw new ResourceNotFoundException("Active school class not found in the requested institution.");
            }
            boolean enabled = schoolClass.isStudentLoginEnabled();
            if (Boolean.TRUE.equals(requested) && !enabled) {
                throw new BusinessRuleViolationException("STUDENT_LOGIN_DISABLED",
                        "Student login is disabled for this school class.");
            }
            return requested == null ? enabled : requested && enabled;
        }
        throw new BusinessRuleViolationException("INSTITUTION_TYPE_UNSUPPORTED",
                "Student creation supports college and school institutions only.");
    }
}

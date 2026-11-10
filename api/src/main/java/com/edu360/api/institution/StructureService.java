package com.edu360.api.institution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.academic.ProgramRuleSet;
import com.edu360.api.academic.ProgramRuleSetRepository;
import com.edu360.api.academic.ProgramRuleSetResponse;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.institution.dto.AcademicSessionResponse;
import com.edu360.api.institution.dto.CourseOfferingResponse;
import com.edu360.api.institution.dto.CreateCourseOfferingRequest;
import com.edu360.api.institution.dto.CreateDepartmentRequest;
import com.edu360.api.institution.dto.CreateProgramRequest;
import com.edu360.api.institution.dto.CreateProgramYearRequest;
import com.edu360.api.institution.dto.CreateSchoolClassRequest;
import com.edu360.api.institution.dto.CreateSectionRequest;
import com.edu360.api.institution.dto.CreateSemesterRequest;
import com.edu360.api.institution.dto.CreateSubjectRequest;
import com.edu360.api.institution.dto.DepartmentResponse;
import com.edu360.api.institution.dto.ProgramResponse;
import com.edu360.api.institution.dto.ProgramYearResponse;
import com.edu360.api.institution.dto.SchoolClassResponse;
import com.edu360.api.institution.dto.SectionResponse;
import com.edu360.api.institution.dto.SemesterResponse;
import com.edu360.api.institution.dto.SubjectResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service orchestrating STRUCTURE_MANAGE (Action Code #26) operations:
 * - Managing academic departments (colleges only per Rule HI-12).
 * - Managing degree programs.
 * - Managing program years and semesters.
 * - Managing school classes and sections.
 * - Managing subjects and course offerings.
 * - Institution-scoped reads for academic sessions and program rule sets.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StructureService {

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final InstitutionRepository institutionRepository;
    private final DepartmentRepository departmentRepository;
    private final ProgramRepository programRepository;
    private final AuditLogService auditLogService;

    private ProgramYearRepository programYearRepository;
    private SemesterRepository semesterRepository;
    private SchoolClassRepository schoolClassRepository;
    private SectionRepository sectionRepository;
    private SubjectRepository subjectRepository;
    private CourseOfferingRepository courseOfferingRepository;
    private AcademicSessionRepository academicSessionRepository;
    private ProgramRuleSetRepository programRuleSetRepository;

    // Overloaded constructor for full dependency injection
    @org.springframework.beans.factory.annotation.Autowired
    public StructureService(
            PermissionEvaluatorService permissionEvaluatorService,
            InstitutionRepository institutionRepository,
            DepartmentRepository departmentRepository,
            ProgramRepository programRepository,
            AuditLogService auditLogService,
            ProgramYearRepository programYearRepository,
            SemesterRepository semesterRepository,
            SchoolClassRepository schoolClassRepository,
            SectionRepository sectionRepository,
            SubjectRepository subjectRepository,
            CourseOfferingRepository courseOfferingRepository,
            AcademicSessionRepository academicSessionRepository,
            ProgramRuleSetRepository programRuleSetRepository) {
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.institutionRepository = institutionRepository;
        this.departmentRepository = departmentRepository;
        this.programRepository = programRepository;
        this.auditLogService = auditLogService;
        this.programYearRepository = programYearRepository;
        this.semesterRepository = semesterRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.sectionRepository = sectionRepository;
        this.subjectRepository = subjectRepository;
        this.courseOfferingRepository = courseOfferingRepository;
        this.academicSessionRepository = academicSessionRepository;
        this.programRuleSetRepository = programRuleSetRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setProgramYearRepository(ProgramYearRepository repo) { this.programYearRepository = repo; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSemesterRepository(SemesterRepository repo) { this.semesterRepository = repo; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSchoolClassRepository(SchoolClassRepository repo) { this.schoolClassRepository = repo; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSectionRepository(SectionRepository repo) { this.sectionRepository = repo; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSubjectRepository(SubjectRepository repo) { this.subjectRepository = repo; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCourseOfferingRepository(CourseOfferingRepository repo) { this.courseOfferingRepository = repo; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAcademicSessionRepository(AcademicSessionRepository repo) { this.academicSessionRepository = repo; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setProgramRuleSetRepository(ProgramRuleSetRepository repo) { this.programRuleSetRepository = repo; }

    @Transactional
    public DepartmentResponse createDepartment(RbacPrincipal principal, UUID institutionId, CreateDepartmentRequest request) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);

        Institution institution = institutionRepository.findById(new InstitutionId(principal.tenantId(), institutionId))
                .orElseThrow(() -> new ResourceNotFoundException("Institution " + institutionId + " was not found"));

        if ("SCHOOL".equalsIgnoreCase(institution.getInstitutionType())) {
            log.warn("Rule HI-12 violation: attempted to create department in school institution {}", institutionId);
            throw new BusinessRuleViolationException(
                    "SCHOOL_CANNOT_HAVE_DEPARTMENTS",
                    "Schools do not have departments in v1 [Rule HI-12].");
        }

        String code = request.departmentCode().trim();
        if (departmentRepository.existsByTenantIdAndInstitutionIdAndDepartmentCode(principal.tenantId(), institutionId, code)) {
            throw new BusinessRuleViolationException(
                    "DUPLICATE_DEPARTMENT_CODE",
                    "A department with code '" + code + "' already exists in this institution.");
        }

        UUID deptId = UUID.randomUUID();
        Department dept = Department.builder()
                .departmentId(deptId)
                .tenantId(principal.tenantId())
                .institutionId(institutionId)
                .departmentCode(code)
                .departmentName(request.departmentName().trim())
                .status("ACTIVE")
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();
        Department saved = departmentRepository.save(dept);

        auditLogService.record(
                AuditEventType.DEPARTMENT_CREATED,
                null,
                code,
                "0.0.0.0",
                "Department '" + code + "' (" + deptId + ") created in institution " + institutionId);

        return mapDepartment(saved);
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> listDepartments(RbacPrincipal principal, UUID institutionId) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);
        return departmentRepository.findByTenantIdAndInstitutionId(principal.tenantId(), institutionId)
                .stream()
                .map(this::mapDepartment)
                .toList();
    }

    @Transactional
    public ProgramResponse createProgram(RbacPrincipal principal, UUID institutionId, CreateProgramRequest request) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);

        institutionRepository.findById(new InstitutionId(principal.tenantId(), institutionId))
                .orElseThrow(() -> new ResourceNotFoundException("Institution " + institutionId + " was not found"));

        Department dept = departmentRepository.findByTenantIdAndDepartmentId(principal.tenantId(), request.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department " + request.departmentId() + " was not found"));

        if (!institutionId.equals(dept.getInstitutionId())) {
            throw new BusinessRuleViolationException(
                    "DEPARTMENT_NOT_IN_INSTITUTION",
                    "Department " + request.departmentId() + " does not belong to institution " + institutionId);
        }

        String code = request.programCode().trim();
        if (programRepository.existsByTenantIdAndInstitutionIdAndProgramCode(principal.tenantId(), institutionId, code)) {
            throw new BusinessRuleViolationException(
                    "DUPLICATE_PROGRAM_CODE",
                    "A program with code '" + code + "' already exists in this institution.");
        }

        UUID progId = UUID.randomUUID();
        Program prog = Program.builder()
                .programId(progId)
                .tenantId(principal.tenantId())
                .institutionId(institutionId)
                .departmentId(request.departmentId())
                .programCode(code)
                .programName(request.programName().trim())
                .durationYears(request.durationYears())
                .semestersPerYear(request.effectiveSemestersPerYear())
                .status("ACTIVE")
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();
        Program saved = programRepository.save(prog);

        auditLogService.record(
                AuditEventType.PROGRAM_CREATED,
                null,
                code,
                "0.0.0.0",
                "Program '" + code + "' (" + progId + ") created in institution " + institutionId);

        return mapProgram(saved);
    }

    @Transactional(readOnly = true)
    public List<ProgramResponse> listPrograms(RbacPrincipal principal, UUID institutionId) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);
        return programRepository.findByTenantIdAndInstitutionId(principal.tenantId(), institutionId)
                .stream()
                .map(this::mapProgram)
                .toList();
    }

    @Transactional
    public ProgramYearResponse createProgramYear(RbacPrincipal principal, UUID institutionId, UUID programId, CreateProgramYearRequest request) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);

        Program program = programRepository.findByTenantIdAndProgramId(principal.tenantId(), programId)
                .orElseThrow(() -> new ResourceNotFoundException("Program " + programId + " was not found"));

        if (!institutionId.equals(program.getInstitutionId())) {
            throw new BusinessRuleViolationException("PROGRAM_NOT_IN_INSTITUTION", "Program does not belong to this institution.");
        }

        if (request.yearNumber() > program.getDurationYears()) {
            throw new BusinessRuleViolationException("INVALID_YEAR_NUMBER", "yearNumber cannot exceed program duration of " + program.getDurationYears() + " years.");
        }

        if (programYearRepository != null && programYearRepository.existsByTenantIdAndProgramIdAndYearNumber(principal.tenantId(), programId, request.yearNumber())) {
            throw new BusinessRuleViolationException("DUPLICATE_PROGRAM_YEAR", "Year " + request.yearNumber() + " already exists for this program.");
        }

        UUID yearId = UUID.randomUUID();
        ProgramYear py = ProgramYear.builder()
                .programYearId(yearId)
                .tenantId(principal.tenantId())
                .institutionId(institutionId)
                .programId(programId)
                .yearNumber(request.yearNumber())
                .label(request.label().trim())
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();

        if (programYearRepository != null) {
            py = programYearRepository.save(py);
        }

        auditLogService.record(
                AuditEventType.PROGRAM_YEAR_CREATED,
                null,
                py.getLabel(),
                "0.0.0.0",
                "ProgramYear '" + py.getLabel() + "' (" + yearId + ") created for program " + programId);

        return new ProgramYearResponse(py.getProgramYearId(), py.getInstitutionId(), py.getProgramId(), py.getYearNumber(), py.getLabel());
    }

    @Transactional(readOnly = true)
    public List<ProgramYearResponse> listProgramYears(RbacPrincipal principal, UUID institutionId, UUID programId) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);
        if (programYearRepository == null) return List.of();
        return programYearRepository.findByTenantIdAndProgramIdOrderByYearNumberAsc(principal.tenantId(), programId)
                .stream()
                .map(py -> new ProgramYearResponse(py.getProgramYearId(), py.getInstitutionId(), py.getProgramId(), py.getYearNumber(), py.getLabel()))
                .toList();
    }

    @Transactional
    public SemesterResponse createSemester(RbacPrincipal principal, UUID institutionId, UUID programYearId, CreateSemesterRequest request) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);

        ProgramYear py = programYearRepository != null
                ? programYearRepository.findByTenantIdAndProgramYearId(principal.tenantId(), programYearId)
                        .orElseThrow(() -> new ResourceNotFoundException("ProgramYear " + programYearId + " was not found"))
                : null;

        if (programYearRepository != null && semesterRepository != null && semesterRepository.existsByTenantIdAndProgramYearIdAndSemesterNumber(principal.tenantId(), programYearId, request.semesterNumber())) {
            throw new BusinessRuleViolationException("DUPLICATE_SEMESTER_NUMBER", "Semester " + request.semesterNumber() + " already exists for this program year.");
        }

        UUID semId = UUID.randomUUID();
        Semester semester = Semester.builder()
                .semesterId(semId)
                .tenantId(principal.tenantId())
                .institutionId(institutionId)
                .programYearId(programYearId)
                .semesterNumber(request.semesterNumber())
                .label(request.label().trim())
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();

        if (semesterRepository != null) {
            semester = semesterRepository.save(semester);
        }

        auditLogService.record(
                AuditEventType.SEMESTER_CREATED,
                null,
                semester.getLabel(),
                "0.0.0.0",
                "Semester '" + semester.getLabel() + "' (" + semId + ") created for programYear " + programYearId);

        return new SemesterResponse(semester.getSemesterId(), semester.getInstitutionId(), semester.getProgramYearId(), semester.getSemesterNumber(), semester.getLabel());
    }

    @Transactional(readOnly = true)
    public List<SemesterResponse> listSemesters(RbacPrincipal principal, UUID institutionId, UUID programYearId) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);
        if (semesterRepository == null) return List.of();
        return semesterRepository.findByTenantIdAndProgramYearIdOrderBySemesterNumberAsc(principal.tenantId(), programYearId)
                .stream()
                .map(s -> new SemesterResponse(s.getSemesterId(), s.getInstitutionId(), s.getProgramYearId(), s.getSemesterNumber(), s.getLabel()))
                .toList();
    }

    @Transactional
    public SchoolClassResponse createSchoolClass(RbacPrincipal principal, UUID institutionId, CreateSchoolClassRequest request) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);

        Institution institution = institutionRepository.findById(new InstitutionId(principal.tenantId(), institutionId))
                .orElseThrow(() -> new ResourceNotFoundException("Institution " + institutionId + " was not found"));

        if (!"SCHOOL".equalsIgnoreCase(institution.getInstitutionType())) {
            throw new BusinessRuleViolationException("COLLEGE_CANNOT_HAVE_CLASSES", "Only school institutions can have school classes.");
        }

        String name = request.className().trim();
        if (schoolClassRepository != null && schoolClassRepository.existsByTenantIdAndInstitutionIdAndClassName(principal.tenantId(), institutionId, name)) {
            throw new BusinessRuleViolationException("DUPLICATE_CLASS_NAME", "Class '" + name + "' already exists in this school.");
        }

        UUID classId = UUID.randomUUID();
        SchoolClass sc = SchoolClass.builder()
                .classId(classId)
                .tenantId(principal.tenantId())
                .institutionId(institutionId)
                .institutionType("SCHOOL")
                .className(name)
                .displayOrder(request.displayOrder())
                .studentLoginEnabled(false)
                .status("ACTIVE")
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();

        if (schoolClassRepository != null) {
            sc = schoolClassRepository.save(sc);
        }

        auditLogService.record(
                AuditEventType.SCHOOL_CLASS_CREATED,
                null,
                name,
                "0.0.0.0",
                "SchoolClass '" + name + "' (" + classId + ") created in school " + institutionId);

        return new SchoolClassResponse(sc.getClassId(), sc.getInstitutionId(), sc.getClassName(), sc.getDisplayOrder(), sc.isStudentLoginEnabled(), sc.getStatus());
    }

    @Transactional(readOnly = true)
    public List<SchoolClassResponse> listSchoolClasses(RbacPrincipal principal, UUID institutionId) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);
        if (schoolClassRepository == null) return List.of();
        return schoolClassRepository.findByTenantIdAndInstitutionIdOrderByDisplayOrderAsc(principal.tenantId(), institutionId)
                .stream()
                .map(sc -> new SchoolClassResponse(sc.getClassId(), sc.getInstitutionId(), sc.getClassName(), sc.getDisplayOrder(), sc.isStudentLoginEnabled(), sc.getStatus()))
                .toList();
    }

    @Transactional
    public SectionResponse createSection(RbacPrincipal principal, UUID institutionId, CreateSectionRequest request) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);

        boolean hasSem = request.semesterId() != null;
        boolean hasClass = request.classId() != null;
        if ((hasSem && hasClass) || (!hasSem && !hasClass)) {
            throw new BusinessRuleViolationException("INVALID_SECTION_PARENT", "Section must belong to either a semester or a school class, not both and not neither.");
        }

        String name = request.sectionName().trim();
        if (sectionRepository != null) {
            if (hasSem && sectionRepository.existsByTenantIdAndAcademicSessionIdAndSemesterIdAndSectionName(principal.tenantId(), request.academicSessionId(), request.semesterId(), name)) {
                throw new BusinessRuleViolationException("DUPLICATE_SECTION_NAME", "Section '" + name + "' already exists for this semester in this session.");
            }
            if (hasClass && sectionRepository.existsByTenantIdAndAcademicSessionIdAndClassIdAndSectionName(principal.tenantId(), request.academicSessionId(), request.classId(), name)) {
                throw new BusinessRuleViolationException("DUPLICATE_SECTION_NAME", "Section '" + name + "' already exists for this class in this session.");
            }
        }

        UUID secId = UUID.randomUUID();
        Section sec = Section.builder()
                .sectionId(secId)
                .tenantId(principal.tenantId())
                .institutionId(institutionId)
                .academicSessionId(request.academicSessionId())
                .semesterId(request.semesterId())
                .classId(request.classId())
                .sectionName(name)
                .status("ACTIVE")
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();

        if (sectionRepository != null) {
            sec = sectionRepository.save(sec);
        }

        auditLogService.record(
                AuditEventType.SECTION_CREATED,
                null,
                name,
                "0.0.0.0",
                "Section '" + name + "' (" + secId + ") created in institution " + institutionId);

        return new SectionResponse(sec.getSectionId(), sec.getInstitutionId(), sec.getAcademicSessionId(), sec.getSemesterId(), sec.getClassId(), sec.getSectionName(), sec.getStatus());
    }

    @Transactional(readOnly = true)
    public List<SectionResponse> listSections(RbacPrincipal principal, UUID institutionId, UUID academicSessionId, UUID semesterId, UUID classId) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);
        if (sectionRepository == null) return List.of();

        List<Section> list;
        if (semesterId != null) {
            list = sectionRepository.findByTenantIdAndInstitutionIdAndSemesterId(principal.tenantId(), institutionId, semesterId);
        } else if (classId != null) {
            list = sectionRepository.findByTenantIdAndInstitutionIdAndClassId(principal.tenantId(), institutionId, classId);
        } else if (academicSessionId != null) {
            list = sectionRepository.findByTenantIdAndInstitutionIdAndAcademicSessionId(principal.tenantId(), institutionId, academicSessionId);
        } else {
            list = sectionRepository.findByTenantIdAndInstitutionId(principal.tenantId(), institutionId);
        }

        return list.stream()
                .map(s -> new SectionResponse(s.getSectionId(), s.getInstitutionId(), s.getAcademicSessionId(), s.getSemesterId(), s.getClassId(), s.getSectionName(), s.getStatus()))
                .toList();
    }

    @Transactional
    public SubjectResponse createSubject(RbacPrincipal principal, UUID institutionId, CreateSubjectRequest request) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);

        String code = request.subjectCode().trim().toUpperCase();
        if (subjectRepository != null && subjectRepository.existsByTenantIdAndInstitutionIdAndSubjectCode(principal.tenantId(), institutionId, code)) {
            throw new BusinessRuleViolationException("DUPLICATE_SUBJECT_CODE", "Subject code '" + code + "' already exists in this institution.");
        }

        UUID subjId = UUID.randomUUID();
        Subject subj = Subject.builder()
                .subjectId(subjId)
                .tenantId(principal.tenantId())
                .institutionId(institutionId)
                .subjectCode(code)
                .subjectName(request.subjectName().trim())
                .credits(request.credits())
                .isElective(request.effectiveIsElective())
                .status("ACTIVE")
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();

        if (subjectRepository != null) {
            subj = subjectRepository.save(subj);
        }

        auditLogService.record(
                AuditEventType.SUBJECT_CREATED,
                null,
                code,
                "0.0.0.0",
                "Subject '" + code + "' (" + subjId + ") created in institution " + institutionId);

        return new SubjectResponse(subj.getSubjectId(), subj.getInstitutionId(), subj.getSubjectCode(), subj.getSubjectName(), subj.getCredits(), subj.isElective(), subj.getStatus());
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> listSubjects(RbacPrincipal principal, UUID institutionId) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);
        if (subjectRepository == null) return List.of();
        return subjectRepository.findByTenantIdAndInstitutionId(principal.tenantId(), institutionId)
                .stream()
                .map(s -> new SubjectResponse(s.getSubjectId(), s.getInstitutionId(), s.getSubjectCode(), s.getSubjectName(), s.getCredits(), s.isElective(), s.getStatus()))
                .toList();
    }

    @Transactional
    public CourseOfferingResponse createCourseOffering(RbacPrincipal principal, UUID institutionId, CreateCourseOfferingRequest request) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);

        boolean hasSem = request.semesterId() != null;
        boolean hasClass = request.classId() != null;
        if ((hasSem && hasClass) || (!hasSem && !hasClass)) {
            throw new BusinessRuleViolationException("INVALID_OFFERING_PARENT", "Course offering must specify either semesterId or classId, not both and not neither.");
        }

        if (courseOfferingRepository != null) {
            if (hasSem && courseOfferingRepository.existsByTenantIdAndSubjectIdAndAcademicSessionIdAndSemesterId(principal.tenantId(), request.subjectId(), request.academicSessionId(), request.semesterId())) {
                throw new BusinessRuleViolationException("DUPLICATE_COURSE_OFFERING", "Course offering already exists for this subject and semester in this session.");
            }
            if (hasClass && courseOfferingRepository.existsByTenantIdAndSubjectIdAndAcademicSessionIdAndClassId(principal.tenantId(), request.subjectId(), request.academicSessionId(), request.classId())) {
                throw new BusinessRuleViolationException("DUPLICATE_COURSE_OFFERING", "Course offering already exists for this subject and class in this session.");
            }
        }

        UUID offId = UUID.randomUUID();
        CourseOffering co = CourseOffering.builder()
                .courseOfferingId(offId)
                .tenantId(principal.tenantId())
                .institutionId(institutionId)
                .subjectId(request.subjectId())
                .academicSessionId(request.academicSessionId())
                .semesterId(request.semesterId())
                .classId(request.classId())
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();

        if (courseOfferingRepository != null) {
            co = courseOfferingRepository.save(co);
        }

        auditLogService.record(
                AuditEventType.COURSE_OFFERING_CREATED,
                null,
                request.subjectId().toString(),
                "0.0.0.0",
                "CourseOffering (" + offId + ") created in institution " + institutionId);

        return new CourseOfferingResponse(co.getCourseOfferingId(), co.getInstitutionId(), co.getSubjectId(), co.getAcademicSessionId(), co.getSemesterId(), co.getClassId());
    }

    @Transactional(readOnly = true)
    public List<CourseOfferingResponse> listCourseOfferings(RbacPrincipal principal, UUID institutionId, UUID academicSessionId) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);
        if (courseOfferingRepository == null) return List.of();

        List<CourseOffering> list = academicSessionId != null
                ? courseOfferingRepository.findByTenantIdAndInstitutionIdAndAcademicSessionId(principal.tenantId(), institutionId, academicSessionId)
                : courseOfferingRepository.findByTenantIdAndInstitutionId(principal.tenantId(), institutionId);

        return list.stream()
                .map(co -> new CourseOfferingResponse(co.getCourseOfferingId(), co.getInstitutionId(), co.getSubjectId(), co.getAcademicSessionId(), co.getSemesterId(), co.getClassId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AcademicSessionResponse> listAcademicSessions(RbacPrincipal principal, UUID institutionId) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);
        if (academicSessionRepository == null) return List.of();

        List<AcademicSession> sessions = academicSessionRepository.findByTenantIdAndInstitutionIdOrderByAcademicYearDesc(principal.tenantId(), institutionId);
        return sessions.stream()
                .map(s -> new AcademicSessionResponse(
                        s.getAcademicSessionId(),
                        s.getTenantId(),
                        s.getInstitutionId(),
                        s.getSessionLabel(),
                        s.getStartDate(),
                        s.getEndDate(),
                        s.getAcademicYear(),
                        s.getStatus(),
                        s.getRowVersion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProgramRuleSetResponse> listProgramRuleSets(RbacPrincipal principal, UUID institutionId) {
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE", structureScope(principal, institutionId), RbacTarget.NONE);
        if (programRuleSetRepository == null) return List.of();

        return programRuleSetRepository.findByIdTenantIdAndInstitutionId(principal.tenantId(), institutionId)
                .stream()
                .map(ProgramRuleSetResponse::from)
                .toList();
    }

    private DepartmentResponse mapDepartment(Department d) {
        return new DepartmentResponse(
                d.getDepartmentId(),
                d.getInstitutionId(),
                d.getDepartmentCode(),
                d.getDepartmentName(),
                d.getHodStaffAssignmentId(),
                d.getStatus());
    }

    private ProgramResponse mapProgram(Program p) {
        return new ProgramResponse(
                p.getProgramId(),
                p.getInstitutionId(),
                p.getDepartmentId(),
                p.getProgramCode(),
                p.getProgramName(),
                p.getDurationYears(),
                p.getSemestersPerYear(),
                p.getStatus());
    }

    /**
     * A-05: STRUCTURE_MANAGE is checked at the scope D3 gives the caller's active role - the
     * Registrar (and other university-level holders) at the university, a College/School Admin or
     * Principal at the target institution. Falls back to the institution scope only if the
     * evaluator gives none.
     */
    private RbacScope structureScope(RbacPrincipal principal, UUID institutionId) {
        return java.util.Objects.requireNonNullElse(
                permissionEvaluatorService.targetScope(principal, institutionId, null),
                RbacScope.institution(institutionId));
    }
}

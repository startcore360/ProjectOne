package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.identity.UserType;
import com.edu360.api.institution.dto.CreateDepartmentRequest;
import com.edu360.api.institution.dto.CreateProgramRequest;
import com.edu360.api.institution.dto.CreateSchoolClassRequest;
import com.edu360.api.institution.dto.UpdateSectionRequest;
import com.edu360.api.institution.dto.CreateSemesterRequest;
import com.edu360.api.institution.dto.DepartmentResponse;
import com.edu360.api.institution.dto.ProgramResponse;
import com.edu360.api.institution.dto.SemesterResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

@ExtendWith(MockitoExtension.class)
class StructureControllerTest {

    @Mock private PermissionEvaluatorService permissionEvaluatorService;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private ProgramRepository programRepository;
    @Mock private ProgramYearRepository programYearRepository;
    @Mock private SemesterRepository semesterRepository;
    @Mock private SchoolClassRepository schoolClassRepository;
    @Mock private SectionRepository sectionRepository;
    @Mock private AcademicSessionRepository academicSessionRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private Authentication authentication;

    private StructureService structureService;
    private StructureController structureController;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COLLEGE_ID = UUID.randomUUID();
    private static final UUID SCHOOL_ID = UUID.randomUUID();
    private static final UUID DEPT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        structureService = new StructureService(
                permissionEvaluatorService,
                institutionRepository,
                departmentRepository,
                programRepository,
                auditLogService);
        structureService.setProgramYearRepository(programYearRepository);
        structureService.setSemesterRepository(semesterRepository);
        structureService.setSchoolClassRepository(schoolClassRepository);
        structureService.setSectionRepository(sectionRepository);
        structureService.setAcademicSessionRepository(academicSessionRepository);
        structureController = new StructureController(structureService);
    }

    private void mockPrincipal(UUID tenantId) {
        TenantPrincipal principal = new TenantPrincipal(tenantId, USER_ID, UserType.STAFF, "College Admin", false);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    // ----------------------------------------------------------------- ALLOW
    @Test
    void createDepartment_allowsWhenAuthorizedInCollegeInstitution() {
        mockPrincipal(TENANT_ID);

        Institution college = Institution.builder()
                .id(new InstitutionId(TENANT_ID, COLLEGE_ID))
                .institutionType("COLLEGE")
                .institutionName("College of Engineering")
                .status("ACTIVE")
                .build();
        when(institutionRepository.findById(new InstitutionId(TENANT_ID, COLLEGE_ID)))
                .thenReturn(Optional.of(college));
        when(departmentRepository.existsByTenantIdAndInstitutionIdAndDepartmentCode(TENANT_ID, COLLEGE_ID, "CSE"))
                .thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateDepartmentRequest request = new CreateDepartmentRequest("CSE", "Computer Science and Engineering");

        ResponseEntity<DepartmentResponse> response = structureController.createDepartment(
                TENANT_ID, COLLEGE_ID, request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().departmentCode()).isEqualTo("CSE");
        assertThat(response.getBody().departmentName()).isEqualTo("Computer Science and Engineering");
        assertThat(response.getBody().status()).isEqualTo("ACTIVE");

        verify(permissionEvaluatorService).authorize(any(), eq("STRUCTURE_MANAGE"), eq(RbacScope.institution(COLLEGE_ID)), eq(RbacTarget.NONE));
        verify(auditLogService).record(eq(AuditEventType.DEPARTMENT_CREATED), any(), eq("CSE"), any(), any());
    }

    @Test
    void createProgram_allowsWhenAuthorizedInCollegeDepartment() {
        mockPrincipal(TENANT_ID);

        Institution college = Institution.builder()
                .id(new InstitutionId(TENANT_ID, COLLEGE_ID))
                .institutionType("COLLEGE")
                .institutionName("College of Engineering")
                .status("ACTIVE")
                .build();
        when(institutionRepository.findById(new InstitutionId(TENANT_ID, COLLEGE_ID)))
                .thenReturn(Optional.of(college));

        Department dept = Department.builder()
                .departmentId(DEPT_ID)
                .tenantId(TENANT_ID)
                .institutionId(COLLEGE_ID)
                .departmentCode("CSE")
                .departmentName("Computer Science")
                .status("ACTIVE")
                .build();
        when(departmentRepository.findByTenantIdAndDepartmentId(TENANT_ID, DEPT_ID))
                .thenReturn(Optional.of(dept));
        when(programRepository.existsByTenantIdAndInstitutionIdAndProgramCode(TENANT_ID, COLLEGE_ID, "BTECH_CSE"))
                .thenReturn(false);
        when(programRepository.save(any(Program.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateProgramRequest request = new CreateProgramRequest(DEPT_ID, "BTECH_CSE", "B.Tech Computer Science", (short) 4, (short) 2);

        ResponseEntity<ProgramResponse> response = structureController.createProgram(
                TENANT_ID, COLLEGE_ID, request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().programCode()).isEqualTo("BTECH_CSE");
        assertThat(response.getBody().durationYears()).isEqualTo((short) 4);
        assertThat(response.getBody().semestersPerYear()).isEqualTo((short) 2);

        verify(auditLogService).record(eq(AuditEventType.PROGRAM_CREATED), any(), eq("BTECH_CSE"), any(), any());
    }

    @Test
    void createSemester_persistsDateRangeAndRejectsInstitutionWideOverlap() {
        mockPrincipal(TENANT_ID);
        UUID programId = UUID.randomUUID();
        UUID programYearId = UUID.randomUUID();
        ProgramYear programYear = ProgramYear.builder()
                .programYearId(programYearId)
                .tenantId(TENANT_ID)
                .institutionId(COLLEGE_ID)
                .programId(programId)
                .yearNumber((short) 1)
                .label("Year 1")
                .build();
        when(programYearRepository.findByTenantIdAndProgramYearId(TENANT_ID, programYearId))
                .thenReturn(Optional.of(programYear));
        when(semesterRepository.existsByTenantIdAndProgramYearIdAndSemesterNumber(TENANT_ID, programYearId, (short) 1))
                .thenReturn(false);
        when(semesterRepository.existsOverlappingInstitutionTerm(
                TENANT_ID, COLLEGE_ID, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-11-30")))
                .thenReturn(false);
        when(semesterRepository.save(any(Semester.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateSemesterRequest request = new CreateSemesterRequest(
                (short) 1, "Semester 1", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-11-30"));

        ResponseEntity<SemesterResponse> response = structureController.createSemester(
                TENANT_ID, COLLEGE_ID, programYearId, request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().startDate()).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(response.getBody().endDate()).isEqualTo(LocalDate.parse("2026-11-30"));
        ArgumentCaptor<Semester> saved = ArgumentCaptor.forClass(Semester.class);
        verify(semesterRepository).save(saved.capture());
        assertThat(saved.getValue().getStartDate()).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(saved.getValue().getEndDate()).isEqualTo(LocalDate.parse("2026-11-30"));
        verify(permissionEvaluatorService).authorize(any(), eq("STRUCTURE_MANAGE"),
                eq(RbacScope.institution(COLLEGE_ID)), eq(RbacTarget.NONE));
    }

    @Test
    void createSemester_rejectsDateRangeOverlappingAnotherProgramInInstitution() {
        mockPrincipal(TENANT_ID);
        UUID programYearId = UUID.randomUUID();
        ProgramYear programYear = ProgramYear.builder()
                .programYearId(programYearId)
                .tenantId(TENANT_ID)
                .institutionId(COLLEGE_ID)
                .programId(UUID.randomUUID())
                .yearNumber((short) 1)
                .label("Year 1")
                .build();
        when(programYearRepository.findByTenantIdAndProgramYearId(TENANT_ID, programYearId))
                .thenReturn(Optional.of(programYear));
        when(semesterRepository.existsByTenantIdAndProgramYearIdAndSemesterNumber(TENANT_ID, programYearId, (short) 1))
                .thenReturn(false);
        when(semesterRepository.existsOverlappingInstitutionTerm(
                TENANT_ID, COLLEGE_ID, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-12-15")))
                .thenReturn(true);

        CreateSemesterRequest request = new CreateSemesterRequest(
                (short) 1, "Semester 1", LocalDate.parse("2026-08-01"), LocalDate.parse("2026-12-15"));

        assertThrows(BusinessRuleViolationException.class, () ->
                structureController.createSemester(TENANT_ID, COLLEGE_ID, programYearId, request, authentication));
        verify(semesterRepository, never()).save(any(Semester.class));
    }

    @Test
    void createSemester_rejectsEndDateBeforeStartDate() {
        mockPrincipal(TENANT_ID);
        UUID programYearId = UUID.randomUUID();
        ProgramYear programYear = ProgramYear.builder()
                .programYearId(programYearId)
                .tenantId(TENANT_ID)
                .institutionId(COLLEGE_ID)
                .programId(UUID.randomUUID())
                .yearNumber((short) 1)
                .label("Year 1")
                .build();
        when(programYearRepository.findByTenantIdAndProgramYearId(TENANT_ID, programYearId))
                .thenReturn(Optional.of(programYear));

        CreateSemesterRequest request = new CreateSemesterRequest(
                (short) 1, "Semester 1", LocalDate.parse("2026-12-15"), LocalDate.parse("2026-08-01"));

        assertThrows(BusinessRuleViolationException.class, () ->
                structureController.createSemester(TENANT_ID, COLLEGE_ID, programYearId, request, authentication));
        verify(semesterRepository, never()).save(any(Semester.class));
    }

    @Test
    void updateDepartment_usesIfMatchAndReturnsFacultyStudentCounts() {
        mockPrincipal(TENANT_ID);
        Department current = Department.builder()
                .departmentId(DEPT_ID).tenantId(TENANT_ID).institutionId(COLLEGE_ID)
                .departmentCode("CSE").departmentName("Computer Science").status("ACTIVE").rowVersion(2).build();
        Department updated = Department.builder()
                .departmentId(DEPT_ID).tenantId(TENANT_ID).institutionId(COLLEGE_ID)
                .departmentCode("CSE").departmentName("Computer Science and Engineering").status("ACTIVE").rowVersion(3).build();
        when(departmentRepository.findByTenantIdAndDepartmentId(TENANT_ID, DEPT_ID))
                .thenReturn(Optional.of(current), Optional.of(updated));
        when(departmentRepository.existsByTenantIdAndInstitutionIdAndDepartmentCodeAndDepartmentIdNot(
                TENANT_ID, COLLEGE_ID, "CSE", DEPT_ID)).thenReturn(false);
        when(departmentRepository.updateProfile(TENANT_ID, DEPT_ID, 2, "CSE",
                "Computer Science and Engineering", USER_ID)).thenReturn(1);
        when(departmentRepository.countActiveFaculty(TENANT_ID, DEPT_ID)).thenReturn(7L);
        when(departmentRepository.countCurrentStudents(TENANT_ID, DEPT_ID)).thenReturn(420L);

        ResponseEntity<DepartmentResponse> response = structureController.updateDepartment(
                TENANT_ID, COLLEGE_ID, DEPT_ID, "\"2\"",
                new CreateDepartmentRequest("CSE", "Computer Science and Engineering"), authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"3\"");
        assertThat(response.getBody().departmentName()).isEqualTo("Computer Science and Engineering");
        assertThat(response.getBody().facultyCount()).isEqualTo(7);
        assertThat(response.getBody().studentCount()).isEqualTo(420);
        verify(permissionEvaluatorService).authorize(any(), eq("STRUCTURE_MANAGE"),
                eq(RbacScope.institution(COLLEGE_ID)), eq(RbacTarget.NONE));
    }

    @Test
    void updateDepartment_rejectsStaleIfMatchWithoutWriting() {
        mockPrincipal(TENANT_ID);
        Department current = Department.builder()
                .departmentId(DEPT_ID).tenantId(TENANT_ID).institutionId(COLLEGE_ID)
                .departmentCode("CSE").departmentName("Computer Science").status("ACTIVE").rowVersion(2).build();
        when(departmentRepository.findByTenantIdAndDepartmentId(TENANT_ID, DEPT_ID)).thenReturn(Optional.of(current));

        assertThrows(BusinessRuleViolationException.class, () -> structureController.updateDepartment(
                TENANT_ID, COLLEGE_ID, DEPT_ID, "\"1\"",
                new CreateDepartmentRequest("CSE", "Changed name"), authentication));
        verify(departmentRepository, never()).updateProfile(any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                any(), any(), any());
    }

    @Test
    void archiveDepartment_usesIfMatchAndRefusesActivePrograms() {
        mockPrincipal(TENANT_ID);
        Department current = Department.builder()
                .departmentId(DEPT_ID).tenantId(TENANT_ID).institutionId(COLLEGE_ID)
                .departmentCode("CSE").departmentName("Computer Science").status("ACTIVE").rowVersion(2).build();
        when(departmentRepository.findByTenantIdAndDepartmentId(TENANT_ID, DEPT_ID)).thenReturn(Optional.of(current));
        when(programRepository.existsByTenantIdAndDepartmentIdAndStatus(TENANT_ID, DEPT_ID, "ACTIVE")).thenReturn(true);

        assertThrows(BusinessRuleViolationException.class, () -> structureController.archiveDepartment(
                TENANT_ID, COLLEGE_ID, DEPT_ID, "\"2\"", authentication));
        verify(departmentRepository, never()).archive(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void updateProgram_usesIfMatchAndValidatesDepartmentScope() {
        mockPrincipal(TENANT_ID);
        UUID programId = UUID.randomUUID();
        Program current = Program.builder().programId(programId).tenantId(TENANT_ID)
                .institutionId(COLLEGE_ID).departmentId(DEPT_ID).programCode("BTECH_CSE")
                .programName("B.Tech CSE").durationYears((short) 4).semestersPerYear((short) 2)
                .degreeLevel("UG").totalCredits(new java.math.BigDecimal("160"))
                .curriculumVersion(1).status("ACTIVE").rowVersion(2).build();
        Program updated = Program.builder().programId(programId).tenantId(TENANT_ID)
                .institutionId(COLLEGE_ID).departmentId(DEPT_ID).programCode("BTECH_CSE")
                .programName("B.Tech Computer Science").durationYears((short) 4).semestersPerYear((short) 2)
                .degreeLevel("UG").totalCredits(new java.math.BigDecimal("160"))
                .curriculumVersion(1).status("ACTIVE").rowVersion(3).build();
        Department department = Department.builder().departmentId(DEPT_ID).tenantId(TENANT_ID)
                .institutionId(COLLEGE_ID).departmentCode("CSE").departmentName("Computer Science").status("ACTIVE").build();
        when(programRepository.findByTenantIdAndProgramId(TENANT_ID, programId))
                .thenReturn(Optional.of(current), Optional.of(updated));
        when(departmentRepository.findByTenantIdAndDepartmentId(TENANT_ID, DEPT_ID)).thenReturn(Optional.of(department));
        when(programRepository.existsByTenantIdAndInstitutionIdAndProgramCodeAndProgramIdNot(
                TENANT_ID, COLLEGE_ID, "BTECH_CSE", programId)).thenReturn(false);
        when(programRepository.updateProfile(TENANT_ID, programId, 2, DEPT_ID, "BTECH_CSE",
                "B.Tech Computer Science", (short) 4, (short) 2, "UG",
                new java.math.BigDecimal("160"), USER_ID)).thenReturn(1);

        ResponseEntity<ProgramResponse> response = structureController.updateProgram(
                TENANT_ID, COLLEGE_ID, programId, "\"2\"",
                new CreateProgramRequest(DEPT_ID, "BTECH_CSE", "B.Tech Computer Science",
                        (short) 4, (short) 2, "UG", new java.math.BigDecimal("160")), authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"3\"");
        assertThat(response.getBody().programName()).isEqualTo("B.Tech Computer Science");
        assertThat(response.getBody().rowVersion()).isEqualTo(3);
    }

    @Test
    void updateSchoolClass_usesIfMatchAndReturnsNewRowVersion() {
        mockPrincipal(TENANT_ID);
        UUID classId = UUID.randomUUID();
        SchoolClass current = SchoolClass.builder().classId(classId).tenantId(TENANT_ID)
                .institutionId(SCHOOL_ID).className("Grade 8").displayOrder((short) 8)
                .studentLoginEnabled(true).status("ACTIVE").rowVersion(2).build();
        SchoolClass updated = SchoolClass.builder().classId(classId).tenantId(TENANT_ID)
                .institutionId(SCHOOL_ID).className("Grade VIII").displayOrder((short) 8)
                .studentLoginEnabled(true).status("ACTIVE").rowVersion(3).build();
        when(schoolClassRepository.findByTenantIdAndClassId(TENANT_ID, classId))
                .thenReturn(Optional.of(current), Optional.of(updated));
        when(schoolClassRepository.existsByTenantIdAndInstitutionIdAndClassNameAndClassIdNot(
                TENANT_ID, SCHOOL_ID, "Grade VIII", classId)).thenReturn(false);
        when(schoolClassRepository.updateProfile(TENANT_ID, classId, 2, "Grade VIII", (short) 8, true, USER_ID))
                .thenReturn(1);

        ResponseEntity<SchoolClassResponse> response = structureController.updateSchoolClass(
                TENANT_ID, SCHOOL_ID, classId, "\"2\"",
                new CreateSchoolClassRequest("Grade VIII", (short) 8), authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"3\"");
        assertThat(response.getBody().className()).isEqualTo("Grade VIII");
        assertThat(response.getBody().rowVersion()).isEqualTo(3);
    }

    @Test
    void updateSection_usesIfMatchAndValidatesSchoolClassScope() {
        mockPrincipal(TENANT_ID);
        UUID sectionId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Section current = Section.builder().sectionId(sectionId).tenantId(TENANT_ID)
                .institutionId(SCHOOL_ID).academicSessionId(sessionId).classId(classId)
                .sectionName("A").capacity(45).status("ACTIVE").rowVersion(2).build();
        Section updated = Section.builder().sectionId(sectionId).tenantId(TENANT_ID)
                .institutionId(SCHOOL_ID).academicSessionId(sessionId).classId(classId)
                .sectionName("B").capacity(60).status("ACTIVE").rowVersion(3).build();
        AcademicSession session = new AcademicSession();
        session.setAcademicSessionId(sessionId);
        session.setTenantId(TENANT_ID);
        session.setInstitutionId(SCHOOL_ID);
        SchoolClass schoolClass = SchoolClass.builder().classId(classId).tenantId(TENANT_ID)
                .institutionId(SCHOOL_ID).className("Grade 8").status("ACTIVE").build();
        Institution school = Institution.builder()
                .id(new InstitutionId(TENANT_ID, SCHOOL_ID)).institutionType("SCHOOL")
                .institutionName("Northbridge School").status("ACTIVE").build();
        when(sectionRepository.findByTenantIdAndSectionId(TENANT_ID, sectionId))
                .thenReturn(Optional.of(current), Optional.of(updated));
        when(academicSessionRepository.findByTenantIdAndAcademicSessionId(TENANT_ID, sessionId))
                .thenReturn(Optional.of(session));
        when(schoolClassRepository.findByTenantIdAndClassId(TENANT_ID, classId)).thenReturn(Optional.of(schoolClass));
        when(institutionRepository.findById(new InstitutionId(TENANT_ID, SCHOOL_ID))).thenReturn(Optional.of(school));
        when(sectionRepository.existsByTenantIdAndAcademicSessionIdAndClassIdAndSectionNameAndSectionIdNot(
                TENANT_ID, sessionId, classId, "B", sectionId)).thenReturn(false);
        when(sectionRepository.updateProfile(TENANT_ID, sectionId, 2, sessionId, null, classId, "B", 60, USER_ID))
                .thenReturn(1);

        ResponseEntity<SectionResponse> response = structureController.updateSection(
                TENANT_ID, SCHOOL_ID, sectionId, "\"2\"",
                new UpdateSectionRequest(sessionId, null, classId, "B", 60), authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"3\"");
        assertThat(response.getBody().sectionName()).isEqualTo("B");
        assertThat(response.getBody().capacity()).isEqualTo(60);
        assertThat(response.getBody().rowVersion()).isEqualTo(3);
    }

    @Test
    void listDepartments_and_listPrograms_returnExistingEntities() {
        mockPrincipal(TENANT_ID);

        Department dept = Department.builder()
                .departmentId(DEPT_ID)
                .tenantId(TENANT_ID)
                .institutionId(COLLEGE_ID)
                .departmentCode("ECE")
                .departmentName("Electronics")
                .status("ACTIVE")
                .build();
        when(departmentRepository.findByTenantIdAndInstitutionId(TENANT_ID, COLLEGE_ID))
                .thenReturn(List.of(dept));

        Program prog = Program.builder()
                .programId(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .institutionId(COLLEGE_ID)
                .departmentId(DEPT_ID)
                .programCode("BTECH_ECE")
                .programName("B.Tech ECE")
                .durationYears((short) 4)
                .semestersPerYear((short) 2)
                .status("ACTIVE")
                .build();
        when(programRepository.findByTenantIdAndInstitutionId(TENANT_ID, COLLEGE_ID))
                .thenReturn(List.of(prog));

        ResponseEntity<List<DepartmentResponse>> deptListResp = structureController.listDepartments(TENANT_ID, COLLEGE_ID, authentication);
        assertThat(deptListResp.getBody()).hasSize(1);
        assertThat(deptListResp.getBody().getFirst().departmentCode()).isEqualTo("ECE");

        ResponseEntity<List<ProgramResponse>> progListResp = structureController.listPrograms(TENANT_ID, COLLEGE_ID, authentication);
        assertThat(progListResp.getBody()).hasSize(1);
        assertThat(progListResp.getBody().getFirst().programCode()).isEqualTo("BTECH_ECE");
    }

    // ------------------------------------------------------------------ DENY
    @Test
    void createDepartment_deniesWhenPermissionEvaluatorThrowsAccessDenied() {
        mockPrincipal(TENANT_ID);
        org.mockito.Mockito.doThrow(new AccessDeniedException("Forbidden"))
                .when(permissionEvaluatorService)
                .authorize(any(), eq("STRUCTURE_MANAGE"), any(), any());

        CreateDepartmentRequest request = new CreateDepartmentRequest("CSE", "Computer Science");

        assertThrows(AccessDeniedException.class, () ->
                structureController.createDepartment(TENANT_ID, COLLEGE_ID, request, authentication));

        verify(departmentRepository, never()).save(any());
    }

    // ----------------------------------------------------------------- GUARD
    @Test
    void createDepartment_rejectsDepartmentCreationForSchools_RuleHI12() {
        mockPrincipal(TENANT_ID);

        Institution school = Institution.builder()
                .id(new InstitutionId(TENANT_ID, SCHOOL_ID))
                .institutionType("SCHOOL")
                .institutionName("Greenwood High School")
                .status("ACTIVE")
                .build();
        when(institutionRepository.findById(new InstitutionId(TENANT_ID, SCHOOL_ID)))
                .thenReturn(Optional.of(school));

        CreateDepartmentRequest request = new CreateDepartmentRequest("CSE", "Computer Science");

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                structureController.createDepartment(TENANT_ID, SCHOOL_ID, request, authentication));

        assertThat(ex.getErrorCode()).isEqualTo("SCHOOL_CANNOT_HAVE_DEPARTMENTS");
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void createDepartment_rejectsDuplicateDepartmentCode() {
        mockPrincipal(TENANT_ID);

        Institution college = Institution.builder()
                .id(new InstitutionId(TENANT_ID, COLLEGE_ID))
                .institutionType("COLLEGE")
                .institutionName("College of Engineering")
                .status("ACTIVE")
                .build();
        when(institutionRepository.findById(new InstitutionId(TENANT_ID, COLLEGE_ID)))
                .thenReturn(Optional.of(college));
        when(departmentRepository.existsByTenantIdAndInstitutionIdAndDepartmentCode(TENANT_ID, COLLEGE_ID, "CSE"))
                .thenReturn(true);

        CreateDepartmentRequest request = new CreateDepartmentRequest("CSE", "Computer Science");

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                structureController.createDepartment(TENANT_ID, COLLEGE_ID, request, authentication));

        assertThat(ex.getErrorCode()).isEqualTo("DUPLICATE_DEPARTMENT_CODE");
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void createProgram_rejectsDuplicateProgramCode() {
        mockPrincipal(TENANT_ID);

        Institution college = Institution.builder()
                .id(new InstitutionId(TENANT_ID, COLLEGE_ID))
                .institutionType("COLLEGE")
                .institutionName("College of Engineering")
                .status("ACTIVE")
                .build();
        when(institutionRepository.findById(new InstitutionId(TENANT_ID, COLLEGE_ID)))
                .thenReturn(Optional.of(college));

        Department dept = Department.builder()
                .departmentId(DEPT_ID)
                .tenantId(TENANT_ID)
                .institutionId(COLLEGE_ID)
                .departmentCode("CSE")
                .departmentName("Computer Science")
                .status("ACTIVE")
                .build();
        when(departmentRepository.findByTenantIdAndDepartmentId(TENANT_ID, DEPT_ID))
                .thenReturn(Optional.of(dept));
        when(programRepository.existsByTenantIdAndInstitutionIdAndProgramCode(TENANT_ID, COLLEGE_ID, "BTECH_CSE"))
                .thenReturn(true);

        CreateProgramRequest request = new CreateProgramRequest(DEPT_ID, "BTECH_CSE", "B.Tech Computer Science", (short) 4, (short) 2);

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                structureController.createProgram(TENANT_ID, COLLEGE_ID, request, authentication));

        assertThat(ex.getErrorCode()).isEqualTo("DUPLICATE_PROGRAM_CODE");
        verify(programRepository, never()).save(any());
    }

    @Test
    void createProgram_rejectsDepartmentNotBelongingToInstitution() {
        mockPrincipal(TENANT_ID);

        Institution college = Institution.builder()
                .id(new InstitutionId(TENANT_ID, COLLEGE_ID))
                .institutionType("COLLEGE")
                .institutionName("College of Engineering")
                .status("ACTIVE")
                .build();
        when(institutionRepository.findById(new InstitutionId(TENANT_ID, COLLEGE_ID)))
                .thenReturn(Optional.of(college));

        UUID otherCollegeId = UUID.randomUUID();
        Department deptFromOtherCollege = Department.builder()
                .departmentId(DEPT_ID)
                .tenantId(TENANT_ID)
                .institutionId(otherCollegeId)
                .departmentCode("CSE")
                .departmentName("Computer Science")
                .status("ACTIVE")
                .build();
        when(departmentRepository.findByTenantIdAndDepartmentId(TENANT_ID, DEPT_ID))
                .thenReturn(Optional.of(deptFromOtherCollege));

        CreateProgramRequest request = new CreateProgramRequest(DEPT_ID, "BTECH_CSE", "B.Tech Computer Science", (short) 4, (short) 2);

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                structureController.createProgram(TENANT_ID, COLLEGE_ID, request, authentication));

        assertThat(ex.getErrorCode()).isEqualTo("DEPARTMENT_NOT_IN_INSTITUTION");
        verify(programRepository, never()).save(any());
    }
}

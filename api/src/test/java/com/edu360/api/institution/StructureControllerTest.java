package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.edu360.api.institution.dto.DepartmentResponse;
import com.edu360.api.institution.dto.ProgramResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

@ExtendWith(MockitoExtension.class)
class StructureControllerTest {

    @Mock private PermissionEvaluatorService permissionEvaluatorService;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private ProgramRepository programRepository;
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

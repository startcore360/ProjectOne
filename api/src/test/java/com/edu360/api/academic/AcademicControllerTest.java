package com.edu360.api.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.identity.StudentProfile;
import com.edu360.api.identity.StudentProfileRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.PermissionEvaluatorService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AcademicControllerTest {

    @Mock
    private ProgramRuleSetRepository programRuleSetRepository;
    @Mock
    private GradingSchemeVersionRepository gradingSchemeVersionRepository;
    @Mock
    private SubjectAllocationRepository subjectAllocationRepository;
    @Mock
    private PromotionRunRepository promotionRunRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private PermissionEvaluatorService permissionEvaluatorService;
    @Mock
    private AuditLogService auditLogService;

    private ProgramRuleController programRuleController;
    private GradingSchemeController gradingSchemeController;
    private SubjectAllocationController subjectAllocationController;
    private PromotionController promotionController;
    private StudentStatusController studentStatusController;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID institutionId = UUID.randomUUID();
    private Authentication auth;

    @BeforeEach
    void setUp() {
        ProgramRuleService programRuleService = new ProgramRuleService(programRuleSetRepository, permissionEvaluatorService, auditLogService);
        programRuleController = new ProgramRuleController(programRuleService);

        GradingSchemeService gradingSchemeService = new GradingSchemeService(gradingSchemeVersionRepository, permissionEvaluatorService, auditLogService);
        gradingSchemeController = new GradingSchemeController(gradingSchemeService);

        SubjectAllocationService subjectAllocationService = new SubjectAllocationService(subjectAllocationRepository, permissionEvaluatorService, auditLogService);
        subjectAllocationController = new SubjectAllocationController(subjectAllocationService);

        PromotionService promotionService = new PromotionService(promotionRunRepository, permissionEvaluatorService, auditLogService);
        promotionController = new PromotionController(promotionService);

        StudentStatusService studentStatusService = new StudentStatusService(studentProfileRepository, permissionEvaluatorService, auditLogService);
        studentStatusController = new StudentStatusController(studentStatusService);

        TenantPrincipal principal = new TenantPrincipal(
                tenantId,
                userId,
                UserType.STAFF,
                "Academic Registrar",
                false
        );
        auth = new UsernamePasswordAuthenticationToken(principal, null);
    }

    // ---------------------------------------------------------------------------------------------
    // Story #30: PROGRAM_RULES_SET
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("ACT-PROGRAM_RULES_SET-ALLOW: Registrar creates program rule set")
    void ALLOW_registrarSetsProgramRules() {
        doNothing().when(permissionEvaluatorService).authorize(any(), eq("PROGRAM_RULES_SET"), any(), any());
        when(programRuleSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateProgramRuleSetRequest req = new CreateProgramRuleSetRequest(null, null, "{\"minCredits\": 160}", LocalDate.now());
        ResponseEntity<ProgramRuleSetResponse> res = programRuleController.createRuleSet(tenantId, req, auth);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().rules()).contains("minCredits");
    }

    @Test
    @DisplayName("ACT-PROGRAM_RULES_SET-DENY: Unauthorized role is rejected")
    void DENY_unauthorizedProgramRulesRejected() {
        doThrow(new AccessDeniedException("ACTION_NOT_GRANTED"))
                .when(permissionEvaluatorService).authorize(any(), eq("PROGRAM_RULES_SET"), any(), any());

        CreateProgramRuleSetRequest req = new CreateProgramRuleSetRequest(null, null, "{}", LocalDate.now());
        assertThatThrownBy(() -> programRuleController.createRuleSet(tenantId, req, auth))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------------------------------------------------------------------------------------------
    // Story #34: GRADING_SCHEME_SET
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("ACT-GRADING_SCHEME_SET-ALLOW: Registrar / Admin creates grading scheme")
    void ALLOW_setsGradingScheme() {
        doNothing().when(permissionEvaluatorService).authorize(any(), eq("GRADING_SCHEME_SET"), any(), any());
        when(gradingSchemeVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateGradingSchemeRequest req = new CreateGradingSchemeRequest(institutionId, "Standard 10-Point Scale", LocalDate.now());
        ResponseEntity<GradingSchemeResponse> res = gradingSchemeController.createGradingScheme(tenantId, req, auth);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().schemeName()).isEqualTo("Standard 10-Point Scale");
    }

    // ---------------------------------------------------------------------------------------------
    // Story #36: SUBJECT_ALLOCATE
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("ACT-SUBJECT_ALLOCATE-ALLOW: HOD / Admin allocates subject to section teacher")
    void ALLOW_allocatesSubject() {
        doNothing().when(permissionEvaluatorService).authorize(any(), eq("SUBJECT_ALLOCATE"), any(), any());
        when(subjectAllocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateSubjectAllocationRequest req = new CreateSubjectAllocationRequest(
                institutionId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID()
        );
        ResponseEntity<SubjectAllocationResponse> res = subjectAllocationController.allocateSubject(tenantId, req, auth);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("ACT-SUBJECT_ALLOCATE-GUARD: Subject allocation must target either a section or an elective group")
    void GUARD_allocationTargetInvalid() {
        doNothing().when(permissionEvaluatorService).authorize(any(), eq("SUBJECT_ALLOCATE"), any(), any());

        CreateSubjectAllocationRequest req = new CreateSubjectAllocationRequest(
                institutionId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(), // Both section and group provided
                UUID.randomUUID()
        );
        assertThatThrownBy(() -> subjectAllocationController.allocateSubject(tenantId, req, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not both and not neither");
    }

    // ---------------------------------------------------------------------------------------------
    // Stories #39 & #40: PROMOTION_RUN & PROMOTION_APPROVE
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("ACT-PROMOTION_RUN-ALLOW & ACT-PROMOTION_APPROVE-ALLOW: Admin runs and Principal approves")
    void ALLOW_promotionRunWorkflow() {
        doNothing().when(permissionEvaluatorService).authorize(any(), eq("PROMOTION_RUN"), any(), any());
        doNothing().when(permissionEvaluatorService).authorize(any(), eq("PROMOTION_APPROVE"), any(), any());

        UUID fromSession = UUID.randomUUID();
        UUID toSession = UUID.randomUUID();
        when(promotionRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 1. Create run
        CreatePromotionRunRequest req = new CreatePromotionRunRequest(institutionId, fromSession, toSession);
        ResponseEntity<PromotionRunResponse> createRes = promotionController.createPromotionRun(tenantId, req, auth);
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 2. Principal approves
        UUID runId = createRes.getBody().promotionRunId();
        PromotionRun run = PromotionRun.builder()
                .id(new PromotionRunId(tenantId, runId))
                .institutionId(institutionId)
                .fromSessionId(fromSession)
                .toSessionId(toSession)
                .status("PENDING_APPROVAL")
                .build();
        when(promotionRunRepository.findByIdTenantIdAndIdPromotionRunId(tenantId, runId)).thenReturn(Optional.of(run));

        ResponseEntity<PromotionRunResponse> approveRes = promotionController.decidePromotionRun(
                tenantId, runId, new PromotionDecisionRequest("APPROVED", "Approved for semester roll-forward"), auth
        );
        assertThat(approveRes.getBody().status()).isEqualTo("APPROVED");
    }

    // ---------------------------------------------------------------------------------------------
    // Story #41: STUDENT_STATUS_CHANGE
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("ACT-STUDENT_STATUS_CHANGE-ALLOW: Admin changes student status with mandatory statutory reason")
    void ALLOW_studentStatusChange() {
        doNothing().when(permissionEvaluatorService).authorize(any(), eq("STUDENT_STATUS_CHANGE"), any(), any());

        UUID studentId = UUID.randomUUID();
        StudentProfile student = new StudentProfile(studentId, tenantId, institutionId, UUID.randomUUID(), "STU-2026-001", "ACTIVE");
        when(studentProfileRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(studentProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChangeStudentStatusRequest req = new ChangeStudentStatusRequest("WITHDRAWN", "Family relocation abroad");
        ResponseEntity<StudentStatusResponse> res = studentStatusController.changeStatus(tenantId, studentId, req, auth);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().status()).isEqualTo("WITHDRAWN");
        assertThat(res.getBody().statusReason()).isEqualTo("Family relocation abroad");
    }

    @Test
    @DisplayName("ACT-STUDENT_STATUS_CHANGE-SCOPE: Cross-tenant student status change refused")
    void SCOPE_crossTenantRefused() {
        UUID otherTenant = UUID.randomUUID();
        assertThatThrownBy(() -> studentStatusController.getStudent(otherTenant, UUID.randomUUID(), auth))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cross-tenant access forbidden");
    }
}

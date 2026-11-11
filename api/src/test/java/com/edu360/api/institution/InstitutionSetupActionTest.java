package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.TenantContext;

import com.edu360.api.academic.ChangeStudentStatusRequest;
import com.edu360.api.academic.CreateGradingSchemeRequest;
import com.edu360.api.academic.CreateProgramRuleSetRequest;
import com.edu360.api.academic.CreatePromotionRunRequest;
import com.edu360.api.academic.CreateSubjectAllocationRequest;
import com.edu360.api.academic.GradingSchemeResponse;
import com.edu360.api.academic.GradingSchemeService;
import com.edu360.api.academic.GradingSchemeVersion;
import com.edu360.api.academic.GradingSchemeVersionId;
import com.edu360.api.academic.GradingSchemeVersionRepository;
import com.edu360.api.academic.ProgramRuleService;
import com.edu360.api.academic.ProgramRuleSet;
import com.edu360.api.academic.ProgramRuleSetRepository;
import com.edu360.api.academic.ProgramRuleSetResponse;
import com.edu360.api.academic.PromotionDecisionRequest;
import com.edu360.api.academic.PromotionRun;
import com.edu360.api.academic.PromotionRunId;
import com.edu360.api.academic.PromotionRunRepository;
import com.edu360.api.academic.PromotionRunResponse;
import com.edu360.api.academic.PromotionService;
import com.edu360.api.academic.StudentStatusResponse;
import com.edu360.api.academic.StudentStatusService;
import com.edu360.api.academic.SubjectAllocation;
import com.edu360.api.academic.SubjectAllocationRepository;
import com.edu360.api.academic.SubjectAllocationResponse;
import com.edu360.api.academic.SubjectAllocationService;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.StudentProfile;
import com.edu360.api.identity.StudentProfileRepository;
import com.edu360.api.institution.dto.AcademicSessionResponse;
import com.edu360.api.institution.dto.CalendarEventRequest;
import com.edu360.api.institution.dto.CalendarEventResponse;
import com.edu360.api.institution.dto.ClassTeacherAssignRequest;
import com.edu360.api.institution.dto.ClassTeacherAssignResponse;
import com.edu360.api.institution.dto.CreateAcademicSessionRequest;
import com.edu360.api.institution.dto.CreateDepartmentRequest;
import com.edu360.api.institution.dto.CreateProgramRequest;
import com.edu360.api.institution.dto.CreateProgramYearRequest;
import com.edu360.api.institution.dto.CreateSchoolClassRequest;
import com.edu360.api.institution.dto.CreateSectionRequest;
import com.edu360.api.institution.dto.CreateSemesterRequest;
import com.edu360.api.institution.dto.CreateSubjectRequest;
import com.edu360.api.institution.dto.DepartmentResponse;
import com.edu360.api.institution.dto.HodAssignRequest;
import com.edu360.api.institution.dto.HodAssignResponse;
import com.edu360.api.institution.dto.MentorAssignRequest;
import com.edu360.api.institution.dto.MentorAssignResponse;
import com.edu360.api.institution.dto.ProgramResponse;
import com.edu360.api.institution.dto.ProgramYearResponse;
import com.edu360.api.institution.dto.SchoolClassResponse;
import com.edu360.api.institution.dto.SectionResponse;
import com.edu360.api.institution.dto.SemesterResponse;
import com.edu360.api.institution.dto.StudentLoginSwitchRequest;
import com.edu360.api.institution.dto.StudentLoginSwitchResponse;
import com.edu360.api.institution.dto.SubjectResponse;
import com.edu360.api.institution.dto.UpdateAcademicSessionRequest;
import com.edu360.api.institution.dto.WeeklyOffRuleRequest;
import com.edu360.api.institution.dto.WeeklyOffRuleResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.termlock.TermLock;
import com.edu360.api.termlock.TermLockEventRepository;
import com.edu360.api.termlock.TermLockId;
import com.edu360.api.termlock.TermLockRepository;
import com.edu360.api.termlock.TermLockService;
import com.edu360.api.termlock.dto.LockTermRequest;
import com.edu360.api.termlock.dto.TermLockResponse;
import com.edu360.api.termlock.dto.UnlockTermRequest;

@ExtendWith(MockitoExtension.class)
class InstitutionSetupActionTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID institutionId = UUID.randomUUID();
    private final UUID departmentId = UUID.randomUUID();
    private final UUID programId = UUID.randomUUID();
    private final UUID programYearId = UUID.randomUUID();
    private final UUID semesterId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID sectionId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID staffAssignmentId = UUID.randomUUID();

    @Mock private PermissionEvaluatorService permissionEvaluatorService;
    @Mock private AuditLogService auditLogService;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private ProgramRepository programRepository;
    @Mock private ProgramYearRepository programYearRepository;
    @Mock private SemesterRepository semesterRepository;
    @Mock private SchoolClassRepository schoolClassRepository;
    @Mock private SectionRepository sectionRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private CourseOfferingRepository courseOfferingRepository;
    @Mock private AcademicSessionRepository academicSessionRepository;
    @Mock private ProgramRuleSetRepository programRuleSetRepository;
    @Mock private AcademicCalendarEventRepository academicCalendarEventRepository;
    @Mock private WeeklyOffRuleRepository weeklyOffRuleRepository;
    @Mock private GradingSchemeVersionRepository gradingSchemeVersionRepository;
    @Mock private StaffAssignmentRepository staffAssignmentRepository;
    @Mock private SubjectAllocationRepository subjectAllocationRepository;
    @Mock private MentorAssignmentRepository mentorAssignmentRepository;
    @Mock private ClassTeacherAssignmentRepository classTeacherAssignmentRepository;
    @Mock private PromotionRunRepository promotionRunRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private TermLockRepository termLockRepository;
    @Mock private TermLockEventRepository termLockEventRepository;

    private StructureService structureService;
    private ProgramRuleService programRuleService;
    private MasterCalendarService masterCalendarService;
    private CalendarEventService calendarEventService;
    private WeeklyOffRuleService weeklyOffRuleService;
    private GradingSchemeService gradingSchemeService;
    private HodAssignmentService hodAssignmentService;
    private SubjectAllocationService subjectAllocationService;
    private MentorAssignmentService mentorAssignmentService;
    private ClassTeacherAssignmentService classTeacherAssignmentService;
    private PromotionService promotionService;
    private StudentStatusService studentStatusService;
    private StudentLoginSwitchService studentLoginSwitchService;
    private TermLockService termLockService;

    @BeforeEach
    void setUp() {
        structureService = new StructureService(
                permissionEvaluatorService,
                institutionRepository,
                departmentRepository,
                programRepository,
                auditLogService,
                programYearRepository,
                semesterRepository,
                schoolClassRepository,
                sectionRepository,
                subjectRepository,
                courseOfferingRepository,
                academicSessionRepository,
                programRuleSetRepository
        );

        programRuleService = new ProgramRuleService(
                programRuleSetRepository,
                permissionEvaluatorService,
                auditLogService
        );

        masterCalendarService = new MasterCalendarService(
                academicSessionRepository,
                academicCalendarEventRepository,
                permissionEvaluatorService,
                auditLogService
        );

        calendarEventService = new CalendarEventService(
                academicCalendarEventRepository,
                permissionEvaluatorService,
                academicSessionRepository
        );

        weeklyOffRuleService = new WeeklyOffRuleService(
                weeklyOffRuleRepository,
                permissionEvaluatorService
        );

        gradingSchemeService = new GradingSchemeService(
                gradingSchemeVersionRepository,
                permissionEvaluatorService,
                auditLogService
        );

        hodAssignmentService = new HodAssignmentService(
                departmentRepository,
                staffAssignmentRepository,
                permissionEvaluatorService
        );

        subjectAllocationService = new SubjectAllocationService(
                subjectAllocationRepository,
                permissionEvaluatorService,
                auditLogService,
                sectionRepository,
                staffAssignmentRepository
        );

        mentorAssignmentService = new MentorAssignmentService(
                sectionRepository,
                semesterRepository,
                programYearRepository,
                programRepository,
                staffAssignmentRepository,
                mentorAssignmentRepository,
                permissionEvaluatorService
        );

        classTeacherAssignmentService = new ClassTeacherAssignmentService(
                sectionRepository,
                staffAssignmentRepository,
                classTeacherAssignmentRepository,
                permissionEvaluatorService
        );

        promotionService = new PromotionService(
                promotionRunRepository,
                permissionEvaluatorService,
                auditLogService
        );

        studentStatusService = new StudentStatusService(
                studentProfileRepository,
                permissionEvaluatorService,
                auditLogService
        );

        studentLoginSwitchService = new StudentLoginSwitchService(
                schoolClassRepository,
                permissionEvaluatorService
        );

        termLockService = new TermLockService(
                termLockRepository,
                termLockEventRepository,
                permissionEvaluatorService,
                auditLogService
        );
        TenantContext.set(tenantId, IsolationMode.SHARED, null, "ALL", java.util.Set.of(institutionId), "PRINCIPAL_COLLEGE", ScopeType.INSTITUTION);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private RbacPrincipal principal() {
        return new RbacPrincipal(tenantId, userId);
    }

    private TenantPrincipal tenantPrincipal() {
        return new TenantPrincipal(tenantId, userId, com.edu360.api.identity.UserType.STAFF, "User", false);
    }

    // =========================================================================
    // 29. STRUCTURE_MANAGE
    // =========================================================================

    @Test
    @DisplayName("ACT-STRUCTURE_MANAGE-ALLOW: College Admin creates department and program")
    void actStructureManage_allow() {
        RbacPrincipal p = principal();
        Institution inst = Institution.builder()
                .id(new InstitutionId(tenantId, institutionId))
                .institutionType("COLLEGE")
                .build();
        when(institutionRepository.findById(new InstitutionId(tenantId, institutionId))).thenReturn(Optional.of(inst));
        when(departmentRepository.existsByTenantIdAndInstitutionIdAndDepartmentCode(tenantId, institutionId, "CS")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

        DepartmentResponse resp = structureService.createDepartment(p, institutionId, new CreateDepartmentRequest("CS", "Computer Science"));
        assertThat(resp).isNotNull();
        assertThat(resp.departmentCode()).isEqualTo("CS");
    }

    @Test
    @DisplayName("ACT-STRUCTURE_MANAGE-DENY: Role lacking STRUCTURE_MANAGE is refused")
    void actStructureManage_deny() {
        RbacPrincipal p = principal();
        doThrow(new AccessDeniedException("Access denied")).when(permissionEvaluatorService)
                .authorize(eq(p), eq("STRUCTURE_MANAGE"), any(RbacScope.class), any(RbacTarget.class));

        assertThatThrownBy(() -> structureService.listDepartments(p, institutionId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-STRUCTURE_MANAGE-SCOPE: Cross-tenant structure access is refused")
    void actStructureManage_scope() {
        RbacPrincipal p = principal();
        when(institutionRepository.findById(new InstitutionId(tenantId, institutionId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> structureService.createDepartment(p, institutionId, new CreateDepartmentRequest("CS", "Comp Sci")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("ACT-STRUCTURE_MANAGE-GUARD: Rule HI-12 school cannot have departments")
    void actStructureManage_guard() {
        RbacPrincipal p = principal();
        Institution school = Institution.builder()
                .id(new InstitutionId(tenantId, institutionId))
                .institutionType("SCHOOL")
                .build();
        when(institutionRepository.findById(new InstitutionId(tenantId, institutionId))).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> structureService.createDepartment(p, institutionId, new CreateDepartmentRequest("CS", "Comp Sci")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("HI-12");
    }

    // =========================================================================
    // 30. PROGRAM_RULES_SET
    // =========================================================================

    @Test
    @DisplayName("ACT-PROGRAM_RULES_SET-ALLOW: Registrar creates program rule set")
    void actProgramRulesSet_allow() {
        when(programRuleSetRepository.save(any(ProgramRuleSet.class))).thenAnswer(inv -> inv.getArgument(0));
        ProgramRuleSetResponse resp = programRuleService.createRuleSet(tenantId, tenantPrincipal(),
                new CreateProgramRuleSetRequest(institutionId, programId, "{\"credits\":120}", LocalDate.now()));
        assertThat(resp).isNotNull();
        assertThat(resp.status()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("ACT-PROGRAM_RULES_SET-DENY: Unauthorized role is refused")
    void actProgramRulesSet_deny() {
        doThrow(new AccessDeniedException("Forbidden")).when(permissionEvaluatorService)
                .authorize(any(), eq("PROGRAM_RULES_SET"), any(), any());

        assertThatThrownBy(() -> programRuleService.listRuleSets(tenantId, tenantPrincipal()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-PROGRAM_RULES_SET-SCOPE: Cross-tenant program rule request rejected")
    void actProgramRulesSet_scope() {
        UUID otherRuleSetId = UUID.randomUUID();
        when(programRuleSetRepository.findByIdTenantIdAndIdProgramRuleSetId(tenantId, otherRuleSetId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> programRuleService.getRuleSet(tenantId, tenantPrincipal(), otherRuleSetId))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("ACT-PROGRAM_RULES_SET-GUARD: Activation of non-existent rule set is rejected")
    void actProgramRulesSet_guard() {
        UUID missingId = UUID.randomUUID();
        when(programRuleSetRepository.findByIdTenantIdAndIdProgramRuleSetId(tenantId, missingId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> programRuleService.activateRuleSet(tenantId, tenantPrincipal(), missingId))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // =========================================================================
    // 31. CALENDAR_MASTER_SET
    // =========================================================================

    @Test
    @DisplayName("ACT-CALENDAR_MASTER_SET-ALLOW: Registrar creates and updates academic session")
    void actCalendarMasterSet_allow() {
        RbacPrincipal p = principal();
        when(academicSessionRepository.save(any(AcademicSession.class))).thenAnswer(inv -> inv.getArgument(0));
        AcademicSessionResponse created = masterCalendarService.createAcademicSession(p,
                new CreateAcademicSessionRequest(null, "2026-2027", LocalDate.of(2026, 8, 1), LocalDate.of(2027, 5, 31), 2026, "ACTIVE"));
        assertThat(created).isNotNull();

        AcademicSession existing = AcademicSession.builder()
                .academicSessionId(created.academicSessionId())
                .tenantId(tenantId)
                .sessionLabel("2026-2027")
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2027, 5, 31))
                .status("ACTIVE")
                .build();
        when(academicSessionRepository.findByTenantIdAndAcademicSessionId(tenantId, created.academicSessionId()))
                .thenReturn(Optional.of(existing));

        AcademicSessionResponse updated = masterCalendarService.updateAcademicSession(p, created.academicSessionId(),
                new UpdateAcademicSessionRequest("2026-2027 Rev", LocalDate.of(2026, 8, 15), LocalDate.of(2027, 6, 15), "ACTIVE", true, true));
        assertThat(updated.sessionLabel()).isEqualTo("2026-2027 Rev");
    }

    @Test
    @DisplayName("ACT-CALENDAR_MASTER_SET-DENY: Role lacking CALENDAR_MASTER_SET is refused")
    void actCalendarMasterSet_deny() {
        RbacPrincipal p = principal();
        doThrow(new AccessDeniedException("Forbidden")).when(permissionEvaluatorService)
                .authorize(eq(p), eq("CALENDAR_MASTER_SET"), any(), any());

        assertThatThrownBy(() -> masterCalendarService.listMasterCalendarEvents(p, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-CALENDAR_MASTER_SET-SCOPE: Mismatched tenant session is rejected with 404")
    void actCalendarMasterSet_scope() {
        RbacPrincipal p = principal();
        UUID otherSessionId = UUID.randomUUID();
        when(academicSessionRepository.findByTenantIdAndAcademicSessionId(tenantId, otherSessionId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> masterCalendarService.getAcademicSession(p, otherSessionId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("ACT-CALENDAR_MASTER_SET-GUARD: End date before start date rejected")
    void actCalendarMasterSet_guard() {
        RbacPrincipal p = principal();
        assertThatThrownBy(() -> masterCalendarService.createAcademicSession(p,
                new CreateAcademicSessionRequest(null, "Invalid", LocalDate.of(2027, 5, 1), LocalDate.of(2026, 5, 1), 2026, "ACTIVE")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("endDate must be on or after startDate");
    }

    // =========================================================================
    // 32. CALENDAR_LOCAL_SET
    // =========================================================================

    @Test
    @DisplayName("ACT-CALENDAR_LOCAL_SET-ALLOW: College Admin adds local holiday")
    void actCalendarLocalSet_allow() {
        RbacPrincipal p = principal();
        when(academicSessionRepository.findByTenantIdAndAcademicSessionId(tenantId, sessionId))
                .thenReturn(Optional.of(new AcademicSession()));
        when(academicCalendarEventRepository.findByTenantIdAndAcademicSessionIdAndSourceLevelOrderByStartDateAsc(tenantId, sessionId, "UNIVERSITY"))
                .thenReturn(List.of());

        CalendarEventResponse resp = calendarEventService.addLocalEvent(p,
                new CalendarEventRequest(institutionId, sessionId, "LOCAL_HOLIDAY", "Founder's Day", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1)));
        assertThat(resp).isNotNull();
        assertThat(resp.title()).isEqualTo("Founder's Day");
    }

    @Test
    @DisplayName("ACT-CALENDAR_LOCAL_SET-DENY: Role lacking CALENDAR_LOCAL_SET is refused")
    void actCalendarLocalSet_deny() {
        RbacPrincipal p = principal();
        doThrow(new AccessDeniedException("Denied")).when(permissionEvaluatorService)
                .authorize(eq(p), eq("CALENDAR_LOCAL_SET"), any(), any());

        assertThatThrownBy(() -> calendarEventService.listLocalEvents(p, institutionId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-CALENDAR_LOCAL_SET-SCOPE: Cross-institution local event query rejected")
    void actCalendarLocalSet_scope() {
        RbacPrincipal p = principal();
        UUID otherInst = UUID.randomUUID();
        doThrow(new AccessDeniedException("Scope breach")).when(permissionEvaluatorService)
                .authorize(eq(p), eq("CALENDAR_LOCAL_SET"), eq(RbacScope.institution(otherInst)), any());

        assertThatThrownBy(() -> calendarEventService.listLocalEvents(p, otherInst))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-CALENDAR_LOCAL_SET-GUARD: T06 unknown session gives 404, T07 blank title rejected, and exam conflict blocked")
    void actCalendarLocalSet_guard() {
        RbacPrincipal p = principal();

        // T07 blank title
        assertThatThrownBy(() -> calendarEventService.addLocalEvent(p,
                new CalendarEventRequest(institutionId, sessionId, "LOCAL_HOLIDAY", "  ", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1))))
                .isInstanceOf(IllegalArgumentException.class);

        // T06 missing session 404
        when(academicSessionRepository.findByTenantIdAndAcademicSessionId(tenantId, sessionId))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> calendarEventService.addLocalEvent(p,
                new CalendarEventRequest(institutionId, sessionId, "LOCAL_HOLIDAY", "Valid Title", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1))))
                .isInstanceOf(ResourceNotFoundException.class);

        // Exam conflict
        when(academicSessionRepository.findByTenantIdAndAcademicSessionId(tenantId, sessionId))
                .thenReturn(Optional.of(new AcademicSession()));
        AcademicCalendarEvent examEvent = new AcademicCalendarEvent();
        examEvent.setTitle("Final Semester Exam");
        examEvent.setStartDate(LocalDate.of(2026, 10, 1));
        examEvent.setEndDate(LocalDate.of(2026, 10, 5));
        when(academicCalendarEventRepository.findByTenantIdAndAcademicSessionIdAndSourceLevelOrderByStartDateAsc(tenantId, sessionId, "UNIVERSITY"))
                .thenReturn(List.of(examEvent));

        assertThatThrownBy(() -> calendarEventService.addLocalEvent(p,
                new CalendarEventRequest(institutionId, sessionId, "LOCAL_HOLIDAY", "Local Holiday", LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2))))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("overlaps with scheduled university exam dates");
    }

    // =========================================================================
    // 33. WEEKLY_OFF_SET
    // =========================================================================

    @Test
    @DisplayName("ACT-WEEKLY_OFF_SET-ALLOW: College Admin sets weekly off rule")
    void actWeeklyOffSet_allow() {
        RbacPrincipal p = principal();
        when(weeklyOffRuleRepository.save(any(WeeklyOffRule.class))).thenAnswer(inv -> inv.getArgument(0));

        WeeklyOffRuleResponse resp = weeklyOffRuleService.setRule(p,
                new WeeklyOffRuleRequest(institutionId, (short) 7, "ALL", LocalDate.now(), null));
        assertThat(resp).isNotNull();
        assertThat(resp.dayOfWeek()).isEqualTo((short) 7);
    }

    @Test
    @DisplayName("ACT-WEEKLY_OFF_SET-DENY: Unauthorized role refused")
    void actWeeklyOffSet_deny() {
        RbacPrincipal p = principal();
        doThrow(new AccessDeniedException("Denied")).when(permissionEvaluatorService)
                .authorize(eq(p), eq("WEEKLY_OFF_SET"), any(), any());

        assertThatThrownBy(() -> weeklyOffRuleService.listRules(p, institutionId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-WEEKLY_OFF_SET-SCOPE: Setting weekly off for foreign institution rejected")
    void actWeeklyOffSet_scope() {
        RbacPrincipal p = principal();
        UUID otherInst = UUID.randomUUID();
        doThrow(new AccessDeniedException("Scope check failed")).when(permissionEvaluatorService)
                .authorize(eq(p), eq("WEEKLY_OFF_SET"), eq(RbacScope.institution(otherInst)), any());

        assertThatThrownBy(() -> weeklyOffRuleService.setRule(p,
                new WeeklyOffRuleRequest(otherInst, (short) 7, "ALL", LocalDate.now(), null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-WEEKLY_OFF_SET-GUARD: Invalid day of week (< 1 or > 7) rejected")
    void actWeeklyOffSet_guard() {
        RbacPrincipal p = principal();
        assertThatThrownBy(() -> weeklyOffRuleService.setRule(p,
                new WeeklyOffRuleRequest(institutionId, (short) 8, "ALL", LocalDate.now(), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // 34. GRADING_SCHEME_SET
    // =========================================================================

    @Test
    @DisplayName("ACT-GRADING_SCHEME_SET-ALLOW: Registrar / Admin creates and activates grading scheme")
    void actGradingSchemeSet_allow() {
        when(gradingSchemeVersionRepository.save(any(GradingSchemeVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        GradingSchemeResponse created = gradingSchemeService.createGradingScheme(tenantId, tenantPrincipal(),
                new CreateGradingSchemeRequest(institutionId, "10-point scale", LocalDate.now()));
        assertThat(created).isNotNull();
        assertThat(created.status()).isEqualTo("DRAFT");

        GradingSchemeVersion gsv = GradingSchemeVersion.builder()
                .id(new GradingSchemeVersionId(tenantId, created.gradingSchemeVersionId()))
                .status("DRAFT")
                .build();
        when(gradingSchemeVersionRepository.findByIdTenantIdAndIdGradingSchemeVersionId(tenantId, created.gradingSchemeVersionId()))
                .thenReturn(Optional.of(gsv));

        GradingSchemeResponse activated = gradingSchemeService.activateGradingScheme(tenantId, tenantPrincipal(), created.gradingSchemeVersionId());
        assertThat(activated.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("ACT-GRADING_SCHEME_SET-DENY: Role lacking GRADING_SCHEME_SET is refused")
    void actGradingSchemeSet_deny() {
        doThrow(new AccessDeniedException("Forbidden")).when(permissionEvaluatorService)
                .authorize(any(), eq("GRADING_SCHEME_SET"), any(), any());

        assertThatThrownBy(() -> gradingSchemeService.listGradingSchemes(tenantId, tenantPrincipal(), institutionId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-GRADING_SCHEME_SET-SCOPE: Cross-tenant grading scheme activation refused")
    void actGradingSchemeSet_scope() {
        UUID otherSchemeId = UUID.randomUUID();
        when(gradingSchemeVersionRepository.findByIdTenantIdAndIdGradingSchemeVersionId(tenantId, otherSchemeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> gradingSchemeService.activateGradingScheme(tenantId, tenantPrincipal(), otherSchemeId))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("ACT-GRADING_SCHEME_SET-GUARD: Non-existent scheme version throws error")
    void actGradingSchemeSet_guard() {
        UUID nonExistent = UUID.randomUUID();
        when(gradingSchemeVersionRepository.findByIdTenantIdAndIdGradingSchemeVersionId(tenantId, nonExistent))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> gradingSchemeService.activateGradingScheme(tenantId, tenantPrincipal(), nonExistent))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // =========================================================================
    // 35. HOD_ASSIGN
    // =========================================================================

    @Test
    @DisplayName("ACT-HOD_ASSIGN-ALLOW: Principal assigns faculty member as HOD")
    void actHodAssign_allow() {
        RbacPrincipal p = principal();
        Department dept = Department.builder()
                .departmentId(departmentId)
                .tenantId(tenantId)
                .institutionId(institutionId)
                .build();
        when(departmentRepository.findByTenantIdAndDepartmentId(tenantId, departmentId)).thenReturn(Optional.of(dept));

        StaffAssignment staff = new StaffAssignment();
        staff.setStaffAssignmentId(staffAssignmentId);
        staff.setTenantId(tenantId);
        staff.setInstitutionId(institutionId);
        staff.setDepartmentId(departmentId);
        staff.setStatus("ACTIVE");
        staff.setEffectiveFrom(LocalDate.now(com.edu360.api.time.TenantClock.DEFAULT_ZONE));
        when(staffAssignmentRepository.findByTenantIdAndStaffAssignmentId(tenantId, staffAssignmentId)).thenReturn(Optional.of(staff));

        HodAssignResponse resp = hodAssignmentService.assignHod(p, departmentId, new HodAssignRequest(staffAssignmentId));
        assertThat(resp).isNotNull();
        assertThat(resp.hodStaffAssignmentId()).isEqualTo(staffAssignmentId);
    }

    @Test
    @DisplayName("ACT-HOD_ASSIGN-DENY: Non-Principal role refused")
    void actHodAssign_deny() {
        RbacPrincipal p = principal();
        Department dept = Department.builder()
                .departmentId(departmentId)
                .tenantId(tenantId)
                .institutionId(institutionId)
                .build();
        when(departmentRepository.findByTenantIdAndDepartmentId(tenantId, departmentId)).thenReturn(Optional.of(dept));
        doThrow(new AccessDeniedException("Access denied")).when(permissionEvaluatorService)
                .authorize(eq(p), eq("HOD_ASSIGN"), any(), any());

        assertThatThrownBy(() -> hodAssignmentService.assignHod(p, departmentId, new HodAssignRequest(staffAssignmentId)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-HOD_ASSIGN-SCOPE: Assigning HOD to non-existent or foreign department rejected")
    void actHodAssign_scope() {
        RbacPrincipal p = principal();
        when(departmentRepository.findByTenantIdAndDepartmentId(tenantId, departmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hodAssignmentService.assignHod(p, departmentId, new HodAssignRequest(staffAssignmentId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("ACT-HOD_ASSIGN-GUARD: Staff from different department cannot be assigned as HOD")
    void actHodAssign_guard() {
        RbacPrincipal p = principal();
        Department dept = Department.builder()
                .departmentId(departmentId)
                .tenantId(tenantId)
                .institutionId(institutionId)
                .build();
        when(departmentRepository.findByTenantIdAndDepartmentId(tenantId, departmentId)).thenReturn(Optional.of(dept));

        UUID otherDeptId = UUID.randomUUID();
        StaffAssignment staff = new StaffAssignment();
        staff.setStaffAssignmentId(staffAssignmentId);
        staff.setTenantId(tenantId);
        staff.setInstitutionId(institutionId);
        staff.setDepartmentId(otherDeptId);
        staff.setStatus("ACTIVE");
        when(staffAssignmentRepository.findByTenantIdAndStaffAssignmentId(tenantId, staffAssignmentId)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> hodAssignmentService.assignHod(p, departmentId, new HodAssignRequest(staffAssignmentId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // 36. SUBJECT_ALLOCATE
    // =========================================================================

    @Test
    @DisplayName("ACT-SUBJECT_ALLOCATE-ALLOW: Admin / HOD allocates subject to section teacher")
    void actSubjectAllocate_allow() {
        when(subjectAllocationRepository.save(any(SubjectAllocation.class))).thenAnswer(inv -> inv.getArgument(0));
        Section section = new Section(); section.setSectionId(sectionId); section.setInstitutionId(institutionId);
        when(sectionRepository.findByTenantIdAndSectionId(tenantId, sectionId)).thenReturn(Optional.of(section));
        StaffAssignment staff = new StaffAssignment(); staff.setStaffAssignmentId(staffAssignmentId); staff.setTenantId(tenantId);
        staff.setInstitutionId(institutionId); staff.setStatus("ACTIVE");
        when(staffAssignmentRepository.findByTenantIdAndStaffAssignmentId(tenantId, staffAssignmentId)).thenReturn(Optional.of(staff));

        SubjectAllocationResponse resp = subjectAllocationService.allocateSubject(tenantId, tenantPrincipal(),
                new CreateSubjectAllocationRequest(institutionId, UUID.randomUUID(), sectionId, null, staffAssignmentId));
        assertThat(resp).isNotNull();
        assertThat(resp.sectionId()).isEqualTo(sectionId);
    }

    @Test
    @DisplayName("ACT-SUBJECT_ALLOCATE-DENY: Unauthorized role refused")
    void actSubjectAllocate_deny() {
        doThrow(new AccessDeniedException("Denied")).when(permissionEvaluatorService)
                .authorize(any(), eq("SUBJECT_ALLOCATE"), any(), any());

        assertThatThrownBy(() -> subjectAllocationService.listAllocations(tenantId, tenantPrincipal(), institutionId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-SUBJECT_ALLOCATE-SCOPE: Ending allocation in foreign tenant rejected")
    void actSubjectAllocate_scope() {
        UUID allocId = UUID.randomUUID();
        when(subjectAllocationRepository.findByIdTenantIdAndIdSubjectAllocationId(tenantId, allocId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectAllocationService.endAllocation(tenantId, tenantPrincipal(), allocId))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("ACT-SUBJECT_ALLOCATE-GUARD: Allocating both section and elective group rejected (ck_subject_allocation_r1)")
    void actSubjectAllocate_guard() {
        assertThatThrownBy(() -> subjectAllocationService.allocateSubject(tenantId, tenantPrincipal(),
                new CreateSubjectAllocationRequest(institutionId, UUID.randomUUID(), sectionId, UUID.randomUUID(), staffAssignmentId)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("must target either a section or an elective group");
    }

    // =========================================================================
    // 37. MENTOR_ASSIGN
    // =========================================================================

    @Test
    @DisplayName("ACT-MENTOR_ASSIGN-ALLOW: College HOD assigns mentor to college section")
    void actMentorAssign_allow() {
        RbacPrincipal p = principal();
        Section sec = new Section();
        sec.setSectionId(sectionId);
        sec.setInstitutionId(institutionId);
        sec.setSemesterId(semesterId);
        when(sectionRepository.findByTenantIdAndSectionId(tenantId, sectionId)).thenReturn(Optional.of(sec));

        Semester sem = new Semester();
        sem.setProgramYearId(programYearId);
        when(semesterRepository.findByTenantIdAndSemesterId(tenantId, semesterId)).thenReturn(Optional.of(sem));

        ProgramYear py = new ProgramYear();
        py.setProgramId(programId);
        when(programYearRepository.findByTenantIdAndProgramYearId(tenantId, programYearId)).thenReturn(Optional.of(py));

        Program prog = new Program();
        prog.setDepartmentId(departmentId);
        when(programRepository.findByTenantIdAndProgramId(tenantId, programId)).thenReturn(Optional.of(prog));

        StaffAssignment staff = new StaffAssignment();
        staff.setStaffAssignmentId(staffAssignmentId);
        staff.setDepartmentId(departmentId);
        staff.setInstitutionId(institutionId);
        staff.setStatus("ACTIVE");
        staff.setEffectiveFrom(LocalDate.now(com.edu360.api.time.TenantClock.DEFAULT_ZONE));
        when(staffAssignmentRepository.findByTenantIdAndStaffAssignmentId(tenantId, staffAssignmentId)).thenReturn(Optional.of(staff));

        MentorAssignResponse resp = mentorAssignmentService.assignMentor(p, sectionId, new MentorAssignRequest(staffAssignmentId));
        assertThat(resp).isNotNull();
        assertThat(resp.staffAssignmentId()).isEqualTo(staffAssignmentId);
    }

    @Test
    @DisplayName("ACT-MENTOR_ASSIGN-DENY: Role lacking MENTOR_ASSIGN refused")
    void actMentorAssign_deny() {
        RbacPrincipal p = principal();
        Section sec = new Section();
        sec.setSectionId(sectionId);
        sec.setInstitutionId(institutionId);
        sec.setSemesterId(semesterId);
        when(sectionRepository.findByTenantIdAndSectionId(tenantId, sectionId)).thenReturn(Optional.of(sec));

        Semester sem = new Semester();
        sem.setProgramYearId(programYearId);
        when(semesterRepository.findByTenantIdAndSemesterId(tenantId, semesterId)).thenReturn(Optional.of(sem));

        ProgramYear py = new ProgramYear();
        py.setProgramId(programId);
        when(programYearRepository.findByTenantIdAndProgramYearId(tenantId, programYearId)).thenReturn(Optional.of(py));

        Program prog = new Program();
        prog.setDepartmentId(departmentId);
        when(programRepository.findByTenantIdAndProgramId(tenantId, programId)).thenReturn(Optional.of(prog));

        doThrow(new AccessDeniedException("Denied")).when(permissionEvaluatorService)
                .authorize(eq(p), eq("MENTOR_ASSIGN"), any(), any());

        assertThatThrownBy(() -> mentorAssignmentService.assignMentor(p, sectionId, new MentorAssignRequest(staffAssignmentId)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-MENTOR_ASSIGN-SCOPE: Assigning mentor to missing section throws 404")
    void actMentorAssign_scope() {
        RbacPrincipal p = principal();
        when(sectionRepository.findByTenantIdAndSectionId(tenantId, sectionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mentorAssignmentService.assignMentor(p, sectionId, new MentorAssignRequest(staffAssignmentId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("ACT-MENTOR_ASSIGN-GUARD: Assigning mentor to school section (semesterId null) is refused")
    void actMentorAssign_guard() {
        RbacPrincipal p = principal();
        Section schoolSec = new Section();
        schoolSec.setSectionId(sectionId);
        schoolSec.setSemesterId(null); // School section
        when(sectionRepository.findByTenantIdAndSectionId(tenantId, sectionId)).thenReturn(Optional.of(schoolSec));

        assertThatThrownBy(() -> mentorAssignmentService.assignMentor(p, sectionId, new MentorAssignRequest(staffAssignmentId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // 38. CLASS_TEACHER_ASSIGN
    // =========================================================================

    @Test
    @DisplayName("ACT-CLASS_TEACHER_ASSIGN-ALLOW: School Principal assigns class teacher to school section")
    void actClassTeacherAssign_allow() {
        RbacPrincipal p = principal();
        Section sec = new Section();
        sec.setSectionId(sectionId);
        sec.setInstitutionId(institutionId);
        sec.setClassId(classId);
        sec.setSemesterId(null);
        when(sectionRepository.findByTenantIdAndSectionId(tenantId, sectionId)).thenReturn(Optional.of(sec));

        StaffAssignment staff = new StaffAssignment();
        staff.setStaffAssignmentId(staffAssignmentId);
        staff.setInstitutionId(institutionId);
        staff.setStatus("ACTIVE");
        staff.setEffectiveFrom(LocalDate.now(com.edu360.api.time.TenantClock.DEFAULT_ZONE));
        when(staffAssignmentRepository.findByTenantIdAndStaffAssignmentId(tenantId, staffAssignmentId)).thenReturn(Optional.of(staff));

        ClassTeacherAssignResponse resp = classTeacherAssignmentService.assignClassTeacher(p, sectionId, new ClassTeacherAssignRequest(staffAssignmentId));
        assertThat(resp).isNotNull();
        assertThat(resp.staffAssignmentId()).isEqualTo(staffAssignmentId);
    }

    @Test
    @DisplayName("ACT-CLASS_TEACHER_ASSIGN-DENY: Unauthorized role refused")
    void actClassTeacherAssign_deny() {
        RbacPrincipal p = principal();
        Section sec = new Section();
        sec.setSectionId(sectionId);
        sec.setInstitutionId(institutionId);
        sec.setClassId(classId);
        sec.setSemesterId(null);
        when(sectionRepository.findByTenantIdAndSectionId(tenantId, sectionId)).thenReturn(Optional.of(sec));
        doThrow(new AccessDeniedException("Access denied")).when(permissionEvaluatorService)
                .authorize(eq(p), eq("CLASS_TEACHER_ASSIGN"), any(), any());

        assertThatThrownBy(() -> classTeacherAssignmentService.assignClassTeacher(p, sectionId, new ClassTeacherAssignRequest(staffAssignmentId)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-CLASS_TEACHER_ASSIGN-SCOPE: Assigning class teacher to missing section rejected")
    void actClassTeacherAssign_scope() {
        RbacPrincipal p = principal();
        when(sectionRepository.findByTenantIdAndSectionId(tenantId, sectionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classTeacherAssignmentService.assignClassTeacher(p, sectionId, new ClassTeacherAssignRequest(staffAssignmentId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("ACT-CLASS_TEACHER_ASSIGN-GUARD: Staff from different school cannot be class teacher")
    void actClassTeacherAssign_guard() {
        RbacPrincipal p = principal();
        Section sec = new Section();
        sec.setSectionId(sectionId);
        sec.setInstitutionId(institutionId);
        sec.setClassId(classId);
        sec.setSemesterId(null);
        when(sectionRepository.findByTenantIdAndSectionId(tenantId, sectionId)).thenReturn(Optional.of(sec));

        UUID otherSchool = UUID.randomUUID();
        StaffAssignment staff = new StaffAssignment();
        staff.setStaffAssignmentId(staffAssignmentId);
        staff.setInstitutionId(otherSchool);
        staff.setStatus("ACTIVE");
        when(staffAssignmentRepository.findByTenantIdAndStaffAssignmentId(tenantId, staffAssignmentId)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> classTeacherAssignmentService.assignClassTeacher(p, sectionId, new ClassTeacherAssignRequest(staffAssignmentId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // 39. PROMOTION_RUN
    // =========================================================================

    @Test
    @DisplayName("ACT-PROMOTION_RUN-ALLOW: Admin initiates promotion run and submits for approval")
    void actPromotionRun_allow() {
        when(promotionRunRepository.save(any(PromotionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        PromotionRunResponse created = promotionService.createPromotionRun(tenantId, tenantPrincipal(),
                new CreatePromotionRunRequest(institutionId, UUID.randomUUID(), UUID.randomUUID()));
        assertThat(created).isNotNull();
        assertThat(created.status()).isEqualTo("DRAFT");

        PromotionRun run = PromotionRun.builder()
                .id(new PromotionRunId(tenantId, created.promotionRunId()))
                .institutionId(institutionId)
                .status("DRAFT")
                .build();
        when(promotionRunRepository.findByIdTenantIdAndIdPromotionRunId(tenantId, created.promotionRunId()))
                .thenReturn(Optional.of(run));

        PromotionRunResponse submitted = promotionService.submitPromotionRun(tenantId, tenantPrincipal(), created.promotionRunId());
        assertThat(submitted.status()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    @DisplayName("ACT-PROMOTION_RUN-DENY: Role lacking PROMOTION_RUN refused")
    void actPromotionRun_deny() {
        UUID runId = UUID.randomUUID();
        PromotionRun run = PromotionRun.builder()
                .id(new PromotionRunId(tenantId, runId))
                .institutionId(institutionId)
                .status("DRAFT")
                .build();
        when(promotionRunRepository.findByIdTenantIdAndIdPromotionRunId(tenantId, runId))
                .thenReturn(Optional.of(run));
        doThrow(new AccessDeniedException("Denied")).when(permissionEvaluatorService)
                .authorize(any(), eq("PROMOTION_RUN"), any(), any());

        assertThatThrownBy(() -> promotionService.getPromotionRun(tenantId, tenantPrincipal(), runId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-PROMOTION_RUN-SCOPE: Promotion run for unknown ID in tenant throws error")
    void actPromotionRun_scope() {
        UUID unknown = UUID.randomUUID();
        when(promotionRunRepository.findByIdTenantIdAndIdPromotionRunId(tenantId, unknown))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> promotionService.getPromotionRun(tenantId, tenantPrincipal(), unknown))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("ACT-PROMOTION_RUN-GUARD: Submitting non-existent promotion run rejected")
    void actPromotionRun_guard() {
        UUID nonExistent = UUID.randomUUID();
        when(promotionRunRepository.findByIdTenantIdAndIdPromotionRunId(tenantId, nonExistent))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> promotionService.submitPromotionRun(tenantId, tenantPrincipal(), nonExistent))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // =========================================================================
    // 40. PROMOTION_APPROVE
    // =========================================================================

    @Test
    @DisplayName("ACT-PROMOTION_APPROVE-ALLOW: Principal approves promotion run")
    void actPromotionApprove_allow() {
        UUID runId = UUID.randomUUID();
        PromotionRun run = PromotionRun.builder()
                .id(new PromotionRunId(tenantId, runId))
                .institutionId(institutionId)
                .status("SUBMITTED")
                .build();
        when(promotionRunRepository.findByIdTenantIdAndIdPromotionRunId(tenantId, runId)).thenReturn(Optional.of(run));
        when(promotionRunRepository.save(any(PromotionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        PromotionRunResponse resp = promotionService.decidePromotionRun(tenantId, tenantPrincipal(), runId,
                new PromotionDecisionRequest("APPROVED", "All criteria verified"));
        assertThat(resp.status()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("ACT-PROMOTION_APPROVE-DENY: Non-Principal role refused")
    void actPromotionApprove_deny() {
        UUID runId = UUID.randomUUID();
        PromotionRun run = PromotionRun.builder()
                .id(new PromotionRunId(tenantId, runId))
                .institutionId(institutionId)
                .status("SUBMITTED")
                .build();
        when(promotionRunRepository.findByIdTenantIdAndIdPromotionRunId(tenantId, runId)).thenReturn(Optional.of(run));
        doThrow(new AccessDeniedException("Denied")).when(permissionEvaluatorService)
                .authorize(any(), eq("PROMOTION_APPROVE"), any(), any());

        assertThatThrownBy(() -> promotionService.decidePromotionRun(tenantId, tenantPrincipal(), runId,
                new PromotionDecisionRequest("APPROVED", "ok")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-PROMOTION_APPROVE-SCOPE: Promotion decision in foreign tenant rejected")
    void actPromotionApprove_scope() {
        UUID runId = UUID.randomUUID();
        when(promotionRunRepository.findByIdTenantIdAndIdPromotionRunId(tenantId, runId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promotionService.decidePromotionRun(tenantId, tenantPrincipal(), runId,
                new PromotionDecisionRequest("APPROVED", "ok")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("ACT-PROMOTION_APPROVE-GUARD: Rejection requires mandatory remark")
    void actPromotionApprove_guard() {
        UUID runId = UUID.randomUUID();
        PromotionRun run = PromotionRun.builder()
                .id(new PromotionRunId(tenantId, runId))
                .institutionId(institutionId)
                .status("SUBMITTED")
                .build();
        when(promotionRunRepository.findByIdTenantIdAndIdPromotionRunId(tenantId, runId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> promotionService.decidePromotionRun(tenantId, tenantPrincipal(), runId,
                new PromotionDecisionRequest("REJECTED", "   ")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Rejection remark is mandatory");
    }

    // =========================================================================
    // 41. STUDENT_STATUS_CHANGE
    // =========================================================================

    @Test
    @DisplayName("ACT-STUDENT_STATUS_CHANGE-ALLOW: Admin changes student status with reason")
    void actStudentStatusChange_allow() {
        UUID studentId = UUID.randomUUID();
        StudentProfile profile = new StudentProfile(studentId, tenantId, institutionId, userId, "STU-001", "ACTIVE");
        when(studentProfileRepository.findById(studentId)).thenReturn(Optional.of(profile));
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentStatusResponse resp = studentStatusService.changeStudentStatus(tenantId, tenantPrincipal(), studentId,
                new ChangeStudentStatusRequest("WITHDRAWN", "Parent requested TC"));
        assertThat(resp.status()).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("ACT-STUDENT_STATUS_CHANGE-DENY: Unauthorized role refused")
    void actStudentStatusChange_deny() {
        UUID studentId = UUID.randomUUID();
        StudentProfile profile = new StudentProfile(studentId, tenantId, institutionId, userId, "STU-001", "ACTIVE");
        when(studentProfileRepository.findById(studentId)).thenReturn(Optional.of(profile));
        doThrow(new AccessDeniedException("Denied")).when(permissionEvaluatorService)
                .authorize(any(), eq("STUDENT_STATUS_CHANGE"), any(), any());

        assertThatThrownBy(() -> studentStatusService.getStudent(tenantId, tenantPrincipal(), studentId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-STUDENT_STATUS_CHANGE-SCOPE: Cross-tenant student profile lookup rejected")
    void actStudentStatusChange_scope() {
        UUID studentId = UUID.randomUUID();
        when(studentProfileRepository.findById(studentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentStatusService.getStudent(tenantId, tenantPrincipal(), studentId))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("ACT-STUDENT_STATUS_CHANGE-GUARD: Missing reason or invalid status transition rejected")
    void actStudentStatusChange_guard() {
        UUID studentId = UUID.randomUUID();
        StudentProfile profile = new StudentProfile(studentId, tenantId, institutionId, userId, "STU-001", "ACTIVE");
        when(studentProfileRepository.findById(studentId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> studentStatusService.changeStudentStatus(tenantId, tenantPrincipal(), studentId,
                new ChangeStudentStatusRequest("WITHDRAWN", "")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Mandatory statutory reason required");
    }

    // =========================================================================
    // 42. STUDENT_LOGIN_SWITCH
    // =========================================================================

    @Test
    @DisplayName("ACT-STUDENT_LOGIN_SWITCH-ALLOW: School Admin enables view-only login for class")
    void actStudentLoginSwitch_allow() {
        RbacPrincipal p = principal();
        SchoolClass sc = new SchoolClass();
        sc.setClassId(classId);
        sc.setInstitutionId(institutionId);
        sc.setStudentLoginEnabled(false);
        when(schoolClassRepository.findByTenantIdAndClassId(tenantId, classId)).thenReturn(Optional.of(sc));

        StudentLoginSwitchResponse resp = studentLoginSwitchService.setStudentLogin(p, classId, new StudentLoginSwitchRequest(true));
        assertThat(resp).isNotNull();
        assertThat(resp.studentLoginEnabled()).isTrue();
    }

    @Test
    @DisplayName("ACT-STUDENT_LOGIN_SWITCH-DENY: Role lacking STUDENT_LOGIN_SWITCH refused")
    void actStudentLoginSwitch_deny() {
        RbacPrincipal p = principal();
        SchoolClass sc = new SchoolClass();
        sc.setClassId(classId);
        sc.setInstitutionId(institutionId);
        when(schoolClassRepository.findByTenantIdAndClassId(tenantId, classId)).thenReturn(Optional.of(sc));
        doThrow(new AccessDeniedException("Forbidden")).when(permissionEvaluatorService)
                .authorize(eq(p), eq("STUDENT_LOGIN_SWITCH"), any(), any());

        assertThatThrownBy(() -> studentLoginSwitchService.setStudentLogin(p, classId, new StudentLoginSwitchRequest(true)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-STUDENT_LOGIN_SWITCH-SCOPE: Missing class in tenant throws 404")
    void actStudentLoginSwitch_scope() {
        RbacPrincipal p = principal();
        when(schoolClassRepository.findByTenantIdAndClassId(tenantId, classId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentLoginSwitchService.setStudentLogin(p, classId, new StudentLoginSwitchRequest(true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("ACT-STUDENT_LOGIN_SWITCH-GUARD: Target institution scope enforced")
    void actStudentLoginSwitch_guard() {
        RbacPrincipal p = principal();
        SchoolClass sc = new SchoolClass();
        sc.setClassId(classId);
        UUID otherInst = UUID.randomUUID();
        sc.setInstitutionId(otherInst);
        when(schoolClassRepository.findByTenantIdAndClassId(tenantId, classId)).thenReturn(Optional.of(sc));
        doThrow(new AccessDeniedException("Institution scope mismatch")).when(permissionEvaluatorService)
                .authorize(eq(p), eq("STUDENT_LOGIN_SWITCH"), eq(RbacScope.institution(otherInst)), any());

        assertThatThrownBy(() -> studentLoginSwitchService.setStudentLogin(p, classId, new StudentLoginSwitchRequest(true)))
                .isInstanceOf(AccessDeniedException.class);
    }

    // =========================================================================
    // 43. TERM_LOCK
    // =========================================================================

    @Test
    @DisplayName("ACT-TERM_LOCK-ALLOW: Admin locks academic term")
    void actTermLock_allow() {
        when(termLockRepository.findByIdTenantIdAndInstitutionIdAndAcademicSessionIdAndSemesterId(tenantId, institutionId, sessionId, semesterId))
                .thenReturn(Optional.empty());
        when(termLockRepository.save(any(TermLock.class))).thenAnswer(inv -> inv.getArgument(0));

        TermLockResponse resp = termLockService.lockTerm(tenantId, institutionId, tenantPrincipal(),
                new LockTermRequest(sessionId, semesterId, "End of semester examination freeze"));
        assertThat(resp).isNotNull();
        assertThat(resp.lockState()).isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("ACT-TERM_LOCK-DENY: Role lacking TERM_LOCK refused")
    void actTermLock_deny() {
        doThrow(new AccessDeniedException("Denied")).when(permissionEvaluatorService)
                .authorize(any(), eq("TERM_LOCK"), any(), any());

        assertThatThrownBy(() -> termLockService.lockTerm(tenantId, institutionId, tenantPrincipal(),
                new LockTermRequest(sessionId, semesterId, "freeze")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-TERM_LOCK-SCOPE: Cross-institution term lock refused")
    void actTermLock_scope() {
        UUID otherInst = UUID.randomUUID();
        doThrow(new AccessDeniedException("Scope breach")).when(permissionEvaluatorService)
                .authorize(any(), eq("TERM_LOCK"), eq(RbacScope.institution(otherInst)), any());

        assertThatThrownBy(() -> termLockService.lockTerm(tenantId, otherInst, tenantPrincipal(),
                new LockTermRequest(sessionId, semesterId, "freeze")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-TERM_LOCK-GUARD: Existing lock is updated idempotently")
    void actTermLock_guard() {
        TermLock existing = TermLock.builder()
                .id(new TermLockId(tenantId, UUID.randomUUID()))
                .institutionId(institutionId)
                .academicSessionId(sessionId)
                .semesterId(semesterId)
                .lockState("TEMP_UNLOCKED")
                .rowVersion(1)
                .build();
        when(termLockRepository.findByIdTenantIdAndInstitutionIdAndAcademicSessionIdAndSemesterId(tenantId, institutionId, sessionId, semesterId))
                .thenReturn(Optional.of(existing));
        when(termLockRepository.save(any(TermLock.class))).thenAnswer(inv -> inv.getArgument(0));

        TermLockResponse resp = termLockService.lockTerm(tenantId, institutionId, tenantPrincipal(),
                new LockTermRequest(sessionId, semesterId, "Relock"));
        assertThat(resp.lockState()).isEqualTo("LOCKED");
    }

    // =========================================================================
    // 44. TERM_UNLOCK
    // =========================================================================

    @Test
    @DisplayName("ACT-TERM_UNLOCK-ALLOW: Principal temporarily unlocks term with reason")
    void actTermUnlock_allow() {
        UUID termLockId = UUID.randomUUID();
        TermLock lock = TermLock.builder()
                .id(new TermLockId(tenantId, termLockId))
                .institutionId(institutionId)
                .lockState("LOCKED")
                .rowVersion(1)
                .build();
        when(termLockRepository.findByIdTenantIdAndIdTermLockId(tenantId, termLockId)).thenReturn(Optional.of(lock));
        when(termLockRepository.save(any(TermLock.class))).thenAnswer(inv -> inv.getArgument(0));

        TermLockResponse resp = termLockService.unlockTerm(tenantId, institutionId, termLockId, tenantPrincipal(),
                new UnlockTermRequest("Grade re-evaluation request approved by Dean", 48));
        assertThat(resp.lockState()).isEqualTo("TEMP_UNLOCKED");
    }

    @Test
    @DisplayName("ACT-TERM_UNLOCK-DENY: Role lacking TERM_UNLOCK refused")
    void actTermUnlock_deny() {
        doThrow(new AccessDeniedException("Forbidden")).when(permissionEvaluatorService)
                .authorize(any(), eq("TERM_UNLOCK"), any(), any());

        assertThatThrownBy(() -> termLockService.unlockTerm(tenantId, institutionId, UUID.randomUUID(), tenantPrincipal(),
                new UnlockTermRequest("reason", 24)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-TERM_UNLOCK-SCOPE: Unlocking term in foreign institution rejected")
    void actTermUnlock_scope() {
        UUID termLockId = UUID.randomUUID();
        UUID otherInst = UUID.randomUUID();
        TermLock lock = TermLock.builder()
                .id(new TermLockId(tenantId, termLockId))
                .institutionId(otherInst)
                .lockState("LOCKED")
                .build();
        when(termLockRepository.findByIdTenantIdAndIdTermLockId(tenantId, termLockId)).thenReturn(Optional.of(lock));

        assertThatThrownBy(() -> termLockService.unlockTerm(tenantId, institutionId, termLockId, tenantPrincipal(),
                new UnlockTermRequest("reason", 24)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("does not belong to the specified institution");
    }

    @Test
    @DisplayName("ACT-TERM_UNLOCK-GUARD: Rule AT-09 reason required and max 72h duration")
    void actTermUnlock_guard() {
        UUID termLockId = UUID.randomUUID();

        // Reason missing
        assertThatThrownBy(() -> termLockService.unlockTerm(tenantId, institutionId, termLockId, tenantPrincipal(),
                new UnlockTermRequest("   ", 24)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Unlock reason is required");

        // Exceeding 72 hours
        assertThatThrownBy(() -> termLockService.unlockTerm(tenantId, institutionId, termLockId, tenantPrincipal(),
                new UnlockTermRequest("Valid reason", 96)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("cannot exceed 72 hours");
    }
}

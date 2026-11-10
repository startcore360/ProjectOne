package com.edu360.api.rbac;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.approval.ApprovalBatchRepository;
import com.edu360.api.approval.ApprovalDelegationController;
import com.edu360.api.approval.ApprovalDelegationService;
import com.edu360.api.approval.ApprovalInboxController;
import com.edu360.api.approval.ApprovalInboxService;
import com.edu360.api.approval.ApprovalRequest;
import com.edu360.api.approval.ApprovalRequestId;
import com.edu360.api.approval.ApprovalRequestRepository;
import com.edu360.api.approval.ApprovalStep;
import com.edu360.api.approval.ApprovalStepId;
import com.edu360.api.approval.ApprovalStepRepository;
import com.edu360.api.approval.Delegation;
import com.edu360.api.approval.DelegationRepository;
import com.edu360.api.approval.dto.BulkApprovalRequest;
import com.edu360.api.approval.dto.BulkApprovalResponse;
import com.edu360.api.approval.dto.CreateDelegationRequest;
import com.edu360.api.approval.dto.DecideApprovalRequestDto;
import com.edu360.api.approval.dto.DelegationResponse;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.guardian.GuardianLinkController;
import com.edu360.api.guardian.GuardianLinkService;
import com.edu360.api.guardian.GuardianProfile;
import com.edu360.api.guardian.GuardianProfileRepository;
import com.edu360.api.guardian.StudentGuardianLink;
import com.edu360.api.guardian.StudentGuardianLinkId;
import com.edu360.api.guardian.StudentGuardianLinkRepository;
import com.edu360.api.guardian.dto.GuardianChangeDecisionRequest;
import com.edu360.api.guardian.dto.GuardianChangeRequestDto;
import com.edu360.api.guardian.dto.GuardianLinkResponse;
import com.edu360.api.guardian.dto.LinkGuardianRequest;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.dto.CreateCustomRoleRequest;
import com.edu360.api.rbac.dto.RoleDetailResponse;
import com.edu360.api.rolldown.CreateRolldownOverrideRequest;
import com.edu360.api.rolldown.RoleRolldownDefaultRepository;
import com.edu360.api.rolldown.RoleRolldownOverride;
import com.edu360.api.rolldown.RoleRolldownOverrideRepository;
import com.edu360.api.rolldown.RolldownController;
import com.edu360.api.rolldown.RolldownMapEntryDto;
import com.edu360.api.rolldown.RolldownOverrideDto;
import com.edu360.api.rolldown.RolldownService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RbacActionTest {

    private static final UUID TENANT_ID = UUID.fromString("29ba13a8-9f52-431e-a359-2eb227a866f3");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final UUID USER_ID = UUID.fromString("874cfca3-360a-4298-90f7-66a938c41ec3");
    private static final UUID INSTITUTION_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID STUDENT_ID = UUID.fromString("55555555-4444-3333-2222-111111111111");
    private static final UUID LINK_ID = UUID.fromString("a03c7d0b-9141-4fe0-9613-68c65f03738a");

    // Mocks
    private RoleDefinitionRepository roleDefRepo;
    private RoleActionRepository roleActionRepo;
    private ActionCatalogRepository actionCatalogRepo;
    private PermissionEvaluatorService permissionEvaluatorService;
    private AuditLogService auditLogService;
    private StudentGuardianLinkRepository linkRepo;
    private GuardianProfileRepository profileRepo;
    private ApprovalRequestRepository approvalRequestRepo;
    private ApprovalStepRepository approvalStepRepo;
    private DelegationRepository delegationRepo;
    private ApprovalBatchRepository approvalBatchRepo;
    private RoleRolldownDefaultRepository defaultRepo;
    private RoleRolldownOverrideRepository overrideRepo;

    // Services & Controllers
    private RoleCustomService roleCustomService;
    private RoleCustomController roleCustomController;
    private GuardianLinkService guardianLinkService;
    private GuardianLinkController guardianLinkController;
    private ApprovalInboxService approvalInboxService;
    private ApprovalInboxController approvalInboxController;
    private ApprovalDelegationService delegationService;
    private ApprovalDelegationController delegationController;
    private RolldownService rolldownService;
    private RolldownController rolldownController;

    @BeforeEach
    void setUp() {
        roleDefRepo = mock(RoleDefinitionRepository.class);
        roleActionRepo = mock(RoleActionRepository.class);
        actionCatalogRepo = mock(ActionCatalogRepository.class);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);
        auditLogService = mock(AuditLogService.class);
        linkRepo = mock(StudentGuardianLinkRepository.class);
        profileRepo = mock(GuardianProfileRepository.class);
        approvalRequestRepo = mock(ApprovalRequestRepository.class);
        approvalStepRepo = mock(ApprovalStepRepository.class);
        delegationRepo = mock(DelegationRepository.class);
        approvalBatchRepo = mock(ApprovalBatchRepository.class);
        defaultRepo = mock(RoleRolldownDefaultRepository.class);
        overrideRepo = mock(RoleRolldownOverrideRepository.class);

        roleCustomService = new RoleCustomService(
                roleDefRepo, roleActionRepo, actionCatalogRepo, permissionEvaluatorService, auditLogService
        );
        roleCustomController = new RoleCustomController(roleCustomService);

        rolldownService = new RolldownService(
                overrideRepo, defaultRepo, permissionEvaluatorService, auditLogService
        );
        rolldownController = new RolldownController(rolldownService);
        TenantContext.set(TENANT_ID, IsolationMode.SHARED, null, "ALL", java.util.Set.of(), "MANAGEMENT", ScopeType.TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Authentication authFor(UUID tenantId, UUID userId, String email) {
        TenantPrincipal principal = new TenantPrincipal(tenantId, userId, UserType.STAFF, email, false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    // ==========================================
    // Story 16: ROLE_CUSTOM_MANAGE
    // ==========================================

    @Test
    @DisplayName("ACT-ROLE_CUSTOM_MANAGE-ALLOW: Authorized tenant admin creates custom role")
    void ACT_ROLE_CUSTOM_MANAGE_ALLOW() {
        when(roleDefRepo.existsByIdTenantIdAndRoleCode(TENANT_ID, "EXAM_ASSISTANT")).thenReturn(false);
        ActionCatalog action = mock(ActionCatalog.class);
        when(action.getActionCode()).thenReturn("MARKS_ENTER");
        when(actionCatalogRepo.findById("MARKS_ENTER")).thenReturn(Optional.of(action));
        when(roleDefRepo.save(any(RoleDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateCustomRoleRequest req = new CreateCustomRoleRequest(
                "EXAM_ASSISTANT",
                "Exam Assistant Role",
                RoleLevel.COLLEGE,
                CustomScope.INSTITUTION,
                List.of("MARKS_ENTER")
        );

        ResponseEntity<RoleDetailResponse> res = roleCustomController.createCustomRole(
                TENANT_ID, req, authFor(TENANT_ID, USER_ID, "admin@univ.edu"));

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("EXAM_ASSISTANT", res.getBody().roleCode());
        verify(auditLogService).record(eq(AuditEventType.CUSTOM_ROLE_CREATED), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ACT-ROLE_CUSTOM_MANAGE-DENY: Unauthorized principal denied role creation")
    void ACT_ROLE_CUSTOM_MANAGE_DENY() {
        doThrow(new UnauthorizedException("Action ROLE_CUSTOM_MANAGE not granted"))
                .when(permissionEvaluatorService).authorize(any(), eq("ROLE_CUSTOM_MANAGE"), any(), any());

        CreateCustomRoleRequest req = new CreateCustomRoleRequest(
                "CUSTOM_TEST", "Test", RoleLevel.COLLEGE, CustomScope.INSTITUTION, List.of()
        );

        assertThrows(UnauthorizedException.class, () ->
                roleCustomController.createCustomRole(TENANT_ID, req, authFor(TENANT_ID, USER_ID, "staff@univ.edu")));
    }

    @Test
    @DisplayName("ACT-ROLE_CUSTOM_MANAGE-SCOPE: Cross-tenant role modification refused")
    void ACT_ROLE_CUSTOM_MANAGE_SCOPE() {
        CreateCustomRoleRequest req = new CreateCustomRoleRequest(
                "CUSTOM_TEST", "Test", RoleLevel.COLLEGE, CustomScope.INSTITUTION, List.of()
        );

        assertThrows(UnauthorizedException.class, () ->
                roleCustomController.createCustomRole(OTHER_TENANT_ID, req, authFor(TENANT_ID, USER_ID, "staff@univ.edu")));
    }

    @Test
    @DisplayName("ACT-ROLE_CUSTOM_MANAGE-GUARD: Duplicate role code rejected")
    void ACT_ROLE_CUSTOM_MANAGE_GUARD() {
        when(roleDefRepo.existsByIdTenantIdAndRoleCode(TENANT_ID, "DUPLICATE_ROLE")).thenReturn(true);

        CreateCustomRoleRequest req = new CreateCustomRoleRequest(
                "DUPLICATE_ROLE", "Duplicate", RoleLevel.COLLEGE, CustomScope.INSTITUTION, List.of()
        );

        assertThrows(DuplicateResourceException.class, () ->
                roleCustomController.createCustomRole(TENANT_ID, req, authFor(TENANT_ID, USER_ID, "admin@univ.edu")));
    }

    // ==========================================
    // Story 17: GUARDIAN_LINK
    // ==========================================

    @Test
    @DisplayName("ACT-ROLLDOWN_VIEW-ALLOW: Authorized user views roll-down map")
    void ACT_ROLLDOWN_VIEW_ALLOW() {
        when(overrideRepo.findByIdTenantIdAndStatus(TENANT_ID, "ACTIVE")).thenReturn(List.of());
        when(defaultRepo.findAll()).thenReturn(List.of());

        ResponseEntity<List<RolldownMapEntryDto>> res = rolldownController.getRolldownMap(
                TENANT_ID, INSTITUTION_ID, null, authFor(TENANT_ID, USER_ID, "admin@univ.edu"));

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
    }

    @Test
    @DisplayName("ACT-ROLLDOWN_VIEW-DENY: Unauthorized user denied viewing roll-down map")
    void ACT_ROLLDOWN_VIEW_DENY() {
        doThrow(new UnauthorizedException("Action ROLLDOWN_VIEW not granted"))
                .when(permissionEvaluatorService).authorize(any(), eq("ROLLDOWN_VIEW"), any(), any());

        assertThrows(UnauthorizedException.class, () ->
                rolldownController.getRolldownMap(TENANT_ID, INSTITUTION_ID, null, authFor(TENANT_ID, USER_ID, "student@univ.edu")));
    }

    @Test
    @DisplayName("ACT-ROLLDOWN_VIEW-SCOPE: Cross-tenant roll-down view forbidden")
    void ACT_ROLLDOWN_VIEW_SCOPE() {
        assertThrows(AccessDeniedException.class, () ->
                rolldownController.getRolldownMap(OTHER_TENANT_ID, INSTITUTION_ID, null, authFor(TENANT_ID, USER_ID, "admin@univ.edu")));
    }

    @Test
    @DisplayName("ACT-ROLLDOWN_VIEW-GUARD: Null tenant ID in path returns forbidden")
    void ACT_ROLLDOWN_VIEW_GUARD() {
        assertThrows(AccessDeniedException.class, () ->
                rolldownController.getRolldownMap(UUID.randomUUID(), INSTITUTION_ID, null, authFor(TENANT_ID, USER_ID, "admin@univ.edu")));
    }

    // ==========================================
    // Story 24: ROLLDOWN_OVERRIDE_SET
    // ==========================================

    @Test
    @DisplayName("ACT-ROLLDOWN_OVERRIDE_SET-ALLOW: Management sets statutory rolldown override")
    void ACT_ROLLDOWN_OVERRIDE_SET_ALLOW() {
        when(overrideRepo.save(any(RoleRolldownOverride.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateRolldownOverrideRequest req = new CreateRolldownOverrideRequest(
                INSTITUTION_ID, "HOD", "ACTION", null, "OVERRIDE", "PROFESSOR", "Senior professor acting as interim HOD"
        );

        ResponseEntity<RolldownOverrideDto> res = rolldownController.createOverride(
                TENANT_ID, "MANAGEMENT", req, authFor(TENANT_ID, USER_ID, "mgmt@univ.edu"));

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("ACTIVE", res.getBody().status());
    }

    @Test
    @DisplayName("ACT-ROLLDOWN_OVERRIDE_SET-DENY: Principal without ROLLDOWN_OVERRIDE_SET denied")
    void ACT_ROLLDOWN_OVERRIDE_SET_DENY() {
        doThrow(new UnauthorizedException("Action ROLLDOWN_OVERRIDE_SET not granted"))
                .when(permissionEvaluatorService).authorize(any(), eq("ROLLDOWN_OVERRIDE_SET"), any(), any());

        CreateRolldownOverrideRequest req = new CreateRolldownOverrideRequest(
                INSTITUTION_ID, "HOD", "ACTION", null, "OVERRIDE", "PROFESSOR", "Senior professor acting as interim HOD"
        );

        assertThrows(UnauthorizedException.class, () ->
                rolldownController.createOverride(TENANT_ID, "STAFF", req, authFor(TENANT_ID, USER_ID, "staff@univ.edu")));
    }

    @Test
    @DisplayName("ACT-ROLLDOWN_OVERRIDE_SET-SCOPE: Setting override in different tenant fails")
    void ACT_ROLLDOWN_OVERRIDE_SET_SCOPE() {
        CreateRolldownOverrideRequest req = new CreateRolldownOverrideRequest(
                INSTITUTION_ID, "HOD", "ACTION", null, "OVERRIDE", "PROFESSOR", "Senior professor acting as interim HOD"
        );

        assertThrows(AccessDeniedException.class, () ->
                rolldownController.createOverride(OTHER_TENANT_ID, "MANAGEMENT", req, authFor(TENANT_ID, USER_ID, "mgmt@univ.edu")));
    }

    @Test
    @DisplayName("ACT-ROLLDOWN_OVERRIDE_SET-GUARD: Guard RD-G2: Withholding approvals is prohibited")
    void ACT_ROLLDOWN_OVERRIDE_SET_GUARD() {
        CreateRolldownOverrideRequest req = new CreateRolldownOverrideRequest(
                INSTITUTION_ID, "HOD", "APPROVE", "MARKS_APPROVE_HOD", "WITHHOLD", "PROFESSOR", "Attempt to withhold approval"
        );

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                rolldownController.createOverride(TENANT_ID, "MANAGEMENT", req, authFor(TENANT_ID, USER_ID, "mgmt@univ.edu")));

        assertEquals("ROLLDOWN_WITHHOLD_APPROVAL", ex.getErrorCode());
    }
}

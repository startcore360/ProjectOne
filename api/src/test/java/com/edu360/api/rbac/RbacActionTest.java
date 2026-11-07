package com.edu360.api.rbac;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

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

        guardianLinkService = new GuardianLinkService(
                linkRepo, profileRepo, permissionEvaluatorService, auditLogService, approvalRequestRepo, approvalStepRepo
        );
        guardianLinkController = new GuardianLinkController(guardianLinkService);

        approvalInboxService = new ApprovalInboxService(
                approvalRequestRepo, approvalStepRepo, auditLogService, linkRepo, null, null
        );
        approvalInboxController = new ApprovalInboxController(approvalInboxService);

        delegationService = new ApprovalDelegationService(
                delegationRepo, approvalBatchRepo, permissionEvaluatorService, auditLogService
        );
        delegationController = new ApprovalDelegationController(delegationService);

        rolldownService = new RolldownService(
                overrideRepo, defaultRepo, permissionEvaluatorService, auditLogService
        );
        rolldownController = new RolldownController(rolldownService);
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
    @DisplayName("ACT-GUARDIAN_LINK-ALLOW: Admin links guardian to student")
    void ACT_GUARDIAN_LINK_ALLOW() {
        when(linkRepo.countByIdTenantIdAndStudentProfileId(TENANT_ID, STUDENT_ID)).thenReturn(0L);
        when(profileRepo.findByIdTenantIdAndMobile(TENANT_ID, "+919876543210")).thenReturn(Optional.empty());
        when(profileRepo.save(any(GuardianProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(linkRepo.save(any(StudentGuardianLink.class))).thenAnswer(inv -> inv.getArgument(0));

        LinkGuardianRequest req = new LinkGuardianRequest(
                "Ramesh Kumar", "+919876543210", "ramesh@example.com", "FATHER", true
        );

        ResponseEntity<GuardianLinkResponse> res = guardianLinkController.linkGuardian(
                TENANT_ID, STUDENT_ID, INSTITUTION_ID, req, authFor(TENANT_ID, USER_ID, "admin@univ.edu"));

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("PENDING_ACTIVATION", res.getBody().status());
        verify(auditLogService).record(eq(AuditEventType.GUARDIAN_LINKED), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ACT-GUARDIAN_LINK-DENY: Non-admin denied guardian linking")
    void ACT_GUARDIAN_LINK_DENY() {
        doThrow(new UnauthorizedException("Action GUARDIAN_LINK not granted"))
                .when(permissionEvaluatorService).authorize(any(), eq("GUARDIAN_LINK"), any(), any());

        LinkGuardianRequest req = new LinkGuardianRequest(
                "Ramesh Kumar", "+919876543210", null, "FATHER", true
        );

        assertThrows(UnauthorizedException.class, () ->
                guardianLinkController.linkGuardian(TENANT_ID, STUDENT_ID, INSTITUTION_ID, req, authFor(TENANT_ID, USER_ID, "student@univ.edu")));
    }

    @Test
    @DisplayName("ACT-GUARDIAN_LINK-SCOPE: Cross-tenant guardian link denied")
    void ACT_GUARDIAN_LINK_SCOPE() {
        LinkGuardianRequest req = new LinkGuardianRequest(
                "Ramesh Kumar", "+919876543210", null, "FATHER", true
        );

        assertThrows(UnauthorizedException.class, () ->
                guardianLinkController.linkGuardian(OTHER_TENANT_ID, STUDENT_ID, INSTITUTION_ID, req, authFor(TENANT_ID, USER_ID, "admin@univ.edu")));
    }

    @Test
    @DisplayName("ACT-GUARDIAN_LINK-GUARD: Limit of max 3 guardians strictly enforced")
    void ACT_GUARDIAN_LINK_GUARD() {
        when(linkRepo.countByIdTenantIdAndStudentProfileId(TENANT_ID, STUDENT_ID)).thenReturn(3L);

        LinkGuardianRequest req = new LinkGuardianRequest(
                "Fourth Guardian", "+919876543299", null, "GUARDIAN", false
        );

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                guardianLinkController.linkGuardian(TENANT_ID, STUDENT_ID, INSTITUTION_ID, req, authFor(TENANT_ID, USER_ID, "admin@univ.edu")));

        assertEquals("MAX_GUARDIANS_EXCEEDED", ex.getErrorCode());
    }

    // ==========================================
    // Story 18: GUARDIAN_CHANGE_REQUEST
    // ==========================================

    @Test
    @DisplayName("ACT-GUARDIAN_CHANGE_REQUEST-ALLOW: Guardian change request submitted with reason")
    void ACT_GUARDIAN_CHANGE_REQUEST_ALLOW() {
        StudentGuardianLink link = StudentGuardianLink.builder()
                .id(new StudentGuardianLinkId(TENANT_ID, LINK_ID))
                .studentProfileId(STUDENT_ID)
                .guardianProfileId(UUID.randomUUID())
                .relationship("FATHER")
                .status("ACTIVE")
                .rowVersion(1)
                .build();

        when(linkRepo.findByIdTenantIdAndIdStudentGuardianLinkId(TENANT_ID, LINK_ID)).thenReturn(Optional.of(link));
        when(linkRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GuardianChangeRequestDto req = new GuardianChangeRequestDto("Court order updating custody arrangement");

        ResponseEntity<GuardianLinkResponse> res = guardianLinkController.requestChange(
                TENANT_ID, LINK_ID, INSTITUTION_ID, req, authFor(TENANT_ID, USER_ID, "admin@univ.edu"));

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody().changeApprovalRequestId());
        verify(auditLogService).record(eq(AuditEventType.GUARDIAN_CHANGE_REQUESTED), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ACT-GUARDIAN_CHANGE_REQUEST-DENY: Unauthorized user cannot request guardian change")
    void ACT_GUARDIAN_CHANGE_REQUEST_DENY() {
        doThrow(new UnauthorizedException("Action GUARDIAN_CHANGE_REQUEST not granted"))
                .when(permissionEvaluatorService).authorize(any(), eq("GUARDIAN_CHANGE_REQUEST"), any(), any());

        GuardianChangeRequestDto req = new GuardianChangeRequestDto("Reason");

        assertThrows(UnauthorizedException.class, () ->
                guardianLinkController.requestChange(TENANT_ID, LINK_ID, INSTITUTION_ID, req, authFor(TENANT_ID, USER_ID, "staff@univ.edu")));
    }

    @Test
    @DisplayName("ACT-GUARDIAN_CHANGE_REQUEST-SCOPE: Foreign tenant request rejected")
    void ACT_GUARDIAN_CHANGE_REQUEST_SCOPE() {
        GuardianChangeRequestDto req = new GuardianChangeRequestDto("Reason");

        assertThrows(UnauthorizedException.class, () ->
                guardianLinkController.requestChange(OTHER_TENANT_ID, LINK_ID, INSTITUTION_ID, req, authFor(TENANT_ID, USER_ID, "admin@univ.edu")));
    }

    @Test
    @DisplayName("ACT-GUARDIAN_CHANGE_REQUEST-GUARD: Blank reason rejected (Rule WF-23)")
    void ACT_GUARDIAN_CHANGE_REQUEST_GUARD() {
        GuardianChangeRequestDto req = new GuardianChangeRequestDto("   ");

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                guardianLinkController.requestChange(TENANT_ID, LINK_ID, INSTITUTION_ID, req, authFor(TENANT_ID, USER_ID, "admin@univ.edu")));

        assertEquals("REASON_REQUIRED", ex.getErrorCode());
    }

    // ==========================================
    // Story 19: GUARDIAN_CHANGE_APPROVE
    // ==========================================

    @Test
    @DisplayName("ACT-GUARDIAN_CHANGE_APPROVE-ALLOW: Principal approves guardian change; old guardian blocked")
    void ACT_GUARDIAN_CHANGE_APPROVE_ALLOW() {
        StudentGuardianLink link = StudentGuardianLink.builder()
                .id(new StudentGuardianLinkId(TENANT_ID, LINK_ID))
                .studentProfileId(STUDENT_ID)
                .guardianProfileId(UUID.randomUUID())
                .status("ACTIVE")
                .changeApprovalRequestId(UUID.randomUUID())
                .rowVersion(1)
                .build();

        when(linkRepo.findByIdTenantIdAndIdStudentGuardianLinkId(TENANT_ID, LINK_ID)).thenReturn(Optional.of(link));
        when(linkRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GuardianChangeDecisionRequest req = new GuardianChangeDecisionRequest(true, "Verified official documents");

        ResponseEntity<GuardianLinkResponse> res = guardianLinkController.decideChange(
                TENANT_ID, LINK_ID, INSTITUTION_ID, req, authFor(TENANT_ID, USER_ID, "principal@college.edu"));

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("BLOCKED", res.getBody().status());
        assertNotNull(res.getBody().blockedAt());
        verify(auditLogService).record(eq(AuditEventType.GUARDIAN_CHANGE_APPROVED), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ACT-GUARDIAN_CHANGE_APPROVE-DENY: Non-principal denied guardian change approval")
    void ACT_GUARDIAN_CHANGE_APPROVE_DENY() {
        doThrow(new UnauthorizedException("Action GUARDIAN_CHANGE_APPROVE not granted"))
                .when(permissionEvaluatorService).authorize(any(), eq("GUARDIAN_CHANGE_APPROVE"), any(), any());

        GuardianChangeDecisionRequest req = new GuardianChangeDecisionRequest(true, "Remark");

        assertThrows(UnauthorizedException.class, () ->
                guardianLinkController.decideChange(TENANT_ID, LINK_ID, INSTITUTION_ID, req, authFor(TENANT_ID, USER_ID, "staff@univ.edu")));
    }

    @Test
    @DisplayName("ACT-GUARDIAN_CHANGE_APPROVE-SCOPE: Other tenant approval rejected")
    void ACT_GUARDIAN_CHANGE_APPROVE_SCOPE() {
        GuardianChangeDecisionRequest req = new GuardianChangeDecisionRequest(true, "Remark");

        assertThrows(UnauthorizedException.class, () ->
                guardianLinkController.decideChange(OTHER_TENANT_ID, LINK_ID, INSTITUTION_ID, req, authFor(TENANT_ID, USER_ID, "principal@college.edu")));
    }

    @Test
    @DisplayName("ACT-GUARDIAN_CHANGE_APPROVE-GUARD: Inbox rejection requires mandatory remark")
    void ACT_GUARDIAN_CHANGE_APPROVE_GUARD() {
        UUID approvalReqId = UUID.randomUUID();
        ApprovalRequest approvalReq = ApprovalRequest.builder()
                .id(new ApprovalRequestId(TENANT_ID, approvalReqId))
                .requestedByUserId(UUID.randomUUID())
                .processCode("GUARDIAN_CHANGE")
                .subjectType("STUDENT_GUARDIAN_LINK")
                .subjectId(LINK_ID)
                .requesterActiveRole("STAFF")
                .status("PENDING")
                .currentStepNo((short) 1)
                .build();

        ApprovalStep step = ApprovalStep.builder()
                .id(new ApprovalStepId(TENANT_ID, UUID.randomUUID()))
                .approvalRequestId(approvalReqId)
                .stepNo((short) 1)
                .approverRoleCode("PRINCIPAL_COLLEGE")
                .decision("PENDING")
                .build();

        when(approvalRequestRepo.findByIdTenantIdAndIdApprovalRequestId(TENANT_ID, approvalReqId))
                .thenReturn(Optional.of(approvalReq));
        when(approvalStepRepo.findByIdTenantIdAndApprovalRequestIdAndStepNo(TENANT_ID, approvalReqId, (short) 1))
                .thenReturn(Optional.of(step));

        DecideApprovalRequestDto req = new DecideApprovalRequestDto((short) 1, "REJECTED", "   ", 1);

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                approvalInboxController.decideApproval(TENANT_ID, approvalReqId, req, authFor(TENANT_ID, USER_ID, "principal@college.edu")));

        assertEquals("REJECTION_REMARK_REQUIRED", ex.getErrorCode());
    }

    // ==========================================
    // Story 20: APPROVAL_DELEGATE
    // ==========================================

    @Test
    @DisplayName("ACT-APPROVAL_DELEGATE-ALLOW: Delegator delegates approval to same or higher role")
    void ACT_APPROVAL_DELEGATE_ALLOW() {
        Instant now = Instant.now();
        Instant end = now.plus(7, ChronoUnit.DAYS);
        UUID delegateId = UUID.randomUUID();

        when(delegationRepo.save(any(Delegation.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateDelegationRequest req = new CreateDelegationRequest(
                delegateId, "SAME_OR_HIGHER", null, now, end
        );

        ResponseEntity<DelegationResponse> res = delegationController.createDelegation(
                TENANT_ID, req, authFor(TENANT_ID, USER_ID, "principal@college.edu"));

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("ACTIVE", res.getBody().status());
        verify(auditLogService).record(eq(AuditEventType.APPROVAL_DELEGATED), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ACT-APPROVAL_DELEGATE-DENY: Principal without APPROVAL_DELEGATE permission is refused")
    void ACT_APPROVAL_DELEGATE_DENY() {
        doThrow(new UnauthorizedException("Action APPROVAL_DELEGATE not granted"))
                .when(permissionEvaluatorService).authorize(any(), eq("APPROVAL_DELEGATE"), any(), any());

        CreateDelegationRequest req = new CreateDelegationRequest(
                UUID.randomUUID(), "SAME_OR_HIGHER", null, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS)
        );

        assertThrows(UnauthorizedException.class, () ->
                delegationController.createDelegation(TENANT_ID, req, authFor(TENANT_ID, USER_ID, "staff@college.edu")));
    }

    @Test
    @DisplayName("ACT-APPROVAL_DELEGATE-SCOPE: Cross-tenant delegation attempt fails")
    void ACT_APPROVAL_DELEGATE_SCOPE() {
        CreateDelegationRequest req = new CreateDelegationRequest(
                UUID.randomUUID(), "SAME_OR_HIGHER", null, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS)
        );

        assertThrows(UnauthorizedException.class, () ->
                delegationController.createDelegation(OTHER_TENANT_ID, req, authFor(TENANT_ID, USER_ID, "principal@college.edu")));
    }

    @Test
    @DisplayName("ACT-APPROVAL_DELEGATE-GUARD: Merit list approval cannot be delegated (Rule WF-37)")
    void ACT_APPROVAL_DELEGATE_GUARD() {
        CreateDelegationRequest req = new CreateDelegationRequest(
                UUID.randomUUID(), "SAME_OR_HIGHER", "MERIT_LIST_APPROVE", Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS)
        );

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                delegationController.createDelegation(TENANT_ID, req, authFor(TENANT_ID, USER_ID, "principal@college.edu")));

        assertEquals("MERIT_LIST_DELEGATION_PROHIBITED", ex.getErrorCode());
    }

    // ==========================================
    // Story 21: APPROVAL_BULK
    // ==========================================

    @Test
    @DisplayName("ACT-APPROVAL_BULK-ALLOW: Bulk approval executes across valid steps")
    void ACT_APPROVAL_BULK_ALLOW() {
        UUID req1 = UUID.randomUUID();
        UUID req2 = UUID.randomUUID();

        BulkApprovalRequest req = new BulkApprovalRequest(
                List.of(req1, req2), "All documents verified in batch", null
        );

        ResponseEntity<BulkApprovalResponse> res = delegationController.bulkDecide(
                TENANT_ID, req, authFor(TENANT_ID, USER_ID, "principal@college.edu"));

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(2, res.getBody().totalProcessed());
        assertEquals(2, res.getBody().succeeded());
        verify(auditLogService, times(2)).record(eq(AuditEventType.APPROVAL_BULK_DECIDED), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ACT-APPROVAL_BULK-DENY: User without APPROVAL_BULK permission denied")
    void ACT_APPROVAL_BULK_DENY() {
        doThrow(new UnauthorizedException("Action APPROVAL_BULK not granted"))
                .when(permissionEvaluatorService).authorize(any(), eq("APPROVAL_BULK"), any(), any());

        BulkApprovalRequest req = new BulkApprovalRequest(List.of(UUID.randomUUID()), null, null);

        assertThrows(UnauthorizedException.class, () ->
                delegationController.bulkDecide(TENANT_ID, req, authFor(TENANT_ID, USER_ID, "staff@college.edu")));
    }

    @Test
    @DisplayName("ACT-APPROVAL_BULK-SCOPE: Cross-tenant bulk approval denied")
    void ACT_APPROVAL_BULK_SCOPE() {
        BulkApprovalRequest req = new BulkApprovalRequest(List.of(UUID.randomUUID()), null, null);

        assertThrows(UnauthorizedException.class, () ->
                delegationController.bulkDecide(OTHER_TENANT_ID, req, authFor(TENANT_ID, USER_ID, "principal@college.edu")));
    }

    @Test
    @DisplayName("ACT-APPROVAL_BULK-GUARD: DPO approvals cannot be bulk approved (Rule C12)")
    void ACT_APPROVAL_BULK_GUARD() {
        BulkApprovalRequest req = new BulkApprovalRequest(
                List.of(UUID.randomUUID()), "APPROVED", "Batch", "DATA_PROTECTION"
        );

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                delegationController.bulkDecide(TENANT_ID, req, authFor(TENANT_ID, USER_ID, "dpo@univ.edu")));

        assertEquals("DPO_BULK_APPROVAL_PROHIBITED", ex.getErrorCode());
    }

    // ==========================================
    // Story 23: ROLLDOWN_VIEW
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

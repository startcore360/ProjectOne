package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.BlockDecisionRequest;
import com.edu360.api.institution.dto.BlockInstitutionRequest;
import com.edu360.api.institution.dto.CreateInstitutionRequest;
import com.edu360.api.institution.dto.InstitutionBlockResponse;
import com.edu360.api.institution.dto.InstitutionResponse;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.approval.ApprovalInboxService;
import com.edu360.api.approval.ApprovalRequest;
import com.edu360.api.approval.ApprovalRequestId;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;

/**
 * Action & Guard tests for:
 * 1. INSTITUTION_CREATE_DRAFT (Sprint 1 Story #25 / WF-16, WF-14)
 *    - ACT-INSTITUTION_CREATE_DRAFT-ALLOW
 *    - ACT-INSTITUTION_CREATE_DRAFT-DENY
 *    - ACT-INSTITUTION_CREATE_DRAFT-SCOPE
 *    - ACT-INSTITUTION_CREATE_DRAFT-GUARD (duplicate check & plan limits)
 *
 * 2. INSTITUTION_ACTIVATE (Sprint 1 Story #26 / WF-16, Rule C7)
 *    - ACT-INSTITUTION_ACTIVATE-ALLOW
 *    - ACT-INSTITUTION_ACTIVATE-DENY
 *    - ACT-INSTITUTION_ACTIVATE-SCOPE
 *    - ACT-INSTITUTION_ACTIVATE-GUARD (refusal under rule C7 if approver missing)
 */
class InstitutionControllerTest {

    private InstitutionRepository institutionRepository;
    private LeaveApproverSettingRepository leaveApproverSettingRepository;
    private TenantRepository tenantRepository;
    private PlanRepository planRepository;
    private PermissionEvaluatorService permissionEvaluatorService;
    private AuditLogService auditLogService;
    private ApprovalInboxService approvalInboxService;
    private InstitutionService institutionService;
    private InstitutionController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        institutionRepository = mock(InstitutionRepository.class);
        leaveApproverSettingRepository = mock(LeaveApproverSettingRepository.class);
        tenantRepository = mock(TenantRepository.class);
        planRepository = mock(PlanRepository.class);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);
        auditLogService = mock(AuditLogService.class);
        approvalInboxService = mock(ApprovalInboxService.class);

        institutionService = new InstitutionService(
                institutionRepository,
                leaveApproverSettingRepository,
                tenantRepository,
                planRepository,
                permissionEvaluatorService,
                auditLogService,
                mock(com.edu360.api.approval.ApprovalRequestRepository.class),
                mock(com.edu360.api.approval.ApprovalStepRepository.class),
                approvalInboxService
        );

        controller = new InstitutionController(institutionService);
        TenantContext.set(TENANT_ID, IsolationMode.SHARED, null, "ALL", java.util.Set.of(), "UNIVERSITY_ADMIN", ScopeType.TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Authentication authenticatedTenantUser(UUID tenantId, UUID userId) {
        TenantPrincipal principal = new TenantPrincipal(
                tenantId,
                userId,
                com.edu360.api.identity.UserType.STAFF,
                "Admin User",
                false
        );
        return new UsernamePasswordAuthenticationToken(principal, "n/a", java.util.Collections.emptyList());
    }

    // =========================================================================
    // INSTITUTION_CREATE_DRAFT (Story #25)
    // =========================================================================

    @Test
    @DisplayName("ACT-INSTITUTION_CREATE_DRAFT-ALLOW: University Admin creates draft institution successfully")
    void actInstitutionCreateDraft_allow() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        CreateInstitutionRequest req = new CreateInstitutionRequest(
                "COLLEGE", "NBC-01", "Northbridge College of Science", false,
                "124 Knowledge Park", "Bengaluru", "Karnataka", "560001", "principal@northbridge.edu.in", "{INST}-{YYYY}-{SEQ:4}", null
        );

        when(institutionRepository.existsByIdTenantIdAndInstitutionCode(TENANT_ID, "NBC-01")).thenReturn(false);
        when(institutionRepository.existsByIdTenantIdAndInstitutionName(TENANT_ID, "Northbridge College of Science")).thenReturn(false);
        when(institutionRepository.countByIdTenantIdAndStatusNot(TENANT_ID, "ARCHIVED")).thenReturn(1L);

        Plan plan = Plan.builder().planId(PLAN_ID).planName("University Plan").maxInstitutions(10).build();
        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).planId(PLAN_ID).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        when(institutionRepository.save(any(Institution.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<InstitutionResponse> response = controller.createDraft(TENANT_ID, req, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().institutionCode()).isEqualTo("NBC-01");
        assertThat(response.getBody().institutionName()).isEqualTo("Northbridge College of Science");
        assertThat(response.getBody().status()).isEqualTo("DRAFT");
        assertThat(response.getBody().tenantId()).isEqualTo(TENANT_ID);

        verify(permissionEvaluatorService).authorize(
                eq(new RbacPrincipal(TENANT_ID, USER_ID)),
                eq("INSTITUTION_CREATE_DRAFT"),
                eq(RbacScope.tenant()),
                eq(RbacTarget.NONE)
        );
    }

    @Test
    @DisplayName("ACT-INSTITUTION_CREATE_DRAFT-DENY: Role lacking INSTITUTION_CREATE_DRAFT is rejected with 403")
    void actInstitutionCreateDraft_deny() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        CreateInstitutionRequest req = new CreateInstitutionRequest(
                "COLLEGE", "NBC-01", "Northbridge College of Science", false, null, null, null, null, null, "{INST}-{YYYY}-{SEQ:4}", null
        );

        doThrow(new UnauthorizedException("The active role does not hold action INSTITUTION_CREATE_DRAFT."))
                .when(permissionEvaluatorService)
                .authorize(any(), eq("INSTITUTION_CREATE_DRAFT"), any(), any());

        assertThatThrownBy(() -> controller.createDraft(TENANT_ID, req, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("INSTITUTION_CREATE_DRAFT");
    }

    @Test
    @DisplayName("ACT-INSTITUTION_CREATE_DRAFT-SCOPE: Cross-tenant access is rejected with 403")
    void actInstitutionCreateDraft_scope() {
        UUID foreignTenantId = UUID.randomUUID();
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        CreateInstitutionRequest req = new CreateInstitutionRequest(
                "COLLEGE", "NBC-01", "Northbridge College", false, null, null, null, null, null, "{INST}-{YYYY}-{SEQ:4}", null
        );

        assertThatThrownBy(() -> controller.createDraft(foreignTenantId, req, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    @DisplayName("ACT-INSTITUTION_CREATE_DRAFT-GUARD: Duplicate code throws 409 Conflict")
    void actInstitutionCreateDraft_guard_duplicateCode() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        CreateInstitutionRequest req = new CreateInstitutionRequest(
                "COLLEGE", "NBC-01", "Northbridge College", false, null, null, null, null, null, "{INST}-{YYYY}-{SEQ:4}", null
        );

        when(institutionRepository.existsByIdTenantIdAndInstitutionCode(TENANT_ID, "NBC-01")).thenReturn(true);

        assertThatThrownBy(() -> controller.createDraft(TENANT_ID, req, auth))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("NBC-01");
    }

    @Test
    @DisplayName("ACT-INSTITUTION_CREATE_DRAFT-GUARD: Exceeding plan limit throws PLAN_LIMIT_REACHED (WF-14)")
    void actInstitutionCreateDraft_guard_planLimitReached() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        CreateInstitutionRequest req = new CreateInstitutionRequest(
                "COLLEGE", "NBC-02", "New Campus", false, null, null, null, null, null, "{INST}-{YYYY}-{SEQ:4}", null
        );

        when(institutionRepository.countByIdTenantIdAndStatusNot(TENANT_ID, "ARCHIVED")).thenReturn(5L);
        Plan plan = Plan.builder().planId(PLAN_ID).planName("Basic Plan").maxInstitutions(5).build();
        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).planId(PLAN_ID).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> controller.createDraft(TENANT_ID, req, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(ex -> {
                    BusinessRuleViolationException bre = (BusinessRuleViolationException) ex;
                    assertThat(bre.getErrorCode()).isEqualTo("PLAN_LIMIT_REACHED");
                    assertThat(bre.getMessage()).contains("WF-14");
                });
    }

    // =========================================================================
    // INSTITUTION_ACTIVATE (Story #26)
    // =========================================================================

    @Test
    @DisplayName("ACT-INSTITUTION_ACTIVATE-ALLOW: Vice Chancellor activates institution when Principal leave approver is set (Rule C7)")
    void actInstitutionActivate_allow() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        Institution draftInstitution = Institution.builder()
                .id(new InstitutionId(TENANT_ID, INSTITUTION_ID))
                .institutionType("COLLEGE")
                .institutionCode("NBC-01")
                .institutionName("Northbridge College of Science")
                .status("DRAFT")
                .rowVersion(1)
                .build();

        when(institutionRepository.findByIdTenantIdAndIdInstitutionId(TENANT_ID, INSTITUTION_ID))
                .thenReturn(Optional.of(draftInstitution));
        when(leaveApproverSettingRepository.existsByIdTenantIdAndSubjectRoleAndInstitutionIdAndStatus(
                TENANT_ID, "PRINCIPAL", INSTITUTION_ID, "ACTIVE"))
                .thenReturn(true);
        when(institutionRepository.save(any(Institution.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID activationRequestId = UUID.randomUUID();
        ApprovalRequest activationRequest = ApprovalRequest.builder()
                .id(new ApprovalRequestId(TENANT_ID, activationRequestId))
                .currentStepNo((short) 1)
                .status("PENDING")
                .processCode("INSTITUTION_ACTIVATION")
                .build();
        when(approvalInboxService.pendingRequestFor(TENANT_ID, "INSTITUTION", INSTITUTION_ID, "INSTITUTION_ACTIVATION"))
                .thenReturn(Optional.of(activationRequest));
        doAnswer(inv -> {
            draftInstitution.setStatus("ACTIVE");
            draftInstitution.setActivatedAt(Instant.now());
            draftInstitution.setRowVersion(2);
            return null;
        }).when(approvalInboxService).decideApproval(eq(TENANT_ID), eq(activationRequestId), any(), any(UUID.class));

        ResponseEntity<InstitutionResponse> response = controller.activateInstitution(TENANT_ID, INSTITUTION_ID, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("ACTIVE");
        assertThat(response.getBody().activatedAt()).isNotNull();
        assertThat(response.getBody().rowVersion()).isEqualTo(2);

        verify(permissionEvaluatorService).authorize(
                eq(new RbacPrincipal(TENANT_ID, USER_ID)),
                eq("INSTITUTION_ACTIVATE"),
                eq(RbacScope.tenant()),
                eq(RbacTarget.NONE)
        );
    }

    @Test
    @DisplayName("ACT-INSTITUTION_ACTIVATE-DENY: Role lacking INSTITUTION_ACTIVATE is rejected with 403")
    void actInstitutionActivate_deny() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);

        doThrow(new UnauthorizedException("The active role does not hold action INSTITUTION_ACTIVATE."))
                .when(permissionEvaluatorService)
                .authorize(any(), eq("INSTITUTION_ACTIVATE"), any(), any());

        assertThatThrownBy(() -> controller.activateInstitution(TENANT_ID, INSTITUTION_ID, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("INSTITUTION_ACTIVATE");
    }

    @Test
    @DisplayName("ACT-INSTITUTION_ACTIVATE-SCOPE: Mismatched tenant in URL is rejected with 403")
    void actInstitutionActivate_scope() {
        UUID foreignTenantId = UUID.randomUUID();
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);

        assertThatThrownBy(() -> controller.activateInstitution(foreignTenantId, INSTITUTION_ID, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    @DisplayName("ACT-INSTITUTION_ACTIVATE-GUARD: Refused with GO_LIVE_BLOCKED when Principal's-leave approver is missing (Rule C7)")
    void actInstitutionActivate_guard_missingLeaveApprover() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        Institution draftInstitution = Institution.builder()
                .id(new InstitutionId(TENANT_ID, INSTITUTION_ID))
                .institutionType("COLLEGE")
                .institutionCode("NBC-01")
                .institutionName("Northbridge College of Science")
                .status("DRAFT")
                .build();

        when(institutionRepository.findByIdTenantIdAndIdInstitutionId(TENANT_ID, INSTITUTION_ID))
                .thenReturn(Optional.of(draftInstitution));
        // Principal leave approver is NOT set:
        when(leaveApproverSettingRepository.existsByIdTenantIdAndSubjectRoleAndInstitutionIdAndStatus(
                TENANT_ID, "PRINCIPAL", INSTITUTION_ID, "ACTIVE"))
                .thenReturn(false);

        UUID activationRequestId = UUID.randomUUID();
        ApprovalRequest activationRequest = ApprovalRequest.builder()
                .id(new ApprovalRequestId(TENANT_ID, activationRequestId))
                .currentStepNo((short) 1)
                .status("PENDING")
                .processCode("INSTITUTION_ACTIVATION")
                .build();
        when(approvalInboxService.pendingRequestFor(TENANT_ID, "INSTITUTION", INSTITUTION_ID, "INSTITUTION_ACTIVATION"))
                .thenReturn(Optional.of(activationRequest));
        doThrow(new BusinessRuleViolationException("GO_LIVE_BLOCKED",
                "Principal's-leave approver not set for institution " + INSTITUTION_ID + " [C7]"))
                .when(approvalInboxService).decideApproval(eq(TENANT_ID), eq(activationRequestId), any(), any(UUID.class));

        assertThatThrownBy(() -> controller.activateInstitution(TENANT_ID, INSTITUTION_ID, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(ex -> {
                    BusinessRuleViolationException bre = (BusinessRuleViolationException) ex;
                    assertThat(bre.getErrorCode()).isEqualTo("GO_LIVE_BLOCKED");
                    assertThat(bre.getMessage()).contains("[C7]");
                });
    }

    // =========================================================================
    // INSTITUTION_BLOCK_REQUEST (Story #27 / Rule RL-01)
    // =========================================================================

    @Test
    @DisplayName("ACT-INSTITUTION_BLOCK_REQUEST-ALLOW: University Admin submits block request with reason")
    void actInstitutionBlockRequest_allow() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        BlockInstitutionRequest req = new BlockInstitutionRequest("Severe statutory non-compliance audit findings");

        Institution activeInst = Institution.builder()
                .id(new InstitutionId(TENANT_ID, INSTITUTION_ID))
                .institutionType("COLLEGE")
                .institutionCode("NBC-01")
                .institutionName("Northbridge College of Science")
                .status("ACTIVE")
                .build();

        when(institutionRepository.findByIdTenantIdAndIdInstitutionId(TENANT_ID, INSTITUTION_ID))
                .thenReturn(Optional.of(activeInst));

        ResponseEntity<InstitutionBlockResponse> response = controller.requestBlock(TENANT_ID, INSTITUTION_ID, req, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().institutionId()).isEqualTo(INSTITUTION_ID);
        assertThat(response.getBody().message()).contains("Severe statutory non-compliance");

        verify(permissionEvaluatorService).authorize(
                eq(new RbacPrincipal(TENANT_ID, USER_ID)),
                eq("INSTITUTION_BLOCK_REQUEST"),
                eq(RbacScope.tenant()),
                eq(RbacTarget.of(INSTITUTION_ID))
        );

        verify(auditLogService).record(
                eq(AuditEventType.INSTITUTION_BLOCK_REQUESTED),
                any(),
                eq(USER_ID.toString()),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("ACT-INSTITUTION_BLOCK_REQUEST-DENY: Role lacking INSTITUTION_BLOCK_REQUEST is refused with 403")
    void actInstitutionBlockRequest_deny() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        BlockInstitutionRequest req = new BlockInstitutionRequest("Severe audit findings");

        doThrow(new UnauthorizedException("The active role does not hold action INSTITUTION_BLOCK_REQUEST."))
                .when(permissionEvaluatorService)
                .authorize(any(), eq("INSTITUTION_BLOCK_REQUEST"), any(), any());

        assertThatThrownBy(() -> controller.requestBlock(TENANT_ID, INSTITUTION_ID, req, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("INSTITUTION_BLOCK_REQUEST");
    }

    @Test
    @DisplayName("ACT-INSTITUTION_BLOCK_REQUEST-SCOPE: Mismatched tenant in URL is rejected with 403")
    void actInstitutionBlockRequest_scope() {
        UUID foreignTenantId = UUID.randomUUID();
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        BlockInstitutionRequest req = new BlockInstitutionRequest("Severe audit findings");

        assertThatThrownBy(() -> controller.requestBlock(foreignTenantId, INSTITUTION_ID, req, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    @DisplayName("ACT-INSTITUTION_BLOCK_REQUEST-GUARD: Requesting block for non-active (DRAFT) institution is refused with 422")
    void actInstitutionBlockRequest_guard_draftInstitutionRefused() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        BlockInstitutionRequest req = new BlockInstitutionRequest("Severe audit findings");

        Institution draftInst = Institution.builder()
                .id(new InstitutionId(TENANT_ID, INSTITUTION_ID))
                .status("DRAFT")
                .build();

        when(institutionRepository.findByIdTenantIdAndIdInstitutionId(TENANT_ID, INSTITUTION_ID))
                .thenReturn(Optional.of(draftInst));

        assertThatThrownBy(() -> controller.requestBlock(TENANT_ID, INSTITUTION_ID, req, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(ex -> {
                    BusinessRuleViolationException bre = (BusinessRuleViolationException) ex;
                    assertThat(bre.getErrorCode()).isEqualTo("INVALID_INSTITUTION_STATUS");
                });
    }

    // =========================================================================
    // INSTITUTION_BLOCK_APPROVE (Story #28 / Rule WF-15)
    // =========================================================================

    @Test
    @DisplayName("ACT-INSTITUTION_BLOCK_APPROVE-ALLOW: Vice Chancellor approves block -> status transitions to BLOCKED")
    void actInstitutionBlockApprove_allow() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        BlockDecisionRequest decision = new BlockDecisionRequest(true, "Confirmed audit violations");

        Institution activeInst = Institution.builder()
                .id(new InstitutionId(TENANT_ID, INSTITUTION_ID))
                .institutionType("COLLEGE")
                .institutionCode("NBC-01")
                .institutionName("Northbridge College of Science")
                .status("ACTIVE")
                .rowVersion(1)
                .build();

        when(institutionRepository.findByIdTenantIdAndIdInstitutionId(TENANT_ID, INSTITUTION_ID))
                .thenReturn(Optional.of(activeInst));

        UUID blockRequestId = UUID.randomUUID();
        ApprovalRequest blockRequest = ApprovalRequest.builder()
                .id(new ApprovalRequestId(TENANT_ID, blockRequestId))
                .currentStepNo((short) 1)
                .status("PENDING")
                .processCode("INSTITUTION_BLOCK")
                .build();
        when(approvalInboxService.pendingRequestFor(TENANT_ID, "INSTITUTION", INSTITUTION_ID, "INSTITUTION_BLOCK"))
                .thenReturn(Optional.of(blockRequest));
        doAnswer(inv -> {
            activeInst.setStatus("BLOCKED");
            activeInst.setBlockedAt(Instant.now());
            activeInst.setRowVersion(2);
            auditLogService.record(AuditEventType.INSTITUTION_BLOCKED, null, USER_ID.toString(), null, "blocked");
            return null;
        }).when(approvalInboxService).decideApproval(eq(TENANT_ID), eq(blockRequestId), any(), any(UUID.class));

        ResponseEntity<InstitutionBlockResponse> response = controller.decideBlock(TENANT_ID, INSTITUTION_ID, decision, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("BLOCKED");
        assertThat(response.getBody().blockedAt()).isNotNull();
        assertThat(activeInst.getStatus()).isEqualTo("BLOCKED");
        assertThat(activeInst.getRowVersion()).isEqualTo(2);

        verify(permissionEvaluatorService).authorize(
                eq(new RbacPrincipal(TENANT_ID, USER_ID)),
                eq("INSTITUTION_BLOCK_APPROVE"),
                eq(RbacScope.tenant()),
                eq(RbacTarget.NONE)
        );

        verify(auditLogService).record(
                eq(AuditEventType.INSTITUTION_BLOCKED),
                any(),
                eq(USER_ID.toString()),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("ACT-INSTITUTION_BLOCK_APPROVE-DENY: Non-VC role attempting to approve block is refused with 403")
    void actInstitutionBlockApprove_deny() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        BlockDecisionRequest decision = new BlockDecisionRequest(true, "Approved");

        doThrow(new UnauthorizedException("The active role does not hold action INSTITUTION_BLOCK_APPROVE."))
                .when(permissionEvaluatorService)
                .authorize(any(), eq("INSTITUTION_BLOCK_APPROVE"), any(), any());

        assertThatThrownBy(() -> controller.decideBlock(TENANT_ID, INSTITUTION_ID, decision, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("INSTITUTION_BLOCK_APPROVE");
    }

    @Test
    @DisplayName("ACT-INSTITUTION_BLOCK_APPROVE-SCOPE: Cross-tenant block approval is refused with 403")
    void actInstitutionBlockApprove_scope() {
        UUID foreignTenantId = UUID.randomUUID();
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        BlockDecisionRequest decision = new BlockDecisionRequest(true, "Approved");

        assertThatThrownBy(() -> controller.decideBlock(foreignTenantId, INSTITUTION_ID, decision, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    @DisplayName("ACT-INSTITUTION_BLOCK_APPROVE-GUARD: Vice Chancellor rejects block -> institution remains ACTIVE")
    void actInstitutionBlockApprove_guard_rejectedKeepsActive() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        BlockDecisionRequest decision = new BlockDecisionRequest(false, "Remediation plan submitted and accepted");

        Institution activeInst = Institution.builder()
                .id(new InstitutionId(TENANT_ID, INSTITUTION_ID))
                .institutionType("COLLEGE")
                .institutionCode("NBC-01")
                .institutionName("Northbridge College of Science")
                .status("ACTIVE")
                .rowVersion(1)
                .build();

        when(institutionRepository.findByIdTenantIdAndIdInstitutionId(TENANT_ID, INSTITUTION_ID))
                .thenReturn(Optional.of(activeInst));

        UUID blockRequestId = UUID.randomUUID();
        ApprovalRequest blockRequest = ApprovalRequest.builder()
                .id(new ApprovalRequestId(TENANT_ID, blockRequestId))
                .currentStepNo((short) 1)
                .status("PENDING")
                .processCode("INSTITUTION_BLOCK")
                .build();
        when(approvalInboxService.pendingRequestFor(TENANT_ID, "INSTITUTION", INSTITUTION_ID, "INSTITUTION_BLOCK"))
                .thenReturn(Optional.of(blockRequest));


        ResponseEntity<InstitutionBlockResponse> response = controller.decideBlock(TENANT_ID, INSTITUTION_ID, decision, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("ACTIVE");
        assertThat(activeInst.getStatus()).isEqualTo("ACTIVE");

        verify(auditLogService).record(
                eq(AuditEventType.INSTITUTION_BLOCK_REJECTED),
                any(),
                eq(USER_ID.toString()),
                any(),
                any()
        );
    }
}

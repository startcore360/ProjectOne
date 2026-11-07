package com.edu360.api.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;
import com.edu360.api.approval.dto.ApprovalInboxResponseDto;
import com.edu360.api.approval.dto.ApprovalSummaryDto;
import com.edu360.api.approval.dto.DecideApprovalRequestDto;
import com.edu360.api.approval.dto.DecideApprovalResponseDto;

public class ApprovalInboxControllerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID STEP_ID = UUID.randomUUID();

    private ApprovalRequestRepository requestRepo;
    private ApprovalStepRepository stepRepo;
    private AuditLogService auditLogService;
    private ApprovalInboxService inboxService;
    private ApprovalInboxController controller;

    @BeforeEach
    void setUp() {
        requestRepo = mock(ApprovalRequestRepository.class);
        stepRepo = mock(ApprovalStepRepository.class);
        auditLogService = mock(AuditLogService.class);

        inboxService = new ApprovalInboxService(requestRepo, stepRepo, auditLogService);
        controller = new ApprovalInboxController(inboxService);
    }

    private Authentication authenticatedTenantUser(UUID tenantId, UUID userId) {
        TenantPrincipal principal = new TenantPrincipal(
                tenantId,
                userId,
                UserType.STAFF,
                "Approver User",
                false
        );
        return new UsernamePasswordAuthenticationToken(principal, "n/a", Collections.emptyList());
    }

    @Test
    @DisplayName("ACT-APPROVAL_INBOX-ALLOW: Approver lists pending steps")
    void actApprovalInbox_allow() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);

        ApprovalStep step = ApprovalStep.builder()
                .id(new ApprovalStepId(TENANT_ID, STEP_ID))
                .approvalRequestId(REQUEST_ID)
                .stepNo((short) 1)
                .approverRoleCode("PRINCIPAL_COLLEGE")
                .approverUserId(USER_ID)
                .decision("PENDING")
                .build();

        ApprovalRequest req = ApprovalRequest.builder()
                .id(new ApprovalRequestId(TENANT_ID, REQUEST_ID))
                .requestedByUserId(UUID.randomUUID()) // Different user, not self
                .processCode("LEAVE_FACULTY")
                .subjectType("LEAVE_REQUEST")
                .subjectId(UUID.randomUUID())
                .requesterActiveRole("PROFESSOR")
                .status("PENDING")
                .currentStepNo((short) 1)
                .build();

        when(stepRepo.findByIdTenantIdAndApproverUserIdAndDecision(TENANT_ID, USER_ID, "PENDING"))
                .thenReturn(List.of(step));
        when(requestRepo.findByIdTenantIdAndIdApprovalRequestId(TENANT_ID, REQUEST_ID))
                .thenReturn(Optional.of(req));

        ResponseEntity<ApprovalInboxResponseDto> response = controller.listPendingApprovals(TENANT_ID, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().items()).hasSize(1);
        assertThat(response.getBody().items().get(0).approverRoleCode()).isEqualTo("PRINCIPAL_COLLEGE");
    }

    @Test
    @DisplayName("ACT-APPROVAL_INBOX-DENY: Unauthenticated call is refused")
    void actApprovalInbox_deny() {
        assertThatThrownBy(() -> controller.listPendingApprovals(TENANT_ID, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-APPROVAL_INBOX-SCOPE: Tenant ID mismatch is rejected with 401/403")
    void actApprovalInbox_scope() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        UUID foreignTenant = UUID.randomUUID();

        assertThatThrownBy(() -> controller.listPendingApprovals(foreignTenant, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Tenant ID in path does not match");
    }

    @Test
    @DisplayName("ACT-APPROVAL_DECIDE-GUARD: Self-approval is strictly forbidden (Addendum rule 11)")
    void actApprovalDecide_guard_selfApproval() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);

        ApprovalRequest req = ApprovalRequest.builder()
                .id(new ApprovalRequestId(TENANT_ID, REQUEST_ID))
                .requestedByUserId(USER_ID) // Self-request!
                .processCode("LEAVE_FACULTY")
                .subjectType("LEAVE_REQUEST")
                .subjectId(UUID.randomUUID())
                .requesterActiveRole("PROFESSOR")
                .status("PENDING")
                .currentStepNo((short) 1)
                .build();

        when(requestRepo.findByIdTenantIdAndIdApprovalRequestId(TENANT_ID, REQUEST_ID))
                .thenReturn(Optional.of(req));

        DecideApprovalRequestDto requestDto = new DecideApprovalRequestDto((short) 1, "APPROVED", null, 1);

        assertThatThrownBy(() -> controller.decideApproval(TENANT_ID, REQUEST_ID, requestDto, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("A principal may not approve or decide their own request");
    }

    @Test
    @DisplayName("ACT-APPROVAL_DECIDE-GUARD: Rejection requires mandatory remark")
    void actApprovalDecide_guard_rejectionRemark() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);

        ApprovalRequest req = ApprovalRequest.builder()
                .id(new ApprovalRequestId(TENANT_ID, REQUEST_ID))
                .requestedByUserId(UUID.randomUUID()) // Different user
                .processCode("LEAVE_FACULTY")
                .subjectType("LEAVE_REQUEST")
                .subjectId(UUID.randomUUID())
                .requesterActiveRole("PROFESSOR")
                .status("PENDING")
                .currentStepNo((short) 1)
                .build();

        ApprovalStep step = ApprovalStep.builder()
                .id(new ApprovalStepId(TENANT_ID, STEP_ID))
                .approvalRequestId(REQUEST_ID)
                .stepNo((short) 1)
                .approverRoleCode("PRINCIPAL_COLLEGE")
                .decision("PENDING")
                .build();

        when(requestRepo.findByIdTenantIdAndIdApprovalRequestId(TENANT_ID, REQUEST_ID))
                .thenReturn(Optional.of(req));
        when(stepRepo.findByIdTenantIdAndApprovalRequestIdAndStepNo(TENANT_ID, REQUEST_ID, (short) 1))
                .thenReturn(Optional.of(step));

        // Missing remark on rejection
        DecideApprovalRequestDto requestDto = new DecideApprovalRequestDto((short) 1, "REJECTED", "", 1);

        assertThatThrownBy(() -> controller.decideApproval(TENANT_ID, REQUEST_ID, requestDto, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("A remark is mandatory when rejecting");
    }
}

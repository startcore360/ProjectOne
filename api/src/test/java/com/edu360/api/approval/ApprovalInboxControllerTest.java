package com.edu360.api.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import com.edu360.api.approval.dto.DecideApprovalRequestDto;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.SelfApprovalForbiddenException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.TenantContext;

/**
 * The inbox controller over the real services and evaluator (R3): ALLOW/DENY/SCOPE/GUARD of
 * APPROVALS_INBOX and the decision endpoint. Step-role, delegation, roll-down and scope rules are
 * in {@link ApprovalEngineB2Test}.
 */
class ApprovalInboxControllerTest {

    private ApprovalTestWorld t;
    private ApprovalInboxController controller;
    private UUID inst;
    private UUID principal;
    private UUID requester;

    @BeforeEach
    void setUp() {
        t = new ApprovalTestWorld();
        controller = new ApprovalInboxController(t.inbox);
        inst = t.institution("COLLEGE", "ACTIVE");
        principal = t.staff("Principal", "PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, inst);
        requester = t.staff("Admin", "COLLEGE_ADMIN", ScopeType.INSTITUTION, inst);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ApprovalRequest pending(UUID by) {
        return t.guardianRequest(t.link(t.student(inst).getStudentProfileId()), inst, by, "PRINCIPAL_COLLEGE");
    }

    @Test
    @DisplayName("ACT-APPROVAL_INBOX-ALLOW: Approver lists pending steps")
    void actApprovalInbox_allow() {
        pending(requester);
        Authentication auth = t.auth(t.as(principal));

        var response = controller.listPendingApprovals(t.tenantId, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).hasSize(1);
        assertThat(response.getBody().items().get(0).approverRoleCode()).isEqualTo("PRINCIPAL_COLLEGE");
    }

    @Test
    @DisplayName("ACT-APPROVAL_INBOX-DENY: Unauthenticated call is refused")
    void actApprovalInbox_deny() {
        assertThatThrownBy(() -> controller.listPendingApprovals(t.tenantId, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-APPROVAL_INBOX-SCOPE: Tenant ID mismatch is rejected with 403")
    void actApprovalInbox_scope() {
        Authentication auth = t.auth(t.as(principal));
        assertThatThrownBy(() -> controller.listPendingApprovals(UUID.randomUUID(), auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Tenant ID in path does not match");
    }

    @Test
    @DisplayName("ACT-APPROVAL_DECIDE-GUARD: Self-approval is strictly forbidden (Addendum rule 11)")
    void actApprovalDecide_guard_selfApproval() {
        ApprovalRequest own = pending(principal);
        Authentication auth = t.auth(t.as(principal));

        assertThatThrownBy(() -> controller.decideApproval(t.tenantId, own.getId().getApprovalRequestId(),
                new DecideApprovalRequestDto((short) 1, "APPROVED", null, 1), auth))
                .isInstanceOf(SelfApprovalForbiddenException.class)
                .hasMessageContaining("A principal may not approve or decide their own request");
        assertThat(own.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("ACT-APPROVAL_DECIDE-GUARD: Rejection requires mandatory remark")
    void actApprovalDecide_guard_rejectionRemark() {
        ApprovalRequest r = pending(requester);
        Authentication auth = t.auth(t.as(principal));

        assertThatThrownBy(() -> controller.decideApproval(t.tenantId, r.getId().getApprovalRequestId(),
                new DecideApprovalRequestDto((short) 1, "REJECTED", "", 1), auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("A remark is mandatory when rejecting");
        assertThat(r.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("ACT-APPROVAL_DECIDE-ALLOW: the step's role holder approves through the controller; state and audit saved")
    void actApprovalDecide_allow() {
        ApprovalRequest r = pending(requester);
        Authentication auth = t.auth(t.as(principal));

        var res = controller.decideApproval(t.tenantId, r.getId().getApprovalRequestId(),
                new DecideApprovalRequestDto((short) 1, "APPROVED", null, 1), auth);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(t.audited("decided as APPROVED by PRINCIPAL_COLLEGE")).isTrue();
    }
}

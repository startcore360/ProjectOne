package com.edu360.api.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import com.edu360.api.approval.dto.BulkApprovalRequest;
import com.edu360.api.approval.dto.CreateDelegationRequest;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.TenantContext;

/** The delegation and bulk controller over the real services and evaluator (R3). */
class ApprovalDelegationControllerTest {

    private ApprovalTestWorld t;
    private ApprovalDelegationController controller;
    private UUID inst;
    private UUID principal;
    private UUID vp;

    @BeforeEach
    void setUp() {
        t = new ApprovalTestWorld();
        controller = new ApprovalDelegationController(t.delegation);
        inst = t.institution("COLLEGE", "ACTIVE");
        principal = t.staff("Principal", "PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, inst);
        vp = t.staff("Vice Principal", "VICE_PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, inst);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CreateDelegationRequest request(UUID delegate, Instant from, Instant to) {
        return new CreateDelegationRequest(delegate, "LEAVE", "GUARDIAN_CHANGE_APPROVE", from, to);
    }

    @Test
    @DisplayName("ALLOW: the delegator creates a delegation (201), lists it and cancels it")
    void allow_createListCancel() {
        Authentication auth = t.auth(t.as(principal));
        var created = controller.createDelegation(t.tenantId,
                request(vp, Instant.now().minusSeconds(60), Instant.now().plus(1, ChronoUnit.DAYS)), auth);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(controller.listDelegations(t.tenantId, auth).getBody()).hasSize(1);

        var cancelled = controller.cancelDelegation(t.tenantId, created.getBody().delegationId(), auth);
        assertThat(cancelled.getBody().status()).isEqualTo("CANCELLED");
        assertThat(t.delegations.get(0).getCancelledBy()).isEqualTo(principal);
    }

    @Test
    @DisplayName("GUARD: self-delegation is prohibited")
    void guard_selfDelegation() {
        Authentication auth = t.auth(t.as(principal));
        assertThatThrownBy(() -> controller.createDelegation(t.tenantId,
                request(principal, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS)), auth))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("themselves");
    }

    @Test
    @DisplayName("GUARD: the end must be after the start")
    void guard_invalidDateOrder() {
        Authentication auth = t.auth(t.as(principal));
        assertThatThrownBy(() -> controller.createDelegation(t.tenantId,
                request(vp, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now()), auth))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("strictly after");
    }

    @Test
    @DisplayName("SCOPE: a tenant mismatch is rejected")
    void scope_tenantMismatch() {
        Authentication auth = t.auth(t.as(principal));
        assertThatThrownBy(() -> controller.listDelegations(UUID.randomUUID(), auth)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("BS16: a delegator who lacks the delegated action is refused (422); an unknown delegate is 404")
    void bs16_actionAndDelegate() {
        Authentication auth = t.auth(t.as(principal));
        assertThatThrownBy(() -> controller.createDelegation(t.tenantId, new CreateDelegationRequest(vp, "LEAVE",
                "MARKS_APPROVE_HOD", Instant.now().minusSeconds(60), Instant.now().plus(1, ChronoUnit.DAYS)), auth))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("does not hold permission");
        assertThatThrownBy(() -> controller.createDelegation(t.tenantId,
                request(UUID.randomUUID(), Instant.now().minusSeconds(60), Instant.now().plus(1, ChronoUnit.DAYS)), auth))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(t.delegations).isEmpty();
    }

    @Test
    @DisplayName("BS15: bulk with an unknown ID is 404 and a stored DPO item is refused whatever the client sends")
    void bs15_bulk() {
        UUID admin = t.staff("Admin", "COLLEGE_ADMIN", ScopeType.INSTITUTION, inst);
        ApprovalRequest dpo = t.request("TENANT_EXPORT", "DATA_PROTECTION", UUID.randomUUID(), inst, admin, "PRINCIPAL_COLLEGE");
        Authentication auth = t.auth(t.as(principal));

        assertThatThrownBy(() -> controller.bulkDecide(t.tenantId,
                new BulkApprovalRequest(List.of(UUID.randomUUID()), "APPROVED", null), auth))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> controller.bulkDecide(t.tenantId,
                new BulkApprovalRequest(List.of(dpo.getId().getApprovalRequestId()), "APPROVED", null, "GENERAL"), auth))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(dpo.getStatus()).isEqualTo("PENDING");
    }
}

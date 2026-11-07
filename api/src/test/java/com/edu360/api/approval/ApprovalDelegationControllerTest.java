package com.edu360.api.approval;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.approval.dto.BulkApprovalRequest;
import com.edu360.api.approval.dto.BulkApprovalResponse;
import com.edu360.api.approval.dto.CreateDelegationRequest;
import com.edu360.api.approval.dto.DelegationResponse;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

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

class ApprovalDelegationControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("29ba13a8-9f52-431e-a359-2eb227a866f3");
    private static final UUID USER_ID = UUID.fromString("874cfca3-360a-4298-90f7-66a938c41ec3");
    private static final UUID DELEGATE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID DELEGATION_ID = UUID.fromString("a03c7d0b-9141-4fe0-9613-68c65f03738a");

    private DelegationRepository delegationRepository;
    private ApprovalBatchRepository approvalBatchRepository;
    private PermissionEvaluatorService permissionEvaluatorService;
    private AuditLogService auditLogService;

    private ApprovalDelegationService delegationService;
    private ApprovalDelegationController controller;

    @BeforeEach
    void setUp() {
        delegationRepository = mock(DelegationRepository.class);
        approvalBatchRepository = mock(ApprovalBatchRepository.class);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);
        auditLogService = mock(AuditLogService.class);

        delegationService = new ApprovalDelegationService(
                delegationRepository,
                approvalBatchRepository,
                permissionEvaluatorService,
                auditLogService
        );

        controller = new ApprovalDelegationController(delegationService);
    }

    private Authentication authFor(UUID tenantId, UUID userId) {
        TenantPrincipal principal = new TenantPrincipal(tenantId, userId, UserType.STAFF, "dean@university.test", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void ALLOW_delegatorCreatesDelegation() {
        Instant now = Instant.now();
        Instant end = now.plus(7, ChronoUnit.DAYS);

        when(delegationRepository.save(any(Delegation.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateDelegationRequest request = new CreateDelegationRequest(
                DELEGATE_ID,
                "SAME_OR_HIGHER",
                null,
                now,
                end
        );

        ResponseEntity<DelegationResponse> response = controller.createDelegation(
                TENANT_ID, request, authFor(TENANT_ID, USER_ID));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ACTIVE", response.getBody().status());
        assertEquals(DELEGATE_ID, response.getBody().delegateUserId());

        verify(auditLogService).record(eq(AuditEventType.APPROVAL_DELEGATED), any(), any(), any(), any());
    }

    @Test
    void DENY_delegatorWithoutPermissionFails() {
        doThrow(new UnauthorizedException("Action APPROVAL_DELEGATE not granted"))
                .when(permissionEvaluatorService).authorize(
                        any(RbacPrincipal.class), eq("APPROVAL_DELEGATE"), any(RbacScope.class), any(RbacTarget.class));

        CreateDelegationRequest request = new CreateDelegationRequest(
                DELEGATE_ID,
                "SAME_OR_HIGHER",
                null,
                Instant.now(),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        assertThrows(UnauthorizedException.class, () ->
                controller.createDelegation(TENANT_ID, request, authFor(TENANT_ID, USER_ID)));
    }

    @Test
    void GUARD_selfDelegationIsProhibited() {
        CreateDelegationRequest request = new CreateDelegationRequest(
                USER_ID, // Self delegation!
                "SAME_OR_HIGHER",
                null,
                Instant.now(),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                controller.createDelegation(TENANT_ID, request, authFor(TENANT_ID, USER_ID)));

        assertEquals("Delegator cannot delegate approval authority to themselves (Rule 11).", ex.getMessage());
    }

    @Test
    void GUARD_invalidDateOrderIsProhibited() {
        Instant now = Instant.now();
        CreateDelegationRequest request = new CreateDelegationRequest(
                DELEGATE_ID,
                "SAME_OR_HIGHER",
                null,
                now,
                now.minus(1, ChronoUnit.DAYS) // End before start
        );

        assertThrows(BusinessRuleViolationException.class, () ->
                controller.createDelegation(TENANT_ID, request, authFor(TENANT_ID, USER_ID)));
    }

    @Test
    void ALLOW_delegatorCancelsDelegation() {
        Delegation delegation = Delegation.builder()
                .id(new DelegationId(TENANT_ID, DELEGATION_ID))
                .delegatorUserId(USER_ID)
                .delegateUserId(DELEGATE_ID)
                .delegationBasis("SAME_OR_HIGHER")
                .startsAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .endsAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .status("ACTIVE")
                .rowVersion(1)
                .build();

        when(delegationRepository.findByIdTenantIdAndIdDelegationId(TENANT_ID, DELEGATION_ID))
                .thenReturn(Optional.of(delegation));
        when(delegationRepository.save(any(Delegation.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<DelegationResponse> response = controller.cancelDelegation(
                TENANT_ID, DELEGATION_ID, authFor(TENANT_ID, USER_ID));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CANCELLED", response.getBody().status());
        assertNotNull(response.getBody().cancelledAt());

        verify(auditLogService).record(eq(AuditEventType.APPROVAL_DELEGATION_CANCELLED), any(), any(), any(), any());
    }

    @Test
    void ALLOW_approverPerformsBulkDecisions() {
        UUID req1 = UUID.randomUUID();
        UUID req2 = UUID.randomUUID();

        when(approvalBatchRepository.save(any(ApprovalBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        BulkApprovalRequest request = new BulkApprovalRequest(
                List.of(req1, req2),
                "APPROVED",
                "Bulk approval batch for examination moderation"
        );

        ResponseEntity<BulkApprovalResponse> response = controller.bulkDecide(
                TENANT_ID, request, authFor(TENANT_ID, USER_ID));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().totalProcessed());
        assertEquals(2, response.getBody().succeeded());
        assertEquals(0, response.getBody().failed());

        // Each logged separately
        verify(auditLogService, times(2)).record(eq(AuditEventType.APPROVAL_BULK_DECIDED), any(), any(), any(), any());
    }

    @Test
    void SCOPE_tenantMismatchRejected() {
        UUID otherTenant = UUID.randomUUID();
        BulkApprovalRequest request = new BulkApprovalRequest(List.of(UUID.randomUUID()), "APPROVED", null);

        assertThrows(UnauthorizedException.class, () ->
                controller.bulkDecide(otherTenant, request, authFor(TENANT_ID, USER_ID)));
    }
}

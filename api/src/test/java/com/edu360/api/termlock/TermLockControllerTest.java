package com.edu360.api.termlock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.termlock.dto.LockTermRequest;
import com.edu360.api.termlock.dto.TermLockResponse;
import com.edu360.api.termlock.dto.UnlockTermRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TermLockControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("29ba13a8-9f52-431e-a359-2eb227a866f3");
    private static final UUID INSTITUTION_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID USER_ID = UUID.fromString("874cfca3-360a-4298-90f7-66a938c41ec3");
    private static final UUID SESSION_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID TERM_LOCK_ID = UUID.fromString("a03c7d0b-9141-4fe0-9613-68c65f03738a");

    private TermLockRepository termLockRepository;
    private TermLockEventRepository termLockEventRepository;
    private PermissionEvaluatorService permissionEvaluatorService;
    private AuditLogService auditLogService;

    private TermLockService termLockService;
    private TermLockController controller;

    @BeforeEach
    void setUp() {
        termLockRepository = mock(TermLockRepository.class);
        termLockEventRepository = mock(TermLockEventRepository.class);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);
        auditLogService = mock(AuditLogService.class);

        termLockService = new TermLockService(
                termLockRepository,
                termLockEventRepository,
                permissionEvaluatorService,
                auditLogService
        );

        controller = new TermLockController(termLockService);
    }

    private Authentication authFor(UUID tenantId, UUID userId) {
        TenantPrincipal principal = new TenantPrincipal(tenantId, userId, UserType.STAFF, "principal@college.test", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void ALLOW_principalLocksTerm() {
        when(termLockRepository.findByIdTenantIdAndInstitutionIdAndAcademicSessionIdAndSemesterId(
                TENANT_ID, INSTITUTION_ID, SESSION_ID, null)).thenReturn(Optional.empty());
        when(termLockRepository.save(any(TermLock.class))).thenAnswer(inv -> inv.getArgument(0));
        when(termLockEventRepository.save(any(TermLockEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        LockTermRequest request = new LockTermRequest(SESSION_ID, null, "Final term marks submitted");

        ResponseEntity<TermLockResponse> response = controller.lockTerm(
                TENANT_ID, INSTITUTION_ID, request, authFor(TENANT_ID, USER_ID));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("LOCKED", response.getBody().lockState());
        assertEquals(INSTITUTION_ID, response.getBody().institutionId());

        verify(auditLogService).record(eq(AuditEventType.TERM_LOCKED), any(), any(), any(), any());
    }

    @Test
    void DENY_userWithoutTermLockPermissionFails() {
        doThrow(new UnauthorizedException("Action TERM_LOCK not granted"))
                .when(permissionEvaluatorService).authorize(
                        any(RbacPrincipal.class), eq("TERM_LOCK"), any(RbacScope.class), any(RbacTarget.class));

        LockTermRequest request = new LockTermRequest(SESSION_ID, null, null);

        assertThrows(UnauthorizedException.class, () ->
                controller.lockTerm(TENANT_ID, INSTITUTION_ID, request, authFor(TENANT_ID, USER_ID)));
    }

    @Test
    void ALLOW_principalUnlocksTermWithReason() {
        TermLock termLock = TermLock.builder()
                .id(new TermLockId(TENANT_ID, TERM_LOCK_ID))
                .institutionId(INSTITUTION_ID)
                .academicSessionId(SESSION_ID)
                .lockState("LOCKED")
                .rowVersion(1)
                .build();

        when(termLockRepository.findByIdTenantIdAndIdTermLockId(TENANT_ID, TERM_LOCK_ID))
                .thenReturn(Optional.of(termLock));
        when(termLockRepository.save(any(TermLock.class))).thenAnswer(inv -> inv.getArgument(0));
        when(termLockEventRepository.save(any(TermLockEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        UnlockTermRequest request = new UnlockTermRequest("Grievance verification approved by Academic Council", 48);

        ResponseEntity<TermLockResponse> response = controller.unlockTerm(
                TENANT_ID, INSTITUTION_ID, TERM_LOCK_ID, null, request, authFor(TENANT_ID, USER_ID));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("TEMP_UNLOCKED", response.getBody().lockState());
        assertNotNull(response.getBody().unlockUntil());

        verify(auditLogService).record(eq(AuditEventType.TERM_UNLOCKED), any(), any(), any(), any());
    }

    @Test
    void GUARD_unlockWithoutReasonIsProhibited() {
        UnlockTermRequest request = new UnlockTermRequest("", 24);

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                controller.unlockTerm(TENANT_ID, INSTITUTION_ID, TERM_LOCK_ID, null, request, authFor(TENANT_ID, USER_ID)));

        assertEquals("REASON_REQUIRED", ex.getErrorCode());
    }

    @Test
    void GUARD_unlockExceeding72HoursIsProhibited() {
        UnlockTermRequest request = new UnlockTermRequest("Administrative audit", 96); // 96h > 72h

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                controller.unlockTerm(TENANT_ID, INSTITUTION_ID, TERM_LOCK_ID, null, request, authFor(TENANT_ID, USER_ID)));

        assertEquals("INVALID_DURATION", ex.getErrorCode());
    }

    @Test
    void SCOPE_tenantMismatchRejected() {
        UUID otherTenant = UUID.randomUUID();
        LockTermRequest request = new LockTermRequest(SESSION_ID, null, null);

        assertThrows(UnauthorizedException.class, () ->
                controller.lockTerm(otherTenant, INSTITUTION_ID, request, authFor(TENANT_ID, USER_ID)));
    }
}

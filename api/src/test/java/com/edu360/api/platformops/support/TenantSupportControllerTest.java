package com.edu360.api.platformops.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.support.dto.CreateSupportTicketRequest;
import com.edu360.api.platformops.support.dto.GrantSupportAccessRequest;
import com.edu360.api.platformops.support.dto.SupportAccessGrantResponse;
import com.edu360.api.platformops.support.dto.SupportTicketResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

class TenantSupportControllerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private SupportTicketRepository ticketRepository;
    private SupportAccessGrantRepository grantRepository;
    private PermissionEvaluatorService evaluator;
    private TenantSupportController controller;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(SupportTicketRepository.class);
        grantRepository = mock(SupportAccessGrantRepository.class);
        evaluator = mock(PermissionEvaluatorService.class);
        PlatformSupportService service =
                new PlatformSupportService(ticketRepository, grantRepository, mock(com.edu360.api.platformops.role.PlatformAuditWriter.class), mock(AuditLogService.class));
        controller = new TenantSupportController(service, evaluator);
        when(grantRepository.save(any(SupportAccessGrant.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Authentication tenantAuth(UUID tenantId) {
        TenantPrincipal principal = new TenantPrincipal(tenantId, USER_ID, null, "Admin", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private void ticketBelongsTo(UUID ticketId, UUID tenantId) {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(
                SupportTicket.builder().supportTicketId(ticketId).tenantId(tenantId).build()));
    }

    @Test
    void tenantAdminGrantsAccessForOwnTicket() {
        UUID ticketId = UUID.randomUUID();
        ticketBelongsTo(ticketId, TENANT_ID);

        ResponseEntity<SupportAccessGrantResponse> res = controller.grantAccess(
                TENANT_ID, new GrantSupportAccessRequest(ticketId, null, 12), tenantAuth(TENANT_ID));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().expiresAt()).isAfter(Instant.now());
        verify(evaluator).authorize(new RbacPrincipal(TENANT_ID, USER_ID), "SUPPORT_ACCESS_GRANT",
                RbacScope.tenant(), RbacTarget.NONE);
    }

    @Test
    void institutionGrantIsCheckedAtInstitutionScope() {
        UUID ticketId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        ticketBelongsTo(ticketId, TENANT_ID);

        controller.grantAccess(TENANT_ID, new GrantSupportAccessRequest(ticketId, institutionId, 1), tenantAuth(TENANT_ID));

        verify(evaluator).authorize(any(), eq("SUPPORT_ACCESS_GRANT"), eq(RbacScope.institution(institutionId)), any());
    }

    @Test
    void userWithoutThePermissionCannotGrant() {
        UUID ticketId = UUID.randomUUID();
        ticketBelongsTo(ticketId, TENANT_ID);
        doThrow(new UnauthorizedException("denied")).when(evaluator).authorize(any(), any(), any(), any());

        assertThatThrownBy(() -> controller.grantAccess(
                TENANT_ID, new GrantSupportAccessRequest(ticketId, null, 12), tenantAuth(TENANT_ID)))
                .isInstanceOf(UnauthorizedException.class);
        verify(grantRepository, never()).save(any());
    }

    @Test
    void platformStaffCannotGrantEvenAsProductOwner() {
        PlatformPrincipal owner = new PlatformPrincipal(USER_ID, PlatformRole.PRODUCT_OWNER, "owner@edu360.test", false);
        Authentication auth = new UsernamePasswordAuthenticationToken(owner, null, List.of());

        assertThatThrownBy(() -> controller.grantAccess(
                TENANT_ID, new GrantSupportAccessRequest(UUID.randomUUID(), null, 12), auth))
                .isInstanceOf(UnauthorizedException.class);
        verify(grantRepository, never()).save(any());
    }

    @Test
    void adminOfAnotherTenantCannotGrant() {
        assertThatThrownBy(() -> controller.grantAccess(
                TENANT_ID, new GrantSupportAccessRequest(UUID.randomUUID(), null, 12), tenantAuth(UUID.randomUUID())))
                .isInstanceOf(UnauthorizedException.class);
        verify(grantRepository, never()).save(any());
    }

    @Test
    void ticketOfAnotherTenantLooksMissing() {
        UUID ticketId = UUID.randomUUID();
        ticketBelongsTo(ticketId, UUID.randomUUID());

        assertThatThrownBy(() -> controller.grantAccess(
                TENANT_ID, new GrantSupportAccessRequest(ticketId, null, 12), tenantAuth(TENANT_ID)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(grantRepository, never()).save(any());
    }

    @Test
    void userWithRaisePermissionCanRaiseATicket() {
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<SupportTicketResponse> res = controller.raiseTicket(TENANT_ID,
                new CreateSupportTicketRequest("Report card export fails", "Error 500 on class 10", "HIGH", "REPORTS"),
                tenantAuth(TENANT_ID));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().status()).isEqualTo("NEW");
        assertThat(res.getBody().tenantId()).isEqualTo(TENANT_ID);
        verify(evaluator).authorize(new RbacPrincipal(TENANT_ID, USER_ID), "SUPPORT_TICKET_RAISE",
                RbacScope.tenant(), RbacTarget.NONE);
    }

    @Test
    void userWithoutRaisePermissionCannotRaiseATicket() {
        doThrow(new UnauthorizedException("denied")).when(evaluator).authorize(any(), eq("SUPPORT_TICKET_RAISE"), any(), any());
        assertThatThrownBy(() -> controller.raiseTicket(TENANT_ID,
                new CreateSupportTicketRequest("Subject", "Description", null, null), tenantAuth(TENANT_ID)))
                .isInstanceOf(UnauthorizedException.class);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void myTicketsQueryIsRestrictedToAuthenticatedUserAndTenant() {
        when(ticketRepository.findByTenantIdAndRaisedByUserIdOrderByCreatedAtDesc(TENANT_ID, USER_ID)).thenReturn(List.of());
        ResponseEntity<List<SupportTicketResponse>> response = controller.myTickets(TENANT_ID, tenantAuth(TENANT_ID));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        verify(ticketRepository).findByTenantIdAndRaisedByUserIdOrderByCreatedAtDesc(TENANT_ID, USER_ID);
        verify(evaluator).authorize(new RbacPrincipal(TENANT_ID, USER_ID), "SUPPORT_TICKET_RAISE",
                RbacScope.tenant(), RbacTarget.NONE);
    }

    @Test
    void myTicketsRejectsAnotherTenantPath() {
        assertThatThrownBy(() -> controller.myTickets(TENANT_ID, tenantAuth(UUID.randomUUID())))
                .isInstanceOf(UnauthorizedException.class);
        verify(ticketRepository, never()).findByTenantIdAndRaisedByUserIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void platformStaffCannotRaiseATicketOnATenantsBehalf() {
        PlatformPrincipal owner = new PlatformPrincipal(USER_ID, PlatformRole.PRODUCT_OWNER, "owner@edu360.test", false);
        Authentication auth = new UsernamePasswordAuthenticationToken(owner, null, List.of());

        assertThatThrownBy(() -> controller.raiseTicket(TENANT_ID,
                new CreateSupportTicketRequest("Subject", "Description", null, null), auth))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void durationCannotExceed24Hours() {
        UUID ticketId = UUID.randomUUID();
        ticketBelongsTo(ticketId, TENANT_ID);

        assertThatThrownBy(() -> controller.grantAccess(
                TENANT_ID, new GrantSupportAccessRequest(ticketId, null, 36), tenantAuth(TENANT_ID)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Rule PQ-21");
        verify(grantRepository, never()).save(any());
    }

    @Test
    void endToEnd_tenantRaisesTicketAndGrantsAccess_thenPlatformAssumesSession() {
        UUID ticketId = UUID.randomUUID();
        SupportTicket ticket = SupportTicket.builder()
                .supportTicketId(ticketId)
                .tenantId(TENANT_ID)
                .ticketNumber("SUP-2026-TEST1")
                .subject("System export issue")
                .status("NEW")
                .build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        // 1. Tenant admin grants support access
        ResponseEntity<SupportAccessGrantResponse> grantRes = controller.grantAccess(
                TENANT_ID, new GrantSupportAccessRequest(ticketId, null, 8), tenantAuth(TENANT_ID));

        assertThat(grantRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        SupportAccessGrantResponse grantBody = grantRes.getBody();
        assertThat(grantBody).isNotNull();
        assertThat(grantBody.ticketId()).isEqualTo(ticketId);

        // 2. Mock grant lookup for active session
        SupportAccessGrant activeGrant = SupportAccessGrant.builder()
                .supportAccessGrantId(grantBody.supportAccessGrantId())
                .ticketId(ticketId)
                .tenantId(TENANT_ID)
                .grantedAt(Instant.now())
                .expiresAt(grantBody.expiresAt())
                .build();
        when(grantRepository.findByTicketIdAndExpiresAtAfterAndWithdrawnAtIsNull(eq(ticketId), any()))
                .thenReturn(Optional.of(activeGrant));

        // 3. Platform support assumes the session
        PlatformSupportService service = new PlatformSupportService(ticketRepository, grantRepository, mock(com.edu360.api.platformops.role.PlatformAuditWriter.class), mock(AuditLogService.class));
        PlatformSupportController platformController = new PlatformSupportController(
                service,
                com.edu360.api.platformops.PlatformTestSupport.permissions(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly())
        );

        PlatformPrincipal supportStaff = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.SUPPORT_MANAGER, "support@platform.edu360.local", false);
        Authentication platformAuth = new UsernamePasswordAuthenticationToken(supportStaff, null, List.of());

        // L-03 / R7: a live grant exists, but the real read-only session is not built, so the answer is an honest 501.
        assertThatThrownBy(() -> platformController.assumeSupportSession(ticketId, platformAuth))
                .isInstanceOfSatisfying(com.edu360.api.exception.FeatureNotAvailableException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("SUPPORT_SESSION_NOT_AVAILABLE"));
    }
}

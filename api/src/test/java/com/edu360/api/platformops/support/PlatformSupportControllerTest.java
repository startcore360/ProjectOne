package com.edu360.api.platformops.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.support.dto.UpdateSupportTicketRequest;

class PlatformSupportControllerTest {

    private SupportTicketRepository ticketRepository;
    private SupportAccessGrantRepository grantRepository;
    private AuditLogService auditLogService;
    private PlatformPermissionService permissionService;
    private PlatformSupportService supportService;
    private SupportSessionService supportSessionService;
    private PlatformSupportController controller;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ticketRepository = mock(SupportTicketRepository.class);
        grantRepository = mock(SupportAccessGrantRepository.class);
        auditLogService = mock(AuditLogService.class);
        permissionService = com.edu360.api.platformops.PlatformTestSupport.permissions(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());
        supportService = new PlatformSupportService(ticketRepository, grantRepository, org.mockito.Mockito.mock(com.edu360.api.platformops.role.PlatformAuditWriter.class), auditLogService);
        supportSessionService = mock(SupportSessionService.class);
        controller = new PlatformSupportController(supportService, supportSessionService, permissionService);
    }

    private Authentication authFor(PlatformRole role) {
        PlatformPrincipal principal = new PlatformPrincipal(USER_ID, role, "support@edu360.local", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void ACT_SUPPORT_ACCESS_USE_GUARD_activeSessionCountReturnsRepositoryCount() {
        when(supportSessionService.countActiveSessions()).thenReturn(2L);

        ResponseEntity<java.util.Map<String, Long>> response = controller.countActiveSupportSessions(
                authFor(PlatformRole.SUPPORT_MANAGER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("count", 2L);
    }

    @Test
    void ACT_SUPPORT_TICKET_MANAGE_GUARD_resolvedRequiresNote() {
        UUID ticketId = UUID.randomUUID();
        SupportTicket ticket = SupportTicket.builder()
                .supportTicketId(ticketId)
                .status("ASSIGNED")
                .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        UpdateSupportTicketRequest req = new UpdateSupportTicketRequest("RESOLVED", null, null); // missing note

        assertThatThrownBy(() -> controller.updateTicket(ticketId, req, authFor(PlatformRole.SUPPORT_MANAGER)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("resolution_note is required");
    }

    @Test
    void ACT_SUPPORT_ACCESS_USE_ALLOW_withActiveGrant() {
        UUID ticketId = UUID.randomUUID();
        SupportTicket ticket = SupportTicket.builder()
                .supportTicketId(ticketId)
                .tenantId(TENANT_ID)
                .ticketNumber("SUP-2026-0001")
                .status("ASSIGNED")
                .build();

        SupportAccessGrant grant = SupportAccessGrant.builder()
                .supportAccessGrantId(UUID.randomUUID())
                .ticketId(ticketId)
                .tenantId(TENANT_ID)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(grantRepository.findByTicketIdAndExpiresAtAfterAndWithdrawnAtIsNull(any(), any()))
                .thenReturn(Optional.of(grant));
        com.edu360.api.platformops.support.dto.SupportSessionResponse expected =
                new com.edu360.api.platformops.support.dto.SupportSessionResponse("signed-token", Instant.now().plusSeconds(1800), "https://tenant.edu360.example");
        when(supportSessionService.open(org.mockito.ArgumentMatchers.eq(ticketId), any())).thenReturn(expected);

        ResponseEntity<com.edu360.api.platformops.support.dto.SupportSessionResponse> response =
                controller.assumeSupportSession(new com.edu360.api.platformops.support.dto.OpenSupportSessionRequest(ticketId),
                        authFor(PlatformRole.SUPPORT_MANAGER));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void ACT_SUPPORT_ACCESS_USE_GUARD_noActiveGrantThrows() {
        UUID ticketId = UUID.randomUUID();
        SupportTicket ticket = SupportTicket.builder()
                .supportTicketId(ticketId)
                .ticketNumber("SUP-2026-0002")
                .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(grantRepository.findByTicketIdAndExpiresAtAfterAndWithdrawnAtIsNull(any(), any()))
                .thenReturn(Optional.empty());

        when(supportSessionService.open(org.mockito.ArgumentMatchers.eq(ticketId), any()))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("SUPPORT_GRANT_REQUIRED"));
        assertThatThrownBy(() -> controller.assumeSupportSession(
                new com.edu360.api.platformops.support.dto.OpenSupportSessionRequest(ticketId),
                authFor(PlatformRole.SUPPORT_MANAGER)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("SUPPORT_GRANT_REQUIRED");
    }
}

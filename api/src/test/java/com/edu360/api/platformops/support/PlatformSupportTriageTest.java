package com.edu360.api.platformops.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.platformops.support.dto.UpdateSupportTicketRequest;
import com.edu360.api.selfservice.InAppNotifier;

class PlatformSupportTriageTest {
    private final SupportTicketRepository tickets = mock(SupportTicketRepository.class);
    private final SupportAccessGrantRepository grants = mock(SupportAccessGrantRepository.class);
    private final PlatformAuditWriter platformAudit = mock(PlatformAuditWriter.class);
    private final AuditLogService tenantAudit = mock(AuditLogService.class);
    private final JdbcTemplate settings = mock(JdbcTemplate.class);
    private final InAppNotifier notifier = mock(InAppNotifier.class);
    private final SupportSessionService sessions = mock(SupportSessionService.class);
    private PlatformSupportService service;
    private PlatformPrincipal actor;
    private UUID ticketId;
    private UUID requesterId;

    @BeforeEach
    void setUp() {
        service = new PlatformSupportService(tickets, grants, platformAudit, tenantAudit);
        service.setSupportDependencies(settings, notifier, sessions);
        actor = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.SUPPORT_MANAGER, "support@example.test", false);
        ticketId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
    }

    private SupportTicket ticket(String status) {
        return SupportTicket.builder().supportTicketId(ticketId).tenantId(UUID.randomUUID())
                .ticketNumber("SUP-2026-ABCDE").raisedByUserId(requesterId).raisedByName("Requester")
                .subject("Cannot sign in").description("Sign-in error").priority("MEDIUM")
                .status(status).createdAt(Instant.now().minusSeconds(300)).createdBy(requesterId)
                .updatedAt(Instant.now()).updatedBy(requesterId).rowVersion(1).build();
    }

    @Test
    void categoryAndPriorityTogetherMoveNewTicketToTriaged() {
        SupportTicket ticket = ticket("NEW");
        when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(settings.queryForObject(any(String.class), eq(Boolean.class), eq("support_ticket_categories"), eq("BILLING")))
                .thenReturn(true);
        when(tickets.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateTicket(ticketId,
                new UpdateSupportTicketRequest(null, null, null, "HIGH", "BILLING"), actor);

        assertThat(response.status()).isEqualTo("TRIAGED");
        assertThat(response.priority()).isEqualTo("HIGH");
        assertThat(response.category()).isEqualTo("BILLING");
    }

    @Test
    void invalidCategoryIsBadRequest() {
        SupportTicket ticket = ticket("NEW");
        when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(settings.queryForObject(any(String.class), eq(Boolean.class), eq("support_ticket_categories"), eq("GENERAL")))
                .thenReturn(false);

        assertThatThrownBy(() -> service.updateTicket(ticketId,
                new UpdateSupportTicketRequest(null, null, null, "HIGH", "GENERAL"), actor))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void resolutionNotifiesTheOriginalRequesterWithResolutionNote() {
        SupportTicket ticket = ticket("ASSIGNED");
        ticket.setPriority("HIGH");
        ticket.setCategory("BILLING");
        when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(tickets.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateTicket(ticketId,
                new UpdateSupportTicketRequest("RESOLVED", null, "Cleared stale sign-in session"), actor);

        verify(notifier).notify(eq(ticket.getTenantId()), eq(requesterId), eq(null),
                eq("SUPPORT_TICKET_RESOLVED"), eq("Support ticket RESOLVED"),
                eq("SUP-2026-ABCDE is RESOLVED. Cleared stale sign-in session"),
                eq("SUPPORT_TICKET"), eq(ticketId), eq(null), eq("NORMAL"),
                eq("support-ticket:" + ticketId + ":RESOLVED"));
    }
}

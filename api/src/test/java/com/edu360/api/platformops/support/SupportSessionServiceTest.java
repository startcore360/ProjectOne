package com.edu360.api.platformops.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.platformops.support.dto.SupportSessionResponse;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

class SupportSessionServiceTest {
    private final SupportTicketRepository tickets = mock(SupportTicketRepository.class);
    private final SupportAccessGrantRepository grants = mock(SupportAccessGrantRepository.class);
    private final SupportSessionRepository sessions = mock(SupportSessionRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final PlatformAuditWriter audit = mock(PlatformAuditWriter.class);
    private final SupportSessionTokenService tokenService =
            new SupportSessionTokenService("0123456789abcdef0123456789abcdef");
    private SupportSessionService service;
    private UUID tenantId;
    private UUID ticketId;
    private UUID grantId;
    private PlatformPrincipal actor;

    @BeforeEach
    void setUp() {
        service = new SupportSessionService(tickets, grants, sessions, tenants, tokenService, audit);
        ReflectionTestUtils.setField(service, "tenantBaseDomain", "edu360.local");
        ReflectionTestUtils.setField(service, "tenantScheme", "https");
        ReflectionTestUtils.setField(service, "tenantPort", "");
        tenantId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        grantId = UUID.randomUUID();
        actor = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.SUPPORT_MANAGER, "support@example.test", false);
    }

    @Test
    void countsOnlyUnendedSessionsThatHaveNotExpired() {
        when(sessions.countByEndedAtIsNullAndExpiresAtAfter(any())).thenReturn(3L);

        assertThat(service.countActiveSessions()).isEqualTo(3L);
        verify(sessions).countByEndedAtIsNullAndExpiresAtAfter(any());
    }

    @Test
    void sessionExpiryIsCappedAtOneHourAndTenantGrantExpiry() {
        SupportTicket ticket = SupportTicket.builder().supportTicketId(ticketId).tenantId(tenantId).build();
        SupportAccessGrant grant = mock(SupportAccessGrant.class);
        Instant grantExpiry = Instant.now().plusSeconds(20 * 3600);
        when(grant.getSupportAccessGrantId()).thenReturn(grantId);
        when(grant.getTicketId()).thenReturn(ticketId);
        when(grant.getTenantId()).thenReturn(tenantId);
        when(grant.getExpiresAt()).thenReturn(grantExpiry);
        when(grant.getInstitutionId()).thenReturn(null);
        Tenant tenant = Tenant.builder().tenantId(tenantId).subdomain("tenant").state(TenantState.ACTIVE)
                .isolationMode(IsolationMode.SHARED).build();
        when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(grants.findByTicketIdAndExpiresAtAfterAndWithdrawnAtIsNull(org.mockito.ArgumentMatchers.eq(ticketId), any()))
                .thenReturn(Optional.of(grant));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(sessions.save(any(SupportSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupportSessionResponse response = service.open(ticketId, actor);

        var claims = tokenService.verify(response.token()).orElseThrow();
        assertThat(response.tenantLoginAddress()).isEqualTo("https://tenant.edu360.local");
        assertThat(response.expiresAt().truncatedTo(ChronoUnit.SECONDS))
                .isEqualTo(claims.expiresAt().truncatedTo(ChronoUnit.SECONDS));
        assertThat(response.expiresAt()).isBeforeOrEqualTo(Instant.now().plusSeconds(3600));
        assertThat(response.expiresAt()).isBeforeOrEqualTo(grantExpiry);
        assertThat(claims.tenantId()).isEqualTo(tenantId);
        assertThat(claims.ticketId()).isEqualTo(ticketId);
        assertThat(claims.grantId()).isEqualTo(grantId);
        verify(audit).record(actor, tenantId, "SUPPORT_SESSION_STARTED",
                "SUPPORT_ACCESS_USE", "SUPPORT_SESSION", claims.sessionId());
    }

    @Test
    void missingActiveGrantReturnsSupportGrantRequired() {
        when(tickets.findById(ticketId)).thenReturn(Optional.of(
                SupportTicket.builder().supportTicketId(ticketId).tenantId(tenantId).ticketNumber("SUP-1").build()));
        when(grants.findByTicketIdAndExpiresAtAfterAndWithdrawnAtIsNull(org.mockito.ArgumentMatchers.eq(ticketId), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(ticketId, actor))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("SUPPORT_GRANT_REQUIRED");
    }

    @Test
    void withdrawingGrantEndsEveryOpenSessionAsWithdrawn() {
        SupportSession first = SupportSession.builder().supportSessionId(UUID.randomUUID()).grantId(grantId)
                .issuedAt(Instant.now().minusSeconds(30)).expiresAt(Instant.now().plusSeconds(1800)).build();
        SupportSession second = SupportSession.builder().supportSessionId(UUID.randomUUID()).grantId(grantId)
                .issuedAt(Instant.now().minusSeconds(20)).expiresAt(Instant.now().plusSeconds(1800)).build();
        when(sessions.findByGrantIdAndEndedAtIsNull(grantId)).thenReturn(java.util.List.of(first, second));

        service.endForGrant(grantId);

        assertThat(first.getEndReason()).isEqualTo("WITHDRAWN");
        assertThat(second.getEndReason()).isEqualTo("WITHDRAWN");
        assertThat(first.getEndedAt()).isNotNull();
        assertThat(second.getEndedAt()).isNotNull();
    }
}

package com.edu360.api.platformops.support;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.platformops.support.dto.SupportSessionResponse;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SupportSessionService {
    private final SupportTicketRepository ticketRepository;
    private final SupportAccessGrantRepository grantRepository;
    private final SupportSessionRepository sessionRepository;
    private final TenantRepository tenantRepository;
    private final SupportSessionTokenService tokenService;
    private final PlatformAuditWriter platformAuditWriter;

    @Value("${app.support-session.tenant-base-domain:edu360.local}")
    private String tenantBaseDomain;

    @Value("${app.support-session.tenant-scheme:https}")
    private String tenantScheme;

    @Value("${app.support-session.tenant-port:}")
    private String tenantPort;

    @Transactional
    public void endCurrent(PlatformPrincipal actor) {
        SupportSession session = sessionRepository
                .findFirstByPlatformUserIdAndEndedAtIsNullOrderByIssuedAtDesc(actor.platformUserId())
                .orElseThrow(() -> new ResourceNotFoundException("No active support session exists for this platform user."));
        String reason = session.getExpiresAt().isAfter(Instant.now()) ? "ENDED" : "EXPIRED";
        session.setEndedAt(Instant.now());
        session.setEndReason(reason);
        sessionRepository.save(session);
        platformAuditWriter.record(actor, "SUPPORT_SESSION_ENDED", "SUPPORT_ACCESS_USE",
                "SUPPORT_SESSION", session.getSupportSessionId());
    }

    @Transactional
    public void endForGrant(UUID grantId) {
        Instant now = Instant.now();
        for (SupportSession session : sessionRepository.findByGrantIdAndEndedAtIsNull(grantId)) {
            session.setEndedAt(now);
            session.setEndReason("WITHDRAWN");
            sessionRepository.save(session);
        }
    }

    @Transactional
    public SupportSessionResponse open(UUID ticketId, PlatformPrincipal actor) {
        var ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + ticketId));
        Instant now = Instant.now();
        SupportAccessGrant grant = grantRepository.findByTicketIdAndExpiresAtAfterAndWithdrawnAtIsNull(ticketId, now)
                .filter(g -> ticket.getTenantId().equals(g.getTenantId()))
                .orElseThrow(() -> new AccessDeniedException("SUPPORT_GRANT_REQUIRED"));

        Instant expiresAt = grant.getExpiresAt().isBefore(now.plusSeconds(3600))
                ? grant.getExpiresAt() : now.plusSeconds(3600);
        if (!expiresAt.isAfter(now)) {
            throw new AccessDeniedException("SUPPORT_GRANT_REQUIRED");
        }

        Tenant tenant = tenantRepository.findById(grant.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Support tenant not found: " + grant.getTenantId()));
        UUID sessionId = UUID.randomUUID();
        SupportSession session = SupportSession.builder()
                .supportSessionId(sessionId)
                .grantId(grant.getSupportAccessGrantId())
                .platformUserId(actor.platformUserId())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        String token = tokenService.issue(session, grant, actor);
        sessionRepository.save(session);
        platformAuditWriter.record(actor, grant.getTenantId(), "SUPPORT_SESSION_STARTED",
                "SUPPORT_ACCESS_USE", "SUPPORT_SESSION", sessionId);

        String loginHost = tenant.getCustomDomain() != null && !tenant.getCustomDomain().isBlank()
                ? tenant.getCustomDomain()
                : tenant.getSubdomain() + "." + tenantBaseDomain;
        String loginAddress = tenantScheme + "://" + loginHost + tenantPort;
        return new SupportSessionResponse(token, expiresAt, loginAddress);
    }
}

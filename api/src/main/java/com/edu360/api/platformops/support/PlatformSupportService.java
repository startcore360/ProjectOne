package com.edu360.api.platformops.support;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.platformops.support.dto.CreateSupportTicketRequest;
import com.edu360.api.platformops.support.dto.GrantSupportAccessRequest;
import com.edu360.api.platformops.support.dto.SupportAccessGrantResponse;
import com.edu360.api.platformops.support.dto.SupportTicketResponse;
import com.edu360.api.platformops.support.dto.UpdateSupportTicketRequest;

import lombok.RequiredArgsConstructor;

@Service
public class PlatformSupportService {

    private final SupportTicketRepository ticketRepository;
    private final SupportAccessGrantRepository grantRepository;
    private final com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter;
    private final com.edu360.api.audit.AuditLogService tenantAuditService;

    @org.springframework.beans.factory.annotation.Autowired
    public PlatformSupportService(
            SupportTicketRepository ticketRepository,
            SupportAccessGrantRepository grantRepository,
            com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter,
            com.edu360.api.audit.AuditLogService tenantAuditService
    ) {
        this.ticketRepository = ticketRepository;
        this.grantRepository = grantRepository;
        this.platformAuditWriter = platformAuditWriter;
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional
    public SupportTicketResponse raiseTicket(UUID tenantId, CreateSupportTicketRequest req, UUID actorUserId, String actorName) {
        String ticketNumber = "SUP-" + LocalDate.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        Instant now = Instant.now();

        SupportTicket ticket = SupportTicket.builder()
                .supportTicketId(UUID.randomUUID())
                .tenantId(tenantId)
                .ticketNumber(ticketNumber)
                .raisedByUserId(actorUserId)
                .raisedByName(actorName)
                .subject(req.subject())
                .description(req.description())
                .priority(req.priority() != null ? req.priority() : "MEDIUM")
                .category(req.category() != null ? req.category() : "GENERAL")
                .status("NEW")
                .createdAt(now)
                .createdBy(actorUserId)
                .updatedAt(now)
                .updatedBy(actorUserId)
                .rowVersion(1)
                .build();

        SupportTicket saved = ticketRepository.save(ticket);
        tenantAuditService.record(com.edu360.api.audit.AuditEventType.SUPPORT_TICKET_CREATED, null, actorUserId.toString(), null,
                "Support ticket raised: " + ticketNumber + " for tenant: " + tenantId);
        return SupportTicketResponse.fromEntity(saved);
    }

    @Transactional
    public SupportTicketResponse updateTicket(UUID ticketId, UpdateSupportTicketRequest req, PlatformPrincipal principal) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + ticketId));

        if (req.status() != null) {
            if (("RESOLVED".equals(req.status()) || "CLOSED".equals(req.status())) && (req.resolutionNote() == null || req.resolutionNote().isBlank())) {
                throw new BusinessRuleViolationException("Rule ck_support_ticket_r1: resolution_note is required when status is RESOLVED or CLOSED.");
            }
            ticket.setStatus(req.status());
            if ("CLOSED".equals(req.status())) {
                ticket.setClosedAt(Instant.now());
            }
        }

        if (req.assignedTo() != null) {
            ticket.setAssignedTo(req.assignedTo());
            if ("NEW".equals(ticket.getStatus())) {
                ticket.setStatus("ASSIGNED");
            }
        }

        if (req.resolutionNote() != null) {
            ticket.setResolutionNote(req.resolutionNote());
        }

        ticket.setUpdatedAt(Instant.now());
        ticket.setUpdatedBy(principal.platformUserId());

        SupportTicket saved = ticketRepository.save(ticket);
        platformAuditWriter.record(principal, "SUPPORT_TICKET_UPDATED", "SUPPORT_TICKET_MANAGE", "SUPPORT_TICKET", saved.getSupportTicketId());
        return SupportTicketResponse.fromEntity(saved);
    }

    @Transactional
    public SupportAccessGrantResponse grantAccess(UUID tenantId, GrantSupportAccessRequest req, UUID actorUserId) {
        // Same 404 for a missing ticket and another tenant's ticket, so a tenant can't probe ticket IDs.
        ticketRepository.findById(req.ticketId())
                .filter(ticket -> tenantId.equals(ticket.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + req.ticketId()));

        if (req.durationHours() < 1 || req.durationHours() > 24) {
            throw new BusinessRuleViolationException("Rule PQ-21 / ck_support_access_grant_r1: Support access grant duration must be between 1 and 24 hours.");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(req.durationHours()));

        SupportAccessGrant grant = SupportAccessGrant.builder()
                .supportAccessGrantId(UUID.randomUUID())
                .ticketId(req.ticketId())
                .tenantId(tenantId)
                .grantedByUserId(actorUserId)
                .institutionId(req.institutionId())
                .grantedAt(now)
                .expiresAt(expiresAt)
                .createdAt(now)
                .createdBy(actorUserId)
                .updatedAt(now)
                .updatedBy(actorUserId)
                .rowVersion(1)
                .build();

        SupportAccessGrant saved = grantRepository.save(grant);
        tenantAuditService.record(com.edu360.api.audit.AuditEventType.SUPPORT_ACCESS_GRANT_CREATED, null, actorUserId.toString(), null,
                "Support access grant created for ticket: " + req.ticketId() + " expires at: " + expiresAt);
        return SupportAccessGrantResponse.fromEntity(saved);
    }

    /**
     * L-03, rule R7: a real read-only support session is designed (docs/SUPPORT_SESSION_DESIGN.md) but not built yet.
     * The made-up session token that used to be returned here is gone. The ticket and the grant are still
     * checked, so the answer is honest about what is missing: the session itself, not the grant.
     */
    @Transactional(readOnly = true)
    public void assumeSupportSession(UUID ticketId, PlatformPrincipal principal) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + ticketId));
        grantRepository.findByTicketIdAndExpiresAtAfterAndWithdrawnAtIsNull(ticketId, Instant.now())
                .orElseThrow(() -> new BusinessRuleViolationException("Rule WF-11: No active support access grant found for ticket " + ticket.getTicketNumber()));
        throw new com.edu360.api.exception.FeatureNotAvailableException("SUPPORT_SESSION_NOT_AVAILABLE",
                "Support sessions are not available yet. A tenant grant exists for this ticket, but read-only access to tenant data has not been built.");
    }

    @Transactional
    public SupportAccessGrantResponse withdrawAccess(UUID tenantId, UUID grantId, UUID actorUserId) {
        SupportAccessGrant grant = grantRepository.findById(grantId)
                .filter(g -> tenantId.equals(g.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Support access grant not found: " + grantId));
        if (grant.getWithdrawnAt() != null) {
            throw new BusinessRuleViolationException("Support access grant is already withdrawn.");
        }
        grant.setWithdrawnAt(Instant.now());
        grant.setUpdatedAt(Instant.now());
        grant.setUpdatedBy(actorUserId);
        SupportAccessGrant saved = grantRepository.save(grant);
        tenantAuditService.record(com.edu360.api.audit.AuditEventType.SUPPORT_ACCESS_GRANT_WITHDRAWN, null, actorUserId.toString(), null,
                "Support access grant withdrawn for ticket: " + grant.getTicketId());
        return SupportAccessGrantResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<SupportAccessGrantResponse> listGrantsForTenant(UUID tenantId) {
        return grantRepository.findByTenantIdOrderByGrantedAtDesc(tenantId).stream()
                .map(SupportAccessGrantResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SupportTicketResponse> listTickets(UUID tenantId) {
        List<SupportTicket> list = (tenantId != null)
                ? ticketRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : ticketRepository.findAllByOrderByCreatedAtDesc();
        return list.stream().map(SupportTicketResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<SupportAccessGrantResponse> listActiveGrants() {
        return grantRepository.findByExpiresAtAfterAndWithdrawnAtIsNull(Instant.now()).stream()
                .map(SupportAccessGrantResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse getTicket(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .map(SupportTicketResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + ticketId));
    }
}

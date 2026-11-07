package com.edu360.api.platformops.support;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.platformops.support.dto.AssumeSupportSessionResponse;
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
    private final AuditLogService auditLogService;
    private final com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter;

    @org.springframework.beans.factory.annotation.Autowired
    public PlatformSupportService(
            SupportTicketRepository ticketRepository,
            SupportAccessGrantRepository grantRepository,
            AuditLogService auditLogService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter
    ) {
        this.ticketRepository = ticketRepository;
        this.grantRepository = grantRepository;
        this.auditLogService = auditLogService;
        this.platformAuditWriter = platformAuditWriter;
    }

    public PlatformSupportService(
            SupportTicketRepository ticketRepository,
            SupportAccessGrantRepository grantRepository,
            AuditLogService auditLogService
    ) {
        this(ticketRepository, grantRepository, auditLogService, null);
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
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, actorUserId.toString(), null,
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
        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "SUPPORT_TICKET_UPDATED", "SUPPORT_TICKET_MANAGE", "SUPPORT_TICKET", saved.getSupportTicketId());
        }
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, principal.platformUserId().toString(), null,
                "Support ticket updated: " + ticket.getTicketNumber() + " new status: " + ticket.getStatus());
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
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, actorUserId.toString(), null,
                "Support access grant created for ticket: " + req.ticketId() + " expires at: " + expiresAt);
        return SupportAccessGrantResponse.fromEntity(saved);
    }

    @Transactional
    public AssumeSupportSessionResponse assumeSupportSession(UUID ticketId, PlatformPrincipal principal) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + ticketId));

        SupportAccessGrant grant = grantRepository.findByTicketIdAndExpiresAtAfterAndWithdrawnAtIsNull(ticketId, Instant.now())
                .orElseThrow(() -> new BusinessRuleViolationException("Rule WF-11: No active support access grant found for ticket " + ticket.getTicketNumber()));

        String token = "supp_token_" + UUID.randomUUID();
        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "SUPPORT_SESSION_ASSUMED", "SUPPORT_ACCESS_USE", "SUPPORT_TICKET", ticket.getSupportTicketId());
        }
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, principal.platformUserId().toString(), null,
                "Read-only support session assumed for ticket " + ticket.getTicketNumber() + " by " + principal.platformRole());

        return new AssumeSupportSessionResponse(
                UUID.randomUUID(),
                ticket.getSupportTicketId(),
                ticket.getTenantId(),
                ticket.getTicketNumber(),
                token,
                grant.getExpiresAt(),
                "READ_ONLY"
        );
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
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, actorUserId.toString(), null,
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

package com.edu360.api.platformops.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.platformops.PlatformAuditOutboxEvent;

/** One row from platform.audit_outbox, for PLATFORM_AUDIT_VIEW. */
public record AuditLogEntryResponse(
        UUID auditOutboxId,
        UUID tenantId,
        Instant occurredAt,
        String actorKind,
        UUID actorUserId,
        String operation,
        String actionCode,
        String entityType,
        UUID entityId,
        String outcome,
        String correlationId,
        String eventType) {

    public static AuditLogEntryResponse from(PlatformAuditOutboxEvent event) {
        return new AuditLogEntryResponse(
                event.getAuditOutboxId(),
                event.getTenantId(),
                event.getOccurredAt(),
                event.getActorKind(),
                event.getActorUserId(),
                event.getOperation(),
                event.getActionCode(),
                event.getEntityType(),
                event.getEntityId(),
                event.getOutcome(),
                event.getCorrelationId(),
                event.getEventType());
    }
}

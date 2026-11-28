package com.edu360.api.audit;

import java.time.Instant;
import java.util.UUID;

/** One structured row in app.audit_outbox, including platform support access under a tenant grant. */
public record TenantAuditEvent(
        UUID tenantId, UUID auditEventId, UUID institutionId, Instant occurredAt,
        String actorKind, UUID actorUserId, String activeRoleCode, UUID supportGrantId,
        String operation, String actionCode, String entityType, UUID entityId,
        Integer recordCount, String sensitivityClass, String outcome,
        String oldValuesJson, String newValuesJson, String correlationId, String payloadHash) {

    /** Compatibility constructor for ordinary tenant-user and system audit events. */
    public TenantAuditEvent(UUID tenantId, UUID auditEventId, UUID institutionId, Instant occurredAt,
            UUID actorUserId, String activeRoleCode, String operation, String actionCode, String entityType,
            UUID entityId, Integer recordCount, String sensitivityClass, String outcome,
            String oldValuesJson, String newValuesJson, String correlationId, String payloadHash) {
        this(tenantId, auditEventId, institutionId, occurredAt, "USER", actorUserId, activeRoleCode, null,
                operation, actionCode, entityType, entityId, recordCount, sensitivityClass, outcome,
                oldValuesJson, newValuesJson, correlationId, payloadHash);
    }
}

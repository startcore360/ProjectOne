package com.edu360.api.audit;
import java.time.Instant;
import java.util.UUID;
/** One structured row in app.audit_outbox. */
public record TenantAuditEvent(UUID tenantId, UUID auditEventId, UUID institutionId, Instant occurredAt,
 UUID actorUserId, String activeRoleCode, String operation, String actionCode, String entityType,
 UUID entityId, Integer recordCount, String sensitivityClass, String outcome,
 String oldValuesJson, String newValuesJson, String correlationId, String payloadHash) { }

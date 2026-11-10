package com.edu360.api.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of app.audit_outbox (D4 §5.23), the tenant audit store: RLS by tenant_id (p_tenant)
 * and institution (p_institution, restrictive), append-only by trigger (fn_audit_guard), INSERT
 * and SELECT only for edu_app (sql/03_bindings.sql). Q-01: tenant events are written here, never
 * to the legacy public.audit_logs.
 *
 * Column rules the database enforces (01_schema.sql): actor_kind USER needs actor_user_id and
 * active_role_code (r1, r2); operation and outcome come from fixed lists; correlation_id 1-100
 * characters; payload_hash is a 64-character SHA-256 hex digest.
 */
public record TenantAuditEvent(
        UUID tenantId,
        UUID auditEventId,
        UUID institutionId,
        Instant occurredAt,
        UUID actorUserId,
        String activeRoleCode,
        String operation,
        String entityType,
        String outcome,
        String newValuesJson,
        String correlationId,
        String payloadHash) {
}

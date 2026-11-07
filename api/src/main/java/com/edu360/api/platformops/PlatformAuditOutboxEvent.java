package com.edu360.api.platformops;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WHAT: a read-only, partial view of platform.audit_outbox (sql/01_schema.sql line ~1122) -
 * "Platform-action audit events awaiting shipment to the write-once store". This is the table
 * PLATFORM_AUDIT_VIEW must read from, and ONLY this table: it is schema-distinct from
 * app.audit_outbox (the tenant-scoped, RLS-protected audit trail under the app schema, line
 * ~7336) and from the pre-existing audit.AuditLog/"audit_logs" table (login/logout/account
 * events only, predates this schema entirely, has no platform/tenant distinction of its own).
 * So the "never tenant audit logs" rule in this story's brief is enforced simply by which table
 * this entity maps to - there is no cross-tenant query to accidentally write.
 *
 * Only the columns an audit log viewer actually needs are mapped (same partial-entity pattern as
 * tenant.Tenant) - old_values/new_values are jsonb and deliberately left unmapped rather than
 * pulling in a new jsonb-mapping dependency for this slice.
 *
 * REAL GAP (flagged, not worked around): nothing in this codebase currently INSERTs into
 * platform.audit_outbox - no platform action in api/ writes an audit event here yet. This
 * endpoint is a correct read-side contract against the real table, but will return an empty
 * page until that write side exists as a separate piece of work.
 */
@Entity
@Table(schema = "platform", name = "audit_outbox")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformAuditOutboxEvent {

    @Id
    @Column(name = "audit_outbox_id")
    private UUID auditOutboxId;

    /** Nullable: only set when the platform action concerned a specific tenant (e.g. suspend,
     *  relocate). Still a platform-scoped event row either way - never app.audit_outbox. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_kind", nullable = false)
    private String actorKind;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(nullable = false)
    private String operation;

    @Column(name = "action_code")
    private String actionCode;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private String outcome;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;
}

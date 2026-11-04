package com.edu360.api.platformops;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WHAT: platform.integration_type (sql/01_schema.sql line ~1025) - "Allowed integration types
 * only; never tenant keys [MX-02]". This is the TYPE catalogue only (e.g. "SMS_DLT",
 * "RAZORPAY") - per the brief, platform roles never see a tenant's actual integration
 * credentials/webhooks through this table or this endpoint, and nothing here references
 * app.channel_config (the tenant-scoped table that holds the actual, secret-by-reference
 * config - a different, tenant-scoped story, out of this package's scope).
 *
 * Mapped as a real, full entity (not partial) because INTEGRATION_TYPES_MANAGE is the one
 * genuinely CRUD-ish story of the four: GET/POST/PATCH all touch this table directly. Not using
 * Hibernate UUID-v7 generation: no UUIDv7 generator utility exists anywhere else in this
 * codebase yet (grepped - every other UUID-keyed entity relies on an application-supplied UUID
 * too), so this follows that same existing convention (plain UUID.randomUUID() from the
 * controller) rather than introducing a new ID-generation mechanism for one table.
 *
 * created_at/updated_at/row_version are DB-trigger-managed (trg_touch, per the schema comments)
 * in the real Postgres bundle, so they are mapped insertable/updatable = false, same pattern as
 * tenant.Tenant and platform.PlatformUser's own *_at columns.
 */
@Entity
@Table(schema = "platform", name = "integration_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationType {

    @Id
    @Column(name = "integration_type_id")
    private UUID integrationTypeId;

    @Column(name = "integration_type_code", nullable = false)
    private String integrationTypeCode;

    @Column(nullable = false)
    private String description;

    /** ALLOWED or DISALLOWED per ck_integration_type_status. */
    @Column(nullable = false)
    @Builder.Default
    private String status = "ALLOWED";

    /** Native Postgres text[] column, mapped via Hibernate 6's built-in array support
     *  (@JdbcTypeCode(SqlTypes.ARRAY)) - no separate join table, no extra dependency. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_providers", nullable = false)
    @Builder.Default
    private List<String> allowedProviders = List.of();

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;
}

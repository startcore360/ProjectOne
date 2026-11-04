package com.edu360.api.tenant;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WHAT: a read-only view of platform.tenant - the tenant directory. Only the columns the
 * bootstrap resolver and routing layer actually need are mapped; this entity is never used to
 * create or edit a tenant (that is TENANT_CREATE/TENANT_ACTIVATE, a separate Platform epic
 * story, not part of this identity migration).
 *
 * WHY this table has no tenant_id column of its own and no RLS: it IS the tenant directory -
 * platform.tenant is deliberately outside the app schema's RLS, because you have to be able to
 * look a tenant up BEFORE you know which tenant's RLS context to apply. This is the one query
 * in the whole system allowed to run with no tenant context set at all, and it is restricted
 * to exactly the columns below - never anything that looks like a different tenant's data.
 */
@Entity
@Table(schema = "platform", name = "tenant")
@Getter
@NoArgsConstructor
public class Tenant {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "isolation_mode", nullable = false)
    private IsolationMode isolationMode;

    /** Vault/Secrets-Manager reference, never a credential itself (schema comment) - only
     *  present when isolationMode = ISOLATED. See TenantSecretsResolver. */
    @Column(name = "db_connection_ref")
    private String dbConnectionRef;

    /** Unique per tenant; the local-dev hostname resolver strips ".edu360.local" from the
     *  request Host header and matches what remains against this column. */
    @Column(nullable = false)
    private String subdomain;

    /** Unique per tenant (DB constraint uq_tenant_keycloak_realm) - the one thing the bootstrap
     *  resolver trusts to pick which issuer/JWKS to validate an incoming token against. */
    @Column(name = "keycloak_realm")
    private String keycloakRealm;
}

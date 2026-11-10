package com.edu360.api.tenant;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity mapping platform.tenant — the tenant directory and configuration.
 * Used both for runtime routing/JWKS resolver and for platform tenant lifecycle
 * operations (TENANT_CREATE, TENANT_ACTIVATE, TENANT_SETTINGS).
 */
@Entity
@Table(schema = "platform", name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "tenant_name", nullable = false)
    private String tenantName;

    @Column(name = "tenant_type", nullable = false)
    private String tenantType;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "isolation_mode", nullable = false)
    private IsolationMode isolationMode;

    /** Vault/Secrets-Manager reference — only present when isolationMode = ISOLATED. */
    @Column(name = "db_connection_ref")
    private String dbConnectionRef;

    /** Unique per tenant; matched against request Host header in subdomain routing. */
    @Column(nullable = false, unique = true)
    private String subdomain;

    @Column(name = "custom_domain", unique = true)
    private String customDomain;

    @Column(name = "time_zone", nullable = false)
    @Builder.Default
    private String timeZone = "Asia/Kolkata";

    @Column(name = "data_region", nullable = false)
    @Builder.Default
    private String dataRegion = "IN";

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "user_limit")
    private Integer userLimit;

    @Column(name = "storage_limit_gb")
    private Integer storageLimitGb;

    @Column(name = "sms_limit")
    private Integer smsLimit;

    @Column(name = "api_limit")
    private Integer apiLimit;

    @Column(name = "branding_logo_file_ref")
    private UUID brandingLogoFileRef;

    /** Unique per tenant — used by bootstrap resolver to validate issuer JWKS. */
    @Column(name = "keycloak_realm", unique = true)
    private String keycloakRealm;

    @Column(name = "analytics_opt_out", nullable = false)
    @Builder.Default
    private boolean analyticsOptOut = false;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", nullable = false)
    @Builder.Default
    private Integer rowVersion = 1;
}

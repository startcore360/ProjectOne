package com.edu360.api.selfservice;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * app.user_device (01_schema.sql + 18_v41_user_sessions.sql): one row per tenant-user sign-in on
 * a device/browser (AUTH_DEVICES_MANAGE, SES-01). RLS-scoped by tenant_id; written only while
 * TenantContext is set. row_version and updated_at are maintained by the trg_touch trigger, so
 * they are read-only here.
 */
@Entity
@Table(name = "user_device", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDevice {

    @EmbeddedId
    private UserDeviceId id;

    @Column(name = "user_account_id", nullable = false)
    private UUID userAccountId;

    @Column(name = "device_class", nullable = false)
    private String deviceClass;

    @Column(name = "device_label")
    private String deviceLabel;

    @Column(name = "keycloak_session_id")
    private String keycloakSessionId;

    @Column(name = "browser_key_hash")
    private String browserKeyHash;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", nullable = false, insertable = false, updatable = false)
    private Integer rowVersion;
}

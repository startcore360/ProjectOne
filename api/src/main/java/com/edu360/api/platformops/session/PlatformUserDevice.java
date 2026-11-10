package com.edu360.api.platformops.session;

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
import lombok.Setter;

/**
 * platform.platform_user_device (18_v41_user_sessions.sql): the platform-staff twin of
 * app.user_device (SES-01). Lives in the platformops package so it joins the platform
 * persistence unit (PlatformJpaConfig) and is written as edu_platform, never edu_app.
 */
@Entity
@Table(name = "platform_user_device", schema = "platform")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformUserDevice {

    @Id
    @Column(name = "platform_user_device_id", nullable = false)
    private UUID platformUserDeviceId;

    @Column(name = "platform_user_id", nullable = false)
    private UUID platformUserId;

    @Column(name = "device_class", nullable = false)
    private String deviceClass;

    @Column(name = "device_label")
    private String deviceLabel;

    @Column(name = "keycloak_session_id", nullable = false)
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

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "row_version", nullable = false, insertable = false, updatable = false)
    private Integer rowVersion;
}

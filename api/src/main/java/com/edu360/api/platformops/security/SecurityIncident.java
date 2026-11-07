package com.edu360.api.platformops.security;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "security_incident", schema = "platform")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityIncident {

    @Id
    @Column(name = "security_incident_id", nullable = false)
    private UUID securityIncidentId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "external_notification_required", nullable = false)
    private boolean externalNotificationRequired;

    @Column(name = "external_notified_at")
    private Instant externalNotifiedAt;

    @Column(name = "evidence_ref")
    private String evidenceRef;

    @Column(name = "closure_note")
    private String closureNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", nullable = false)
    @Builder.Default
    private Integer rowVersion = 1;
}

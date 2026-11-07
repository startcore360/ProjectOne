package com.edu360.api.platformops.support;

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
@Table(name = "support_access_grant", schema = "platform")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportAccessGrant {

    @Id
    @Column(name = "support_access_grant_id", nullable = false)
    private UUID supportAccessGrantId;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "granted_by_user_id", nullable = false)
    private UUID grantedByUserId;

    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;
}

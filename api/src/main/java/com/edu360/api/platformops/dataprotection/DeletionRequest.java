package com.edu360.api.platformops.dataprotection;

import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "deletion_request", schema = "platform")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeletionRequest {

    @Id
    @Column(name = "deletion_request_id", nullable = false)
    private UUID deletionRequestId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "origin", nullable = false)
    private String origin;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "po_confirmed_by")
    private UUID poConfirmedBy;

    @Column(name = "po_confirmed_at")
    private Instant poConfirmedAt;

    @Column(name = "export_request_id")
    private UUID exportRequestId;

    @Column(name = "retention_ends_at")
    private LocalDate retentionEndsAt;

    @Column(name = "dpo_decided_by")
    private UUID dpoDecidedBy;

    @Column(name = "dpo_decided_at")
    private Instant dpoDecidedAt;

    @Column(name = "dpo_remark")
    private String dpoRemark;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;
}

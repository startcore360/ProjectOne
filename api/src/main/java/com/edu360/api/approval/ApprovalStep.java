package com.edu360.api.approval;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "approval_step", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalStep implements Serializable {

    @EmbeddedId
    private ApprovalStepId id;

    @Column(name = "approval_request_id", nullable = false)
    private UUID approvalRequestId;

    @Column(name = "step_no", nullable = false)
    private short stepNo;

    @Column(name = "approver_role_code", nullable = false)
    private String approverRoleCode;

    @Column(name = "approver_user_id")
    private UUID approverUserId;

    @Column(name = "decision", nullable = false)
    @Builder.Default
    private String decision = "PENDING";

    @Column(name = "acted_by_user_id")
    private UUID actedByUserId;

    @Column(name = "acting_via_delegation_id")
    private UUID actingViaDelegationId;

    @Column(name = "acted_active_role")
    private String actedActiveRole;

    @Column(name = "remark")
    private String remark;

    @Column(name = "skip_reason")
    private String skipReason;

    @Column(name = "approval_batch_id")
    private UUID approvalBatchId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
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

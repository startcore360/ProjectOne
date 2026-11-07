package com.edu360.api.academic;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "promotion_run", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionRun {

    @EmbeddedId
    private PromotionRunId id;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "from_session_id", nullable = false)
    private UUID fromSessionId;

    @Column(name = "to_session_id", nullable = false)
    private UUID toSessionId;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "approval_request_id")
    private UUID approvalRequestId;

    @Column(name = "executed_at")
    private Instant executedAt;

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
    private int rowVersion = 1;
}

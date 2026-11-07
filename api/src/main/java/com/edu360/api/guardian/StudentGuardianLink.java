package com.edu360.api.guardian;

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

@Entity
@Table(schema = "app", name = "student_guardian_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentGuardianLink {

    @EmbeddedId
    private StudentGuardianLinkId id;

    @Column(name = "student_profile_id", nullable = false)
    private UUID studentProfileId;

    @Column(name = "guardian_profile_id", nullable = false)
    private UUID guardianProfileId;

    @Column(name = "relationship", nullable = false)
    private String relationship;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "PENDING_ACTIVATION";

    @Column(name = "change_approval_request_id")
    private UUID changeApprovalRequestId;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "block_reason")
    private String blockReason;

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

    public UUID getTenantId() {
        return id != null ? id.getTenantId() : null;
    }

    public UUID getStudentGuardianLinkId() {
        return id != null ? id.getStudentGuardianLinkId() : null;
    }
}

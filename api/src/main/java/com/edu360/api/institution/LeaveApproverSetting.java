package com.edu360.api.institution;

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
 * Maps app.leave_approver_setting: identifies who approves a Principal's,
 * Vice-Chancellor's, or Data Protection Contact's leave.
 * Required before go-live under rule C7 / C6.
 */
@Entity
@Table(name = "leave_approver_setting", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveApproverSetting {

    @EmbeddedId
    private LeaveApproverSettingId id;

    @Column(name = "subject_role", nullable = false)
    private String subjectRole;

    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "approver_kind", nullable = false)
    private String approverKind;

    @Column(name = "approver_user_id", nullable = false)
    private UUID approverUserId;

    @Column(name = "effective_from", nullable = false)
    @Builder.Default
    private Instant effectiveFrom = Instant.now();

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, updatable = false)
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

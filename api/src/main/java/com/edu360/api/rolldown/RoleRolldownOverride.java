package com.edu360.api.rolldown;

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
@Table(name = "role_rolldown_override", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleRolldownOverride {

    @EmbeddedId
    private RoleRolldownOverrideId id;

    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "missing_role_code", nullable = false)
    private String missingRoleCode;

    @Column(name = "permission_kind", nullable = false)
    private String permissionKind;

    @Column(name = "action_code")
    private String actionCode;

    @Column(name = "mode", nullable = false)
    private String mode;

    @Column(name = "target_role_code")
    private String targetRoleCode;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "set_by_user_id", nullable = false)
    private UUID setByUserId;

    @Column(name = "set_by_role", nullable = false)
    private String setByRole;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "effective_from", nullable = false)
    @Builder.Default
    private Instant effectiveFrom = Instant.now();

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

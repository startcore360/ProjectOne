package com.edu360.api.rbac;

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
@Table(name = "tenant_step_in", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantStepIn {

    @EmbeddedId
    private TenantStepInId id;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "action_code", nullable = false)
    private String actionCode;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "target_role_definition_id")
    private UUID targetRoleDefinitionId;

    @Column(name = "started_at", insertable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}

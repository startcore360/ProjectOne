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

/**
 * WHAT: one row of app.role_action - one action held by one tenant role_definition. This, not
 * ref.standard_role_action, is the table PermissionEvaluatorService actually checks: the schema
 * comment on app.role_action is explicit that it holds "standard rows from
 * ref.standard_role_action [copied at provisioning]; custom rows picked by an Admin" - i.e. by
 * the time a role exists in a tenant, this table is the single source of truth for what it can
 * do, whether the role itself is STANDARD or CUSTOM (Addendum rule 5: role authority depends on
 * its permissions, not its name or origin).
 */
@Entity
@Table(schema = "app", name = "role_action")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleAction {

    @EmbeddedId
    private RoleActionId id;

    @Column(name = "role_definition_id", nullable = false)
    private UUID roleDefinitionId;

    @Column(name = "action_code", nullable = false)
    private String actionCode;

    @Column(name = "access_mode")
    private String accessMode;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;
}

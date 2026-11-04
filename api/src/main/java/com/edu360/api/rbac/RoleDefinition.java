package com.edu360.api.rbac;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WHAT: one row of app.role_definition - a role as it exists WITHIN one tenant: either a
 * standard role copied at provisioning, or a custom role a tenant administrator built from
 * approved actions only (Addendum rules 3 and 4). This is the "which role" half of an
 * assignment; the "which actions" half lives in RoleAction, never duplicated here.
 *
 * row_version mapped read-only for the same reason as UserAccount: this slice only needs
 * SELECT (PermissionEvaluatorService reads roleCode/status), so proper optimistic locking is
 * deferred to whichever future story first UPDATEs this row (role editing/retirement).
 */
@Entity
@Table(schema = "app", name = "role_definition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleDefinition {

    @EmbeddedId
    private RoleDefinitionId id;

    @Column(name = "role_code", nullable = false)
    private String roleCode;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_origin", nullable = false)
    private RoleOrigin roleOrigin;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_level", nullable = false)
    private RoleLevel roleLevel;

    /** Set only for STANDARD roles: the ref.standard_role template this was copied from. */
    @Column(name = "base_standard_role")
    private String baseStandardRole;

    /** Set only for CUSTOM roles (ck_role_definition_r1); null for STANDARD. */
    @Enumerated(EnumType.STRING)
    @Column(name = "custom_scope")
    private CustomScope customScope;

    /** Maintained by a database trigger on role_action (schema comment) whenever the role
     *  holds any approval, finance, HR or exam action - never set directly by this app. */
    @Column(name = "is_sensitive_role", nullable = false)
    private boolean sensitiveRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleDefinitionStatus status;

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

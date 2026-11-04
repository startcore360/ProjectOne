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
 * WHAT: one row of app.user_role_assignment - a (role + scope) held by one user. The schema
 * comment calls this out explicitly: "one active role per session" [HI-14, HI-15, PQ-17],
 * which is Addendum rule 10 (exactly one role active within a given assignment and access
 * context) at the database-model level.
 *
 * WHY scopeType/institutionId/departmentId together, never combined across rows
 * (Addendum rules 8 and 9): a user can hold several assignments (e.g. one per institution they
 * work at), each separately scoped - PermissionEvaluatorService looks up the ONE assignment
 * that matches the exact scope of the request and checks only that assignment's role, never
 * unions permissions across more than one matching row.
 */
@Entity
@Table(schema = "app", name = "user_role_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRoleAssignment {

    @EmbeddedId
    private UserRoleAssignmentId id;

    @Column(name = "user_account_id", nullable = false)
    private UUID userAccountId;

    @Column(name = "role_definition_id", nullable = false)
    private UUID roleDefinitionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private ScopeType scopeType;

    /** NULL only when scopeType = TENANT (ck_user_role_assignment_r1). */
    @Column(name = "institution_id")
    private UUID institutionId;

    /** Non-null only when scopeType = DEPARTMENT (ck_user_role_assignment_r2). */
    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    /** "Ending logs the user out; new rights within 1 minute" (schema comment, WF-27/PQ-20) -
     *  never a DELETE, always a status flip to ENDED (Addendum rule 17). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status;

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

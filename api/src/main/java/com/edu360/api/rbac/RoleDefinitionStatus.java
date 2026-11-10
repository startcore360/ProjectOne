package com.edu360.api.rbac;

/**
 * WHAT: app.role_definition.status - mirrors ck_role_definition_status as widened by
 * sql/16_v41_tenant_top_post_authority.sql: DRAFT, ACTIVE, INACTIVE, RETIRED, ARCHIVED
 * (Addendum rule 16 lifecycle: Draft, Active, Inactive, Archived; RETIRED is the rule-17
 * "no hard delete" end state kept from the original schema).
 *
 * Q-02 (DB-A11): only ACTIVE grants rights. PermissionEvaluatorService denies every other status
 * (ROLE_NOT_ACTIVE), and a role that is not ACTIVE cannot be newly assigned. Every value the
 * column can hold is listed here, so reading any row never fails.
 */
public enum RoleDefinitionStatus {
    DRAFT,
    ACTIVE,
    INACTIVE,
    RETIRED,
    ARCHIVED
}

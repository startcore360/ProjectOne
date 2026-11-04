package com.edu360.api.rbac;

/**
 * WHAT: the three scopes a user_role_assignment can be held at - mirrors
 * ck_user_role_assignment_scope_type in sql/01_schema.sql word for word. Keep the two in
 * lockstep by hand; a drift fails fast in Java instead of failing the database CHECK on insert.
 *
 * WHY only these three (no PLATFORM here): app.user_role_assignment is a tenant-side table -
 * platform staff authorization is a completely separate model (platform.PlatformRole), never
 * mixed into this one (Addendum rule 13, tenant/platform isolation).
 */
public enum ScopeType {
    TENANT,
    INSTITUTION,
    DEPARTMENT
}

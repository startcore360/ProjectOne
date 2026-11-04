package com.edu360.api.rbac;

/**
 * WHAT: app.role_definition.custom_scope - mirrors ck_role_definition_custom_scope. Only set
 * when role_origin = CUSTOM (ck_role_definition_r1). Addendum rule 4/6: a University Admin may
 * build only university-office custom roles, never institution-level ones, and role creation
 * must validate the administrator's delegated authority against this value - that validation
 * belongs to the (not-yet-built) role-management service, not to this read model.
 */
public enum CustomScope {
    UNIVERSITY_OFFICE,
    INSTITUTION
}

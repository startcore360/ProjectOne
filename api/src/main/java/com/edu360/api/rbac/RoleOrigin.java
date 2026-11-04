package com.edu360.api.rbac;

/**
 * WHAT: app.role_definition.role_origin - mirrors ck_role_definition_role_origin.
 * STANDARD = copied verbatim from a ref.standard_role template at provisioning (Addendum rule
 * 3: standard roles are suggested templates, not compulsory). CUSTOM = built by a tenant
 * administrator from approved actions only (Addendum rule 4).
 */
public enum RoleOrigin {
    STANDARD,
    CUSTOM
}

package com.edu360.api.rbac;

/**
 * WHAT: the organisational-hierarchy level a role sits at (Addendum rule 1: role ownership
 * follows organisational hierarchy; rule 2: a lower-level administrator cannot manage a
 * higher-level-owned role). Mirrors ck_standard_role_role_level, which is the wider of the two
 * CHECK constraints that use this concept: ref.standard_role allows PLATFORM (the 6 platform
 * roles) in addition to the five tenant-side levels. app.role_definition's own
 * ck_role_definition_role_level is narrower (no PLATFORM) - the database enforces that
 * narrower rule on its own column; this single Java enum is reused for both entities rather
 * than duplicated, since every RoleLevel value app.role_definition.role_level can ever hold is
 * already a subset of this one.
 */
public enum RoleLevel {
    PLATFORM,
    TENANT,
    UNIVERSITY,
    COLLEGE,
    SCHOOL,
    EXTERNAL
}

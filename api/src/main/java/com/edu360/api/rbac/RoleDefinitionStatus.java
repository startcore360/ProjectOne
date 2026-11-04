package com.edu360.api.rbac;

/**
 * WHAT: app.role_definition.status - mirrors ck_role_definition_status in sql/01_schema.sql
 * word for word: only ACTIVE and RETIRED exist in the committed schema today.
 *
 * KNOWN GAP (flagged, not silently resolved): the Product Owner's Addendum rule 16 calls for a
 * four-state role lifecycle - Draft, Active, Inactive, Archived. The committed sql/01_schema.sql
 * only has two states. Per CLAUDE.md this repo does not get a second migration mechanism and
 * sql/ is explicitly off limits to this change, so this enum intentionally matches the DATABASE
 * exactly rather than inventing Java-side states the column cannot store. RETIRED is used here
 * as the rule-17 "no hard delete" terminal state (archive-equivalent); Draft and Inactive are
 * NOT representable until the schema is revisited with whoever owns sql/ - do not add a third
 * value to this enum without first adding it to ck_role_definition_status in the same change.
 */
public enum RoleDefinitionStatus {
    ACTIVE,
    RETIRED
}

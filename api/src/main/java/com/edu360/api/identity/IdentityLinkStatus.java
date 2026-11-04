package com.edu360.api.identity;

/** Mirrors ck_user_identity_link_status. REVOKED (not deleted) is how a link is cut - e.g. an
 *  old guardian losing access (GUARDIAN_CHANGE_APPROVE) - keeping the row as an audit trail
 *  instead of removing it, consistent with "no hard delete" elsewhere in this schema. */
public enum IdentityLinkStatus {
    ACTIVE,
    REVOKED
}

package com.edu360.api.identity;

/** Mirrors ck_user_account_status in sql/01_schema.sql. A brand new account starts
 *  PENDING_ACTIVATION until its first successful login; DEACTIVATED is permanent in this
 *  schema - there is no "reactivate", matching the "no hard delete" rule elsewhere in V4.0. */
public enum UserAccountStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    DEACTIVATED
}

package com.edu360.api.audit;

/**
 * The kinds of security events we keep a record of: someone logging in,
 * failing to log in, getting locked out, logging out, or a new account
 * being created. See AuditLog for where these get stored.
 */
public enum AuditEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    ACCOUNT_LOCKED,
    LOGOUT,
    ACCOUNT_CREATED
}

package com.edu360.api.audit;

/**
 * The kinds of security events we keep a record of: a login attempt (success or failure),
 * logging out, or a new account being created. Keycloak itself verifies the password - this
 * app only records the outcome, since AuthController.login forwards every attempt through
 * KeycloakAuthClient rather than checking credentials itself. See AuditLog for where these
 * get stored.
 */
public enum AuditEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    ACCOUNT_CREATED,
    PASSWORD_CHANGED
}

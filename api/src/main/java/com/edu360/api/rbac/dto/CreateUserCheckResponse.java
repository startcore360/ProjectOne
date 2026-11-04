package com.edu360.api.rbac.dto;

/**
 * WHAT: the response for the USER_CREATE proof-of-integration endpoint - confirms the caller is
 * authorized to proceed. There is no "user created" payload: this endpoint is the
 * authorization check only, not the full USER_CREATE story (see the controller's javadoc).
 */
public record CreateUserCheckResponse(String actionCode, String reasonCode, String message) {
}

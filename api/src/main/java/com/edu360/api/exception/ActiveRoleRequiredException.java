package com.edu360.api.exception;

/**
 * Thrown when a signed-in tenant user holds more than one active role assignment and has not
 * chosen which one is active (A-02, story 6, PO-3, Addendum rules 8-10, D3 CR-11). Only the
 * active role's permissions may apply to a request, so the server refuses to guess and
 * GlobalExceptionHandler returns 409 with code ACTIVE_ROLE_REQUIRED; the screen then sends the
 * user to the role switcher.
 */
public class ActiveRoleRequiredException extends RuntimeException {

    public static final String CODE = "ACTIVE_ROLE_REQUIRED";

    public ActiveRoleRequiredException(String message) {
        super(message);
    }
}

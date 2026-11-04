package com.edu360.api.rbac;

/**
 * WHAT: the clear allow/deny result PermissionEvaluatorService.can(...) always returns -
 * never a thrown exception (see that class's javadoc for why). reasonCode is a short, stable,
 * machine-checkable code (asserted on directly in tests); message is the human-readable text
 * that becomes the 403 body's "error" field when a denial is turned into an UnauthorizedException
 * at the controller boundary (see PermissionEvaluatorService.authorize(...)).
 */
public record PermissionDecision(boolean allowed, String reasonCode, String message) {

    public static PermissionDecision allow() {
        return new PermissionDecision(true, "ALLOWED", "Permitted.");
    }

    public static PermissionDecision deny(String reasonCode, String message) {
        return new PermissionDecision(false, reasonCode, message);
    }
}

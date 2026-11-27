package com.edu360.api.exception;

import java.util.List;

/**
 * Thrown when someone is logged in but not allowed to do the thing they're
 * trying to do. Despite the name, GlobalExceptionHandler turns this into a
 * 403 Forbidden response, not a 401 — the caller IS known, they're just blocked.
 *
 * The optional fields let the response carry a real reason instead of a bare
 * "Access denied": reasonCode mirrors PermissionDecision.reasonCode() (e.g.
 * ACTION_NOT_GRANTED), and heldByRoleNames names up to a couple of roles that
 * currently hold the action, so the UI can say "ask a Registrar" instead of a
 * generic "ask your administrator". heldByRoleNames must stay empty for any
 * sensitive/locked action — callers decide that before constructing this.
 */
public class UnauthorizedException extends RuntimeException {
    private final String reasonCode;
    private final String actionCode;
    private final String actionDescription;
    private final List<String> heldByRoleNames;

    public UnauthorizedException(String message) {
        this(message, null, null, null, List.of());
    }

    public UnauthorizedException(String message, String reasonCode, String actionCode,
            String actionDescription, List<String> heldByRoleNames) {
        super(message);
        this.reasonCode = reasonCode;
        this.actionCode = actionCode;
        this.actionDescription = actionDescription;
        this.heldByRoleNames = heldByRoleNames == null ? List.of() : heldByRoleNames;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getActionCode() {
        return actionCode;
    }

    public String getActionDescription() {
        return actionDescription;
    }

    public List<String> getHeldByRoleNames() {
        return heldByRoleNames;
    }
}

package com.edu360.api.rbac;

import java.util.UUID;

/**
 * WHAT: the "record" half of PermissionEvaluatorService.can(principal, actionCode, scope,
 * target) - who/what the action would be performed against, kept to exactly the one field the
 * Addendum rule 11 guard needs today (self-elevation/self-approval): is the target the same
 * person as the principal. {@link #NONE} is for actions with no natural "acted-upon person"
 * (e.g. USER_CREATE, where the target does not exist yet).
 */
public record RbacTarget(UUID targetUserAccountId) {

    public static final RbacTarget NONE = new RbacTarget(null);

    public static RbacTarget of(UUID targetUserAccountId) {
        return new RbacTarget(targetUserAccountId);
    }
}

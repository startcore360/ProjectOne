package com.edu360.api.rbac.dto;

import java.util.UUID;

import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserType;

/**
 * WHAT: the shared user-account projection returned by the USER_VIEW (list/detail) and
 * USER_EDIT endpoints - deliberately just the app.user_account columns that are safe to hand
 * back to an authorized caller (no password, no Keycloak subject, no row_version yet - see
 * UserAccount's own javadoc for why optimistic-locking plumbing is deferred).
 */
public record UserSummaryResponse(
        UUID userAccountId,
        UserType userType,
        String displayName,
        String email,
        String mobile,
        UserAccountStatus status) {
}

package com.edu360.api.rbac.dto;

/**
 * WHAT: the response for POST /api/tenants/{tenantId}/users/{userId}/password-reset
 * (USER_PASSWORD_RESET_UNLOCK). temporaryPassword is only present on a successful reset.
 *
 * SECURITY NOTE (flagged deliberately, not silently decided): returning the freshly generated
 * temporary password in the API response is a proof-of-integration simplification - it is what
 * lets this endpoint be useful at all today, since there is no secure out-of-band delivery
 * channel (e.g. a notification/email service) built yet to hand it to the target user instead. A
 * production-ready version of this story should deliver it through such a channel and have this
 * endpoint return no credential material in the response body at all. Do not log this response.
 */
public record PasswordResetResponse(String actionCode, String reasonCode, String message, String temporaryPassword) {
}

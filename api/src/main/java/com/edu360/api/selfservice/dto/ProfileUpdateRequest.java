package com.edu360.api.selfservice.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * What PATCH /api/me/profile accepts. The DTO shape itself is the "never allowed to change own
 * role" guard (PROFILE_EDIT_OWN, Sign-in epic, Scope: Self): there is deliberately no roles/
 * platformRole/userType/status field here at all, so there is nothing a caller could set even by
 * sending extra JSON - Jackson ignores unknown properties, and this class simply never declares
 * the ones that must stay server-controlled. That is enforced by the type, not by a runtime
 * "strip privileged fields" check that a future edit could forget.
 *
 * Both fields are optional (null = "leave unchanged") so a caller can update just one of them -
 * there is no way to express "clear this field back to empty" via this endpoint today, which is
 * an acceptable gap for a first slice (same partial-update style as the rest of this app's
 * PATCH-less, full-replace-only entities, deliberately kept simpler here).
 *
 * email is intentionally NOT editable here, even though it is a "contact detail" on UserAccount/
 * PlatformUser: for STAFF (UsernameKind.EMAIL) and every platform account, email doubles as the
 * Keycloak login username. Letting self-service change it would desync the Keycloak account from
 * app.user_account/platform.platform_user without an accompanying Keycloak admin update, which is
 * a bigger, riskier piece of work than this slice - flagged here rather than silently supported.
 */
@Data
public class ProfileUpdateRequest {

    @Size(min = 1, max = 255, message = "Display name must not be blank")
    private String displayName;

    /** Tenant accounts only - ignored for a platform principal (PlatformUser has no mobile
     *  column). E.164-ish shape, same intent as the mobile column's own schema comment. */
    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Mobile must be a valid E.164-style number")
    private String mobile;

    /** Verification code required when a parent (GUARDIAN) updates their mobile number (Rule WF-30). */
    private String otpCode;
}

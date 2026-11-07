package com.edu360.api.auth.dto;

import java.util.UUID;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.identity.UserType;
import com.edu360.api.platform.PlatformRole;

/**
 * The safe, public shape of a signed-in identity returned to the browser - built from whichever
 * principal type authenticated (see TenantPrincipal/PlatformPrincipal). Exactly one of
 * (tenantId, userType) or (platformRole) is populated, never both - the two are deliberately
 * different identity spaces (see PlatformUser's class comment).
 *
 * roleCode/roleName are the tenant user's resolved app.role_definition (via their ACTIVE
 * app.user_role_assignment - see AuthService's role resolution) - null for platform principals,
 * and null for a tenant user with no active role assignment yet. This is read-only display data
 * for the frontend to choose a role-appropriate dashboard; it grants nothing by itself -
 * PermissionEvaluatorService still independently enforces every actual action server-side.
 *
 * photoUrl: same meaning and same null cases as ProfileResponse.photoUrl (see that class) - null
 * for platform principals (app.file_object is tenant-scoped, so a platform account has nowhere
 * in this schema to own a stored file) and null when the tenant user has no active photo yet.
 */
public record AuthUserResponse(
        UUID id,
        String displayName,
        UUID tenantId,
        UserType userType,
        PlatformRole platformRole,
        String roleCode,
        String roleName,
        String photoUrl,
        boolean mustChangePassword,
        boolean mfaRequired) {

    public AuthUserResponse(UUID id, String displayName, UUID tenantId, UserType userType,
                            PlatformRole platformRole, String roleCode, String roleName,
                            String photoUrl, boolean mustChangePassword) {
        this(id, displayName, tenantId, userType, platformRole, roleCode, roleName, photoUrl, mustChangePassword, false);
    }

    public static AuthUserResponse fromTenant(TenantPrincipal principal, String displayName) {
        return fromTenant(principal, displayName, null, null, null, false);
    }

    public static AuthUserResponse fromTenant(TenantPrincipal principal, String displayName,
                                                String roleCode, String roleName, String photoUrl) {
        return fromTenant(principal, displayName, roleCode, roleName, photoUrl, false);
    }

    public static AuthUserResponse fromTenant(TenantPrincipal principal, String displayName,
                                                String roleCode, String roleName, String photoUrl, boolean mfaRequired) {
        return new AuthUserResponse(principal.userAccountId(), displayName, principal.tenantId(),
                principal.userType(), null, roleCode, roleName, photoUrl, principal.mustChangePassword(), mfaRequired);
    }

    public static AuthUserResponse fromPlatform(PlatformPrincipal principal) {
        return fromPlatform(principal, true);
    }

    public static AuthUserResponse fromPlatform(PlatformPrincipal principal, boolean mfaRequired) {
        return new AuthUserResponse(principal.platformUserId(), principal.email(), null, null,
                principal.platformRole(), null, null, null, principal.mustChangePassword(), mfaRequired);
    }
}

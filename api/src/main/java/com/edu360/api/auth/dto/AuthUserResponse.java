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
 */
public record AuthUserResponse(
        UUID id,
        String displayName,
        UUID tenantId,
        UserType userType,
        PlatformRole platformRole,
        boolean mustChangePassword) {

    public static AuthUserResponse fromTenant(TenantPrincipal principal, String displayName) {
        return new AuthUserResponse(principal.userAccountId(), displayName, principal.tenantId(),
                principal.userType(), null, principal.mustChangePassword());
    }

    public static AuthUserResponse fromPlatform(PlatformPrincipal principal) {
        return new AuthUserResponse(principal.platformUserId(), principal.email(), null, null,
                principal.platformRole(), principal.mustChangePassword());
    }
}

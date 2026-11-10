package com.edu360.api.selfservice.dto;

import java.util.UUID;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserType;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platform.PlatformUser;

import lombok.Builder;

/**
 * WHAT: GET /api/me/profile response - the authenticated principal's own profile, nothing it
 * cannot already see about itself via GET /api/auth/me plus the contact fields that endpoint
 * does not return (mobile; the tenant/platform id fields it already had are repeated here so
 * this response is self-contained for a "my profile" screen).
 *
 * roles/platformRole are included READ-ONLY (no setter exposed by ProfileUpdateRequest - see
 * that class) precisely so the screen can show them without being able to change them.
 *
 * photoUrl: null if this principal has no active profile photo, otherwise the path the frontend
 * can GET to stream it (ProfilePhotoController). Always null for a platform principal -
 * app.file_object is tenant-scoped (tenant_id NOT NULL), so a platform account literally has
 * nowhere in this schema to own a stored file; see ProfileService's class comment.
 */
@Builder
public record ProfileResponse(
        UUID tenantId,
        UUID userAccountId,
        UserType userType,
        UUID platformUserId,
        PlatformRole platformRole,
        String displayName,
        String email,
        String mobile,
        String photoUrl) {

    public static ProfileResponse fromTenant(TenantPrincipal principal, UserAccount account, String photoUrl) {
        return ProfileResponse.builder()
                .tenantId(principal.tenantId())
                .userAccountId(principal.userAccountId())
                .userType(principal.userType())
                .displayName(account.getDisplayName())
                .email(account.getEmail())
                .mobile(account.getMobile())
                .photoUrl(photoUrl)
                .build();
    }

    public static ProfileResponse fromPlatform(PlatformPrincipal principal, PlatformUser user) {
        return ProfileResponse.builder()
                .platformUserId(principal.platformUserId())
                .platformRole(principal.platformRole())
                .displayName(user.getFullName())
                .email(user.getEmail())
                .build();
    }
}

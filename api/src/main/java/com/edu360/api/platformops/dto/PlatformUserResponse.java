package com.edu360.api.platformops.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserStatus;

/**
 * tempPassword is non-null only on the response to the create call that generated it - it is
 * never persisted anywhere and never appears again on a later read, same "shown once" contract
 * as TopUserResponse/UserPasswordResetUnlockController.
 */
public record PlatformUserResponse(
        UUID platformUserId,
        String fullName,
        String email,
        PlatformRole platformRole,
        UUID platformRoleId,
        String customRoleName,
        PlatformUserStatus status,
        boolean mfaRequired,
        Instant lastLoginAt,
        Instant createdAt,
        String tempPassword
) {
    public static PlatformUserResponse from(PlatformUser user, String customRoleName, String tempPassword) {
        return new PlatformUserResponse(
                user.getPlatformUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getPlatformRole(),
                user.getPlatformRoleId(),
                customRoleName,
                user.getStatus(),
                user.isMfaRequired(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                tempPassword
        );
    }

    public static PlatformUserResponse from(PlatformUser user) {
        return from(user, null, null);
    }
}

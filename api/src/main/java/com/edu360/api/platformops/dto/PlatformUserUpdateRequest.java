package com.edu360.api.platformops.dto;

import java.util.UUID;

import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platform.PlatformUserStatus;

import jakarta.validation.constraints.Size;

/**
 * D5 updatePlatformUser. Every field is optional. To change what someone can do, send platformRole
 * for a standard role or platformRoleId for a custom one, never both.
 */
public record PlatformUserUpdateRequest(
        @Size(min = 1, max = 150) String fullName,
        PlatformRole platformRole,
        UUID platformRoleId,
        PlatformUserStatus status
) {
}

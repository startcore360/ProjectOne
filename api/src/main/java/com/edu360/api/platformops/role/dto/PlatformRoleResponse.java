package com.edu360.api.platformops.role.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.role.PlatformCustomRoleStatus;

public record PlatformRoleResponse(
        UUID platformRoleId,
        String roleName,
        PlatformRole basePlatformRole,
        UUID sourceTemplateId,
        PlatformCustomRoleStatus status,
        List<RoleActionItem> actions,
        long holders,
        Instant createdAt
) {
}

package com.edu360.api.platformops.role.dto;

import java.util.List;
import java.util.UUID;

import com.edu360.api.platform.PlatformRole;

public record PlatformRoleTemplateResponse(
        UUID platformRoleTemplateId,
        String templateName,
        String description,
        PlatformRole basePlatformRole,
        boolean standard,
        boolean locked,
        String status,
        List<String> actionCodes
) {
}

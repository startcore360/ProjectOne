package com.edu360.api.platformops.dto;

import java.util.UUID;

public record TenantFirstAdminPasswordResetResponse(
        UUID tenantId,
        String tenantName,
        String adminEmail,
        String temporaryPassword
) {}

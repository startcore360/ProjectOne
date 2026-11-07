package com.edu360.api.selfservice.dto;

import java.util.List;
import java.util.UUID;
import com.edu360.api.identity.UserType;

/**
 * Complete self-identity response for GET /v1/tenants/{tenantId}/me (and /api/me).
 * Returns the caller's profile, active role assignment, and all available role options for switching.
 */
public record UserMeResponse(
        UUID userAccountId,
        UUID tenantId,
        String email,
        String displayName,
        UserType userType,
        String currentRoleCode,
        String currentRoleName,
        UUID currentAssignmentId,
        List<UserRoleOption> roles
) {}

package com.edu360.api.rolldown;

import java.time.Instant;
import java.util.UUID;

public record RolldownOverrideDto(
        UUID roleRolldownOverrideId,
        UUID institutionId,
        String missingRoleCode,
        String permissionKind,
        String actionCode,
        String mode,
        String targetRoleCode,
        String reason,
        UUID setByUserId,
        String setByRole,
        String status,
        Instant effectiveFrom,
        Instant createdAt
) {
    public static RolldownOverrideDto from(RoleRolldownOverride o) {
        return new RolldownOverrideDto(
                o.getId().getRoleRolldownOverrideId(),
                o.getInstitutionId(),
                o.getMissingRoleCode(),
                o.getPermissionKind(),
                o.getActionCode(),
                o.getMode(),
                o.getTargetRoleCode(),
                o.getReason(),
                o.getSetByUserId(),
                o.getSetByRole(),
                o.getStatus(),
                o.getEffectiveFrom(),
                o.getCreatedAt()
        );
    }
}

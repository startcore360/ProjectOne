package com.edu360.api.rolldown;

public record RolldownMapEntryDto(
        String missingRoleCode,
        String context,
        String permissionKind,
        short preference,
        String standInRoleCode,
        String note,
        boolean hasOverride,
        String overrideMode,
        String overrideTargetRoleCode
) {}

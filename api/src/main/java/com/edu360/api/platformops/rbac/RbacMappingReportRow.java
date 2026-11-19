package com.edu360.api.platformops.rbac;

import java.util.UUID;

public record RbacMappingReportRow(
        String scope,
        UUID tenantId,
        String tenantName,
        String roleCode,
        String roleName,
        String actionCode,
        String result,
        String detail) {
}

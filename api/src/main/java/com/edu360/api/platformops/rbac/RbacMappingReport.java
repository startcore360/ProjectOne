package com.edu360.api.platformops.rbac;

import java.util.List;
import java.util.UUID;

public record RbacMappingReport(
        List<RbacMappingReportRow> mismatches,
        List<RbacMappingReportRow> noBaseTemplate,
        List<SkippedTenant> skippedTenants) {
    public record SkippedTenant(UUID tenantId, String tenantName, String state) {}
}

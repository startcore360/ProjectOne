package com.edu360.api.platformops.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * POST /platform/tenants/{id}/provisioning/retry (E1-S2). The body is optional: the retention
 * values are only needed when the retention step (P09) itself did not finish, because they are
 * not stored anywhere until that step succeeds.
 */
public record TenantProvisioningRetryRequest(
        @Min(1) @Max(240) Integer retentionMonths,
        @Min(7) @Max(3650) Integer backupRetentionDays) {
}

package com.edu360.api.platformops.dto;

import java.util.List;
import java.util.UUID;

/** Current setup state for a tenant. Step ordering is supplied by the provisioning orchestrator. */
public record TenantProvisioningStatusResponse(
        UUID tenantId,
        String overallStatus,
        boolean retryable,
        List<TenantProvisioningStepResponse> steps
) {
    public TenantProvisioningStatusResponse {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}

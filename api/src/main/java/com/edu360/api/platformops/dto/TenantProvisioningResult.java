package com.edu360.api.platformops.dto;

import java.util.UUID;

/**
 * Initial tenant-creation result. The temporary password is returned only in this response and
 * must never be persisted or included in later provisioning-status responses.
 */
public record TenantProvisioningResult(
        UUID tenantId,
        String loginAddress,
        UUID firstAdminUserId,
        String firstAdminTemporaryPassword,
        TenantProvisioningStatusResponse provisioning
) {
}

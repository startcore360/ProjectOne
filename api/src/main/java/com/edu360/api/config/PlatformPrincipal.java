package com.edu360.api.config;

import java.util.UUID;

import com.edu360.api.platform.PlatformRole;

/**
 * The Authentication principal for a request that authenticated against the PLATFORM realm
 * (edu360-platform). Has no tenantId field at all, on purpose - a platform login never carries
 * tenant context, so TenantContext.set(...) is never called for one (see TenantContext's class
 * comment on why that is what keeps Platform Admin from becoming an implicit RLS bypass).
 */
public record PlatformPrincipal(
        UUID platformUserId,
        PlatformRole platformRole,
        String email,
        boolean mustChangePassword) {
}

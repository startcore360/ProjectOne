package com.edu360.api.config;

import java.util.UUID;

import com.edu360.api.identity.UserType;

/**
 * The Authentication principal for a request that authenticated against a TENANT realm
 * (abc-university, xyz-university, ...). Carries exactly what the rest of the app needs without
 * another database round trip per request: which tenant, which user, what kind of user.
 *
 * Deliberately a different Java type from PlatformPrincipal (see PlatformUser's class comment)
 * so code can never accidentally treat a platform login as a tenant login or vice versa - the
 * compiler enforces the separation the Sprint 1 brief asked for, not just a runtime check.
 */
public record TenantPrincipal(
        UUID tenantId,
        UUID userAccountId,
        UserType userType,
        String displayName,
        boolean mustChangePassword) {
}

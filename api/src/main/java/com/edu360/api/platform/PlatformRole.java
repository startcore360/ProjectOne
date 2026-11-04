package com.edu360.api.platform;

/** Mirrors ck_platform_user_platform_role exactly - the six platform-level roles (D2/D3
 *  Platform epic). Deliberately a closed set, not the tenant role_definition model - platform
 *  staff never hold a tenant role and vice versa. */
public enum PlatformRole {
    PRODUCT_OWNER,
    PLATFORM_ADMIN,
    BILLING_MANAGER,
    SUPPORT_MANAGER,
    RELEASE_MANAGER,
    DATA_PROTECTION_OFFICER
}

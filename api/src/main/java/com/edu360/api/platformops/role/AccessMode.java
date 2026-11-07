package com.edu360.api.platformops.role;

/**
 * Mirrors ck_platform_role_action_mode (TPA-02). SHARED: the Product Owner can also act.
 * HANDED_OVER: while an active person holds the role, the Product Owner acts only by stepping in.
 */
public enum AccessMode {
    SHARED,
    HANDED_OVER
}

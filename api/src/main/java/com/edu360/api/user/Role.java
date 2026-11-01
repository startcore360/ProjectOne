package com.edu360.api.user;

/** The three kinds of account this app knows about. */
public enum Role {
    // Matches the "Platform Admin" role in the V4.0 role catalogue. Deliberate simplification:
    // in the real V4.0 model, Platform Admin only creates/activates TENANTS (USER_CREATE
    // belongs to each institution's own University/College/School Admin — see
    // docs/V4.0_SPEC_SUMMARY.md §2/§7). This app has no tenant/institution concept yet, so
    // PLATFORM_ADMIN stands in as the one account-creator role until that model exists.
    // It is the only role allowed to create accounts (see AuthController.register) —
    // there is no self-service sign-up.
    PLATFORM_ADMIN,
    TEACHER,
    STUDENT
}

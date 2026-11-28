package com.edu360.api.institution;

import com.edu360.api.rbac.dto.CreateUserResponse;

/** One-time onboarding response. temporaryPassword is returned only at account creation. */
public record StaffOnboardingResponse(
        StaffProfileResponse staff,
        CreateUserResponse login,
        boolean personalEmailVerificationRequired
) {}

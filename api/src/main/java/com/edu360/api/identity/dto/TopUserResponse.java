package com.edu360.api.identity.dto;

import java.util.UUID;

import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserType;

public record TopUserResponse(
        UUID userAccountId,
        String displayName,
        String email,
        String mobile,
        String roleCode,
        UserType userType,
        UserAccountStatus status,
        String temporaryPassword,
        boolean twoStepLoginRequired,
        String message
) {}

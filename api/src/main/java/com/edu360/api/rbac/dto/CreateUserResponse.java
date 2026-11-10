package com.edu360.api.rbac.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserType;

public record CreateUserResponse(
        UUID userAccountId,
        UUID tenantId,
        UserType userType,
        String displayName,
        String email,
        String mobile,
        UserAccountStatus status,
        String temporaryPassword,
        String initialRole,
        Instant createdAt
) {
    public static CreateUserResponse from(
            UserAccount account,
            String temporaryPassword,
            String initialRole) {
        return new CreateUserResponse(
                account.getId().getUserAccountId(),
                account.getId().getTenantId(),
                account.getUserType(),
                account.getDisplayName(),
                account.getEmail(),
                account.getMobile(),
                account.getStatus(),
                temporaryPassword,
                initialRole,
                account.getCreatedAt() != null ? account.getCreatedAt() : Instant.now()
        );
    }
}

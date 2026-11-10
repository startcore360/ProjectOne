package com.edu360.api.platformops.dto;

import java.util.UUID;

import com.edu360.api.platform.PlatformRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for creating a platform staff account (PLATFORM_USER_MANAGE).
 * Field constraints mirror platform.platform_user's own CHECK constraints
 * (ck_platform_user_full_name, ck_platform_user_email) so a bad request is rejected here
 * rather than surfacing as an opaque 500 from Postgres.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformUserCreateRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 1, max = 150, message = "Full name must be between 1 and 150 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    /** A standard role. Send this or platformRoleId, not both. */
    private PlatformRole platformRole;

    /** A custom role. Send this or platformRole, not both. */
    private UUID platformRoleId;
}

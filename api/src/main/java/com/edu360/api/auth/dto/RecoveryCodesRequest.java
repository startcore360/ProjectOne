package com.edu360.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** POST /api/auth/mfa/recovery-codes (MFA-01): the current password, proven again. */
@Data
public class RecoveryCodesRequest {

    @NotBlank(message = "Enter your current password")
    private String currentPassword;
}

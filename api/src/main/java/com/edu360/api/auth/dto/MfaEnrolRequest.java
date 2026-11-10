package com.edu360.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * POST /api/auth/mfa/enrol - finishes the in-app authenticator set-up started by a
 * MFA_ENROLMENT_REQUIRED sign-in answer. The password is sent again and re-proven: the set-up
 * id alone is never enough to attach an authenticator to an account.
 */
@Data
public class MfaEnrolRequest {
    @NotBlank(message = "Email or student ID is required")
    private String email;
    @NotBlank(message = "Password is required")
    private String password;
    @NotBlank(message = "The set-up has expired. Sign in again to start over.")
    @Pattern(regexp = "^[0-9a-f]{16,128}$", message = "The set-up has expired. Sign in again to start over.")
    private String enrolmentId;
    @NotBlank(message = "Enter the 6-digit code from your authenticator app")
    @Pattern(regexp = "^[0-9]{6}$", message = "The authenticator code is 6 digits")
    private String code;
    @Size(max = 50, message = "Device name can be at most 50 characters")
    private String deviceName;
}

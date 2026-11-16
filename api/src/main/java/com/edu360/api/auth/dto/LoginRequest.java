package com.edu360.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * What the browser sends to POST /api/auth/login: an identifier (staff/platform: email; college
 * student: Student ID), a password and - only on the second step of a two-step sign-in (I-01) -
 * the 6-digit code from the user's authenticator app.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Email or student ID is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @Pattern(regexp = "^$|^[0-9]{6}$", message = "The authenticator code is 6 digits")
    private String totp;

    /** MFA-01: a one-time backup code, used instead of totp when the authenticator is lost. */
    @Pattern(regexp = "^$|^[A-Za-z0-9\\- ]{10,14}$", message = "A backup code looks like K7QD4-9MXRT")
    private String recoveryCode;
}

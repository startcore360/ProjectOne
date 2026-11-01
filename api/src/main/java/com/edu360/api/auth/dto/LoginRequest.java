package com.edu360.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** What the browser sends to POST /api/auth/login: an identifier and a password. */
@Data
public class LoginRequest {

    // Accepts a school email or a student ID, matching the login screen's own label.
    // There is no separate student-ID lookup yet; AuthService still matches by email,
    // so a non-email identifier correctly falls through to the generic invalid-credentials
    // error below rather than being rejected by format validation before that check runs.
    @NotBlank(message = "Email or student ID is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}

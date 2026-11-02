package com.edu360.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** What the browser sends to POST /api/auth/login: an identifier and a password. */
@Data
public class LoginRequest {

    @NotBlank(message = "Email or student ID is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}

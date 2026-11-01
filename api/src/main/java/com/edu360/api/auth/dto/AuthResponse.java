package com.edu360.api.auth.dto;

import com.edu360.api.user.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Internal result of a login or account creation: who the user is, their
 * role, and the signed token that proves they're logged in. This never
 * goes straight to the browser - AuthController wraps the token in a
 * cookie first and sends AuthUserResponse back instead.
 */
@Data
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    private Long userId;
    private String name;
    private String email;
    private Role role;
}

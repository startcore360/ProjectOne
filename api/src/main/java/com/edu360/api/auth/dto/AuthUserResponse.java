package com.edu360.api.auth.dto;

import com.edu360.api.user.Role;
import com.edu360.api.user.User;

/**
 * The safe, public shape of a user that we're allowed to send back to the
 * browser - just id, name, email, and role. Never includes the password.
 */
public record AuthUserResponse(Long id, String name, String email, Role role) {
    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}

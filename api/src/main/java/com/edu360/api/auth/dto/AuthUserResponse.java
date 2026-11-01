package com.edu360.api.auth.dto;

import com.edu360.api.user.Role;
import com.edu360.api.user.User;

public record AuthUserResponse(Long id, String name, String email, Role role) {
    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}

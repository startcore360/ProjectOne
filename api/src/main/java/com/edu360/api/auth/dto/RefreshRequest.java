package com.edu360.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** What the browser sends to POST /api/auth/refresh to get a new access token. */
@Data
public class RefreshRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}

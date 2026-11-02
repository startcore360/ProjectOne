package com.edu360.api.auth.dto;

import lombok.Builder;

/** What POST /api/auth/login and /api/auth/refresh return: the Keycloak-issued tokens the
 *  frontend needs to attach as a bearer header, plus who they belong to. */
@Builder
public record AuthTokenResponse(String accessToken, String refreshToken, long expiresInSeconds,
                                 AuthUserResponse user) {
}

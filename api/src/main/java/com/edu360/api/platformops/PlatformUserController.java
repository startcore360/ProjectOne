package com.edu360.api.platformops;

import com.edu360.api.platformops.dto.PlatformUserUpdateRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import java.util.UUID;
import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.dto.PlatformUserCreateRequest;
import com.edu360.api.platformops.dto.PlatformUserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller exposing platform staff account management:
 * - PLATFORM_USER_MANAGE: POST, GET and PATCH /v1/platform/platform-users
 *
 * PLATFORM_USER_MANAGE is granted to PRODUCT_OWNER only (ref.standard_role_action row 1826) -
 * deliberately narrower than the other platform actions, which also allow PLATFORM_ADMIN.
 */
@RestController
@RequestMapping({"/v1/platform/platform-users", "/api/platform/platform-users"})
@RequiredArgsConstructor
@Tag(name = "Platform Users", description = "Platform staff account management (PLATFORM_USER_MANAGE)")
public class PlatformUserController {

    private final PlatformPermissionService platformPermissionService;
    private final PlatformUserManagementService platformUserManagementService;

    @PostMapping
    @Operation(summary = "Create a platform staff account (PLATFORM_USER_MANAGE)")
    public ResponseEntity<PlatformUserResponse> createPlatformUser(
            @Valid @RequestBody PlatformUserCreateRequest request,
            Authentication authentication) {

        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "PLATFORM_USER_MANAGE");

        PlatformUserResponse created = platformUserManagementService.createPlatformUser(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/platform/platform-users/" + created.platformUserId()))
                .body(created);
    }

    @PatchMapping("/{platformUserId}")
    @Operation(summary = "Change a platform staff account's name, role or status (PLATFORM_USER_MANAGE)")
    public ResponseEntity<PlatformUserResponse> updatePlatformUser(
            @PathVariable UUID platformUserId,
            @Valid @RequestBody PlatformUserUpdateRequest request,
            Authentication authentication) {

        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "PLATFORM_USER_MANAGE");
        return ResponseEntity.ok(platformUserManagementService.updatePlatformUser(platformUserId, request, principal));
    }

    @GetMapping
    @Operation(summary = "List platform staff accounts")
    public ResponseEntity<List<PlatformUserResponse>> listPlatformUsers(Authentication authentication) {
        platformPermissionService.authorize(requirePlatformPrincipal(authentication), "PLATFORM_USER_MANAGE");
        return ResponseEntity.ok(platformUserManagementService.listPlatformUsers());
    }

    private PlatformPrincipal requirePlatformPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("Platform credentials required; no platform principal on this request.");
        }
        return principal;
    }
}

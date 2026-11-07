package com.edu360.api.selfservice;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.selfservice.dto.ProfileResponse;
import com.edu360.api.selfservice.dto.ProfileUpdateRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * PROFILE_EDIT_OWN (Sign-in epic, Scope: Self). Any authenticated principal - tenant or
 * platform - can see and edit its own profile; see ProfileService's class comment for the /api
 * path deviation from the V4.0 spec's unimplemented /v1/tenants/{tenantId}/me, and
 * ProfileUpdateRequest's class comment for why roles/platformRole/email are not editable here.
 *
 * "Who am I" is resolved from the Authentication principal exactly the way AuthController.me()
 * already does it - never from a request parameter, so self-only is a property of the code, not
 * a check that could be bypassed by a caller-supplied id.
 */
@RestController
@RequestMapping({"/api/me/profile", "/v1/tenants/{tenantId}/me/profile", "/api/tenants/{tenantId}/me/profile"})
@RequiredArgsConstructor
@Tag(name = "Self-service", description = "See and edit your own profile and contact details")
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    @Operation(summary = "Get the authenticated principal's own profile")
    public ResponseEntity<ProfileResponse> getProfile(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ProfileResponse response = switch (authentication.getPrincipal()) {
            case TenantPrincipal tenant -> profileService.getTenantProfile(tenant);
            case PlatformPrincipal platform -> profileService.getPlatformProfile(platform);
            case null, default -> null;
        };
        if (response == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(response);
    }

    @PatchMapping
    @Operation(summary = "Edit the authenticated principal's own display name/contact details "
            + "(never roles - see ProfileUpdateRequest)")
    public ResponseEntity<ProfileResponse> updateProfile(@Valid @RequestBody ProfileUpdateRequest request,
                                                           Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ProfileResponse response = switch (authentication.getPrincipal()) {
            case TenantPrincipal tenant -> profileService.updateTenantProfile(tenant, request);
            case PlatformPrincipal platform -> profileService.updatePlatformProfile(platform, request);
            case null, default -> null;
        };
        if (response == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload your own profile photo (JPEG/PNG, 2 MB max; tenant accounts only)")
    public ResponseEntity<ProfileResponse> uploadPhoto(@RequestParam("file") MultipartFile file,
                                                          Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return switch (authentication.getPrincipal()) {
            case TenantPrincipal tenant -> ResponseEntity.ok(profileService.uploadTenantPhoto(tenant, file));
            case PlatformPrincipal ignored -> throw new UnauthorizedException(
                    "Profile photos are not available for platform accounts");
            case null, default -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        };
    }

    @GetMapping(value = "/photo", produces = MediaType.ALL_VALUE)
    @Operation(summary = "Stream your own current profile photo (tenant accounts only)")
    public ResponseEntity<byte[]> getPhoto(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return switch (authentication.getPrincipal()) {
            case TenantPrincipal tenant -> {
                ProfileService.StoredPhoto photo = profileService.getTenantPhoto(tenant);
                yield ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType())).body(photo.content());
            }
            case PlatformPrincipal ignored -> throw new UnauthorizedException(
                    "Profile photos are not available for platform accounts");
            case null, default -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        };
    }
}

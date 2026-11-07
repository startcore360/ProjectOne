package com.edu360.api.platformops.release;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.release.dto.CreateReleaseRequest;
import com.edu360.api.platformops.release.dto.ReleaseResponse;
import com.edu360.api.platformops.release.dto.ReleaseTransitionRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/platform/releases", "/api/platform/releases"})
@RequiredArgsConstructor
public class PlatformReleaseController {

    private final PlatformReleaseService releaseService;
    private final PlatformPermissionService permissionService;

    @GetMapping
    public ResponseEntity<List<ReleaseResponse>> listReleases(Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "RELEASE_MANAGE");
        return ResponseEntity.ok(releaseService.listReleases());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReleaseResponse> getRelease(@PathVariable UUID id, Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "RELEASE_MANAGE");
        return ResponseEntity.ok(releaseService.getRelease(id));
    }

    @PostMapping
    public ResponseEntity<ReleaseResponse> createRelease(
            @Valid @RequestBody CreateReleaseRequest req,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "RELEASE_MANAGE");
        return ResponseEntity.status(HttpStatus.CREATED).body(releaseService.createRelease(req, principal));
    }

    @PostMapping("/{id}/transitions")
    public ResponseEntity<ReleaseResponse> transitionRelease(
            @PathVariable UUID id,
            @Valid @RequestBody ReleaseTransitionRequest req,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "RELEASE_MANAGE");
        return ResponseEntity.ok(releaseService.transitionRelease(id, req, principal));
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}")
    public ResponseEntity<ReleaseResponse> patchRelease(
            @PathVariable UUID id,
            @Valid @RequestBody com.edu360.api.platformops.release.dto.UpdateReleaseRequest req,
            Authentication auth
    ) {
        return updateRelease(id, req, auth);
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    public ResponseEntity<ReleaseResponse> updateRelease(
            @PathVariable UUID id,
            @Valid @RequestBody com.edu360.api.platformops.release.dto.UpdateReleaseRequest req,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "RELEASE_MANAGE");
        return ResponseEntity.ok(releaseService.updateRelease(id, req, principal));
    }
}
